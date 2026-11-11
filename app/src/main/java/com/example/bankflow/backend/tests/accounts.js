'use strict';

const request = require('supertest');

const { createTestApp, assertLedgerConsistent } = require('./helpers');
const { seedDatabase, DEMO_PASSWORD, ALICE_EMAIL } = require('../src/seed');
const { seedDatabase, DEMO_PASSWORD, ALICE_EMAIL, BOB_EMAIL } = require('../src/seed');

describe('accounts and transaction history (seeded demo data)', () => {
  let app;
  let db;
  let auth;
  let checking;
  let savings;

  beforeAll(async () => {
    ({ app, db } = createTestApp());
    seedDatabase(db);

    const login = await request(app).post('/api/v1/auth/login').send({ email: ALICE_EMAIL, password: DEMO_PASSWORD });
    expect(login.status).toBe(200);
    auth = { Authorization: `Bearer ${login.body.accessToken}` };

    const accounts = await request(app).get('/api/v1/accounts').set(auth);
    checking = accounts.body.accounts.find((a) => a.accountNumber === '100000000001');
    savings = accounts.body.accounts.find((a) => a.accountNumber === '100000000002');
  });
  afterAll(() => db.close());

  const history = (query = {}) =>
    request(app).get(`/api/v1/accounts/${checking.id}/transactions`).query(query).set(auth);

  test('seeding is repeatable and leaves the ledger balanced', () => {
    expect(seedDatabase(db).seeded).toBe(false);
    assertLedgerConsistent(db);
  });

  test('lists only the caller\'s customer accounts', async () => {
    const res = await request(app).get('/api/v1/accounts').set(auth);
    expect(res.body.accounts.map((a) => a.accountNumber).sort()).toEqual(['100000000001', '100000000002']);
  });

  test('dashboard totals equal the sum of account balances and include recent activity', async () => {
    const res = await request(app).get('/api/v1/dashboard').set(auth);
    const sum = res.body.accounts.reduce((total, a) => total + a.balanceMinor, 0);

    expect(res.body.totals).toEqual([{ currency: 'USD', balanceMinor: sum }]);
    expect(res.body.recentTransactions).toHaveLength(5);
  });

  test('the newest transaction\'s running balance equals the account balance', async () => {
    const res = await history({ limit: 1 });
    expect(res.body.items[0].balanceAfterMinor).toBe(checking.balanceMinor);
  });

    describe('recipient lookup', () => {
      const lookup = (accountNumber, headers = auth) =>
        request(app).get('/api/v1/recipients/lookup').query({ accountNumber }).set(headers);

      test('names the person with a shortened name and a masked number', async () => {
        const res = await lookup('200000000001');

        expect(res.status).toBe(200);
        expect(res.body.recipient).toEqual({
          displayName: 'Bob D.',
          accountNumberMasked: '••••0001',
          currency: 'USD',
          status: 'ACTIVE',
          isOwnAccount: false,
        });

        expect(JSON.stringify(res.body)).not.toContain('200000000001');
        expect(JSON.stringify(res.body)).not.toContain('Demo');
      });

      test("flags the caller's own accounts", async () => {
        const res = await lookup('100000000002');

        expect(res.body.recipient).toMatchObject({ displayName: 'Alice D.', isOwnAccount: true });
      });

      test('an unknown number is not found', async () => {
        expect((await lookup('999999999999')).status).toBe(404);
      });

      test('malformed numbers are rejected, so bank and merchant accounts cannot be probed', async () => {
        expect((await lookup('MERCHANT-GROCERY')).status).toBe(400);
        expect((await lookup('123')).status).toBe(400);
      });

      test('requires authentication', async () => {
        const res = await request(app).get('/api/v1/recipients/lookup').query({ accountNumber: '200000000001' });
        expect(res.status).toBe(401);
      });
    });

  describe('pagination', () => {
    test('walking every page returns each transaction exactly once, newest first', async () => {
      const seen = [];
      let cursor;
      let pages = 0;

      do {
        const res = await history({ limit: 10, ...(cursor && { cursor }) });
        expect(res.status).toBe(200);
        expect(res.body.items.length).toBeLessThanOrEqual(10);
        seen.push(...res.body.items);
        cursor = res.body.nextCursor;
        pages += 1;
      } while (cursor && pages < 100);

      const expected = db
        .prepare('SELECT COUNT(*) AS c FROM ledger_entries WHERE account_id = ?')
        .get(checking.id).c;
      expect(seen).toHaveLength(expected);
      expect(new Set(seen.map((t) => `${t.transferId}:${t.direction}`)).size).toBe(expected);
      expect(pages).toBeGreaterThan(1);

      for (let i = 1; i < seen.length; i++) {
        expect(seen[i - 1].createdAt >= seen[i].createdAt).toBe(true);
      }
    });

    test('the last page has no cursor', async () => {
      const res = await history({ limit: 50 });
      // Alice's checking account has well over 50 entries; follow cursors to the end.
      let page = res;
      while (page.body.nextCursor) {
        page = await history({ limit: 50, cursor: page.body.nextCursor });
      }
      expect(page.body.nextCursor).toBeNull();
    });

    test('rejects an invalid cursor and an oversized limit', async () => {
      expect((await history({ cursor: '!!!not-a-cursor' })).status).toBe(400);
      expect((await history({ limit: 1000 })).status).toBe(400);
      expect((await history({ limit: 0 })).status).toBe(400);
    });
  });

  describe('filters', () => {
    test('direction', async () => {
      const incoming = await history({ direction: 'IN', limit: 50 });
      expect(incoming.body.items.length).toBeGreaterThan(0);
      expect(incoming.body.items.every((t) => t.direction === 'IN')).toBe(true);

      const outgoing = await history({ direction: 'OUT', limit: 50 });
      expect(outgoing.body.items.every((t) => t.direction === 'OUT')).toBe(true);
    });

      describe('transaction details', () => {
        const detail = (accountId, transferId, headers = auth) =>
          request(app).get(`/api/v1/accounts/${accountId}/transactions/${transferId}`).set(headers);

        test('returns the same transaction the history list shows', async () => {
          const first = (await history({ limit: 1 })).body.items[0];

          const res = await detail(checking.id, first.transferId);

          expect(res.status).toBe(200);
          expect(res.body.transaction).toEqual(first);
        });

        test('a transfer between your own accounts has one entry per account', async () => {
          const own = (await history({ category: 'TRANSFER', limit: 1 })).body.items[0];

          const fromChecking = await detail(checking.id, own.transferId);
          const toSavings = await detail(savings.id, own.transferId);

          expect(fromChecking.body.transaction.direction).toBe('OUT');
          expect(toSavings.body.transaction.direction).toBe('IN');
          expect(toSavings.body.transaction.accountId).toBe(savings.id);
        });

        test('someone else cannot read it, and an unknown id is not found', async () => {
          const first = (await history({ limit: 1 })).body.items[0];
          const bob = await request(app).post('/api/v1/auth/login').send({ email: BOB_EMAIL, password: DEMO_PASSWORD });
          const bobAuth = { Authorization: `Bearer ${bob.body.accessToken}` };

          expect((await detail(checking.id, first.transferId, bobAuth)).status).toBe(404);
          expect((await detail(checking.id, '00000000-0000-4000-8000-000000000000')).status).toBe(404);
        });

        test('requires authentication', async () => {
          const res = await request(app).get(`/api/v1/accounts/${checking.id}/transactions/x`);
          expect(res.status).toBe(401);
        });
      });

    test('category', async () => {
      const res = await history({ category: 'FOOD', limit: 50 });
      expect(res.body.items.length).toBeGreaterThan(0);
      expect(res.body.items.every((t) => t.category === 'FOOD')).toBe(true);
    });

    test('search matches description or counterparty, ignoring case', async () => {
      const res = await history({ search: 'RENT', limit: 50 });
      expect(res.body.items.length).toBeGreaterThan(0);
      for (const t of res.body.items) {
        const haystack = `${t.description} ${t.counterparty.name}`.toLowerCase();
        expect(haystack).toContain('rent');
      }
    });

    test('LIKE wildcards in a search are treated as literal text', async () => {
      const res = await history({ search: '%', limit: 50 });
      expect(res.body.items).toEqual([]);
    });

    test('amount range', async () => {
      const res = await history({ minAmountMinor: 100000, limit: 50 });
      expect(res.body.items.length).toBeGreaterThan(0);
      expect(res.body.items.every((t) => t.amountMinor >= 100000)).toBe(true);
    });

    test('date range, including offsets that are normalised to UTC', async () => {
      const future = await history({ from: '2999-01-01T00:00:00Z' });
      expect(future.body.items).toEqual([]);
      expect(future.body.nextCursor).toBeNull();

      const withOffset = await history({ from: '2000-01-01T00:00:00+02:00', limit: 50 });
      expect(withOffset.status).toBe(200);
      expect(withOffset.body.items.length).toBeGreaterThan(0);
    });

    test('rejects contradictory ranges', async () => {
      const dates = await history({ from: '2026-10-01T00:00:00Z', to: '2026-09-01T00:00:00Z' });
      const amounts = await history({ minAmountMinor: 500, maxAmountMinor: 100 });
      expect(dates.status).toBe(400);
      expect(amounts.status).toBe(400);
    });

    test('filters combine with each other', async () => {
      const res = await history({ direction: 'OUT', category: 'BILLS', limit: 50 });
      expect(res.body.items.length).toBeGreaterThan(0);
      expect(res.body.items.every((t) => t.direction === 'OUT' && t.category === 'BILLS')).toBe(true);
    });
  });

  test('merchants appear with a name but no account number', async () => {
    const res = await history({ search: 'Fresh Market', limit: 1 });
    expect(res.body.items[0].counterparty).toMatchObject({ name: 'Fresh Market', accountNumberMasked: null });
  });

  test('transfers between the user\'s own accounts are flagged', async () => {
    const res = await history({ category: 'TRANSFER', limit: 1 });
    expect(res.body.items[0].counterparty).toMatchObject({ name: 'High-Yield Savings', isOwnAccount: true });
  });
});