'use strict';

const crypto = require('crypto');
const request = require('supertest');

const {
  createTestApp,
  registerUser,
  postTransfer,
  getBalance,
  addCustomerAccount,
  assertLedgerConsistent,
} = require('./helpers');

describe('transfers API', () => {
  let app;
  let db;
  let alice;
  let bob;

  beforeEach(async () => {
    ({ app, db } = createTestApp());
    alice = await registerUser(app, { email: 'alice@example.com', fullName: 'Alice Tester' });
    bob = await registerUser(app, { email: 'bob@example.com', fullName: 'Bob Tester' });
  });
  afterEach(() => db.close());

  const toBob = (amountMinor, extra = {}) => ({
    sourceAccountId: alice.account.id,
    destinationAccountNumber: bob.account.accountNumber,
    amountMinor,
    ...extra,
  });

  describe('successful transfers', () => {
    test('moves money between two users and keeps the ledger balanced', async () => {
      const res = await postTransfer(app, alice.auth, toBob(2500, { description: 'Lunch', category: 'FOOD' }));

      expect(res.status).toBe(201);
      expect(res.body.transfer).toMatchObject({
        status: 'COMPLETED',
        amountMinor: 2500,
        category: 'FOOD',
        description: 'Lunch',
      });
      expect(res.body.sourceAccountBalanceMinor).toBe(97500);

      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(97500);
      expect(await getBalance(app, bob.auth, bob.account.id)).toBe(102500);
      assertLedgerConsistent(db);
    });

    test('shows up in both users\' histories with the right direction and counterparty', async () => {
      await postTransfer(app, alice.auth, toBob(2500));

      const aliceHistory = await request(app).get(`/api/v1/accounts/${alice.account.id}/transactions`).set(alice.auth);
      expect(aliceHistory.body.items[0]).toMatchObject({
        direction: 'OUT',
        amountMinor: 2500,
        balanceAfterMinor: 97500,
        counterparty: { name: 'Bob Tester', isOwnAccount: false },
      });
      // Other users' full account numbers are masked.
      expect(aliceHistory.body.items[0].counterparty.accountNumberMasked).toMatch(/^••••\d{4}$/);

      const bobHistory = await request(app).get(`/api/v1/accounts/${bob.account.id}/transactions`).set(bob.auth);
      expect(bobHistory.body.items[0]).toMatchObject({ direction: 'IN', amountMinor: 2500 });
    });

    test('transfers between the user\'s own accounts by id', async () => {
      const savingsId = addCustomerAccount(db, alice.userId, { number: '300000000001' });

      const res = await postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        destinationAccountId: savingsId,
        amountMinor: 40000,
      });

      expect(res.status).toBe(201);
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(60000);
      expect(await getBalance(app, alice.auth, savingsId)).toBe(40000);
      assertLedgerConsistent(db);
    });

    test('GET /transfers/:id works for both parties and is hidden from everyone else', async () => {
      const created = await postTransfer(app, alice.auth, toBob(1000));
      const id = created.body.transfer.id;

      const asAlice = await request(app).get(`/api/v1/transfers/${id}`).set(alice.auth);
      expect(asAlice.status).toBe(200);
      expect(asAlice.body.transfer.direction).toBe('OUT');

      const asBob = await request(app).get(`/api/v1/transfers/${id}`).set(bob.auth);
      expect(asBob.body.transfer.direction).toBe('IN');

      const carol = await registerUser(app, { email: 'carol@example.com' });
      const asCarol = await request(app).get(`/api/v1/transfers/${id}`).set(carol.auth);
      expect(asCarol.status).toBe(404);
    });
  });

  describe('insufficient funds and double spending', () => {
    test('rejects a transfer larger than the balance and records the failed attempt', async () => {
      const res = await postTransfer(app, alice.auth, toBob(100001));

      expect(res.status).toBe(422);
      expect(res.body.error.code).toBe('INSUFFICIENT_FUNDS');
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(100000);
      expect(await getBalance(app, bob.auth, bob.account.id)).toBe(100000);

      const failed = db.prepare(`SELECT failure_reason FROM transfers WHERE status = 'FAILED'`).all();
      expect(failed).toEqual([{ failure_reason: 'INSUFFICIENT_FUNDS' }]);
      assertLedgerConsistent(db);
    });

    test('two simultaneous $800 transfers from a $1,000 account: exactly one succeeds', async () => {
      const [first, second] = await Promise.all([
        postTransfer(app, alice.auth, toBob(80000)),
        postTransfer(app, alice.auth, toBob(80000)),
      ]);

      const statuses = [first.status, second.status].sort();
      expect(statuses).toEqual([201, 422]);
      const failure = first.status === 422 ? first : second;
      expect(failure.body.error.code).toBe('INSUFFICIENT_FUNDS');

      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(20000);
      expect(await getBalance(app, bob.auth, bob.account.id)).toBe(180000);
      assertLedgerConsistent(db);
    });

    test('many parallel transfers never overdraw the account', async () => {
      const results = await Promise.all(
        Array.from({ length: 10 }, () => postTransfer(app, alice.auth, toBob(30000)))
      );

      const succeeded = results.filter((r) => r.status === 201).length;
      expect(succeeded).toBe(3); // 3 x $300 fits in $1,000, a 4th would not
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(10000);
      assertLedgerConsistent(db);
    });
  });

  describe('idempotency', () => {
    test('retrying with the same key returns the original transfer and moves money once', async () => {
      const key = crypto.randomUUID();

      const first = await postTransfer(app, alice.auth, toBob(5000), key);
      const retry = await postTransfer(app, alice.auth, toBob(5000), key);

      expect(first.status).toBe(201);
      expect(retry.status).toBe(200);
      expect(retry.headers['idempotent-replayed']).toBe('true');
      expect(retry.body.transfer.id).toBe(first.body.transfer.id);

      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(95000);
      const count = db
        .prepare(`SELECT COUNT(*) AS c FROM transfers WHERE kind = 'TRANSFER' AND status = 'COMPLETED'`)
        .get().c;
      expect(count).toBe(1);
      assertLedgerConsistent(db);
    });

    test('simultaneous duplicate requests with the same key move money once', async () => {
      const key = crypto.randomUUID();
      const results = await Promise.all([
        postTransfer(app, alice.auth, toBob(5000), key),
        postTransfer(app, alice.auth, toBob(5000), key),
        postTransfer(app, alice.auth, toBob(5000), key),
      ]);

      expect(results.every((r) => r.status === 201 || r.status === 200)).toBe(true);
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(95000);
    });

    test('reusing a key with different details is rejected', async () => {
      const key = crypto.randomUUID();
      await postTransfer(app, alice.auth, toBob(5000), key);
      const conflict = await postTransfer(app, alice.auth, toBob(6000), key);

      expect(conflict.status).toBe(409);
      expect(conflict.body.error.code).toBe('IDEMPOTENCY_CONFLICT');
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(95000);
    });

    test('replaying a FAILED attempt fails the same way instead of succeeding later', async () => {
      const key = crypto.randomUUID();
      const first = await postTransfer(app, alice.auth, toBob(500000), key);
      const second = await postTransfer(app, alice.auth, toBob(500000), key);

      expect(first.body.error.code).toBe('INSUFFICIENT_FUNDS');
      expect(second.body.error.code).toBe('INSUFFICIENT_FUNDS');
    });

    test('the Idempotency-Key header is required and must be a UUID', async () => {
      const missing = await request(app).post('/api/v1/transfers').set(alice.auth).send(toBob(100));
      expect(missing.status).toBe(400);

      const invalid = await request(app)
        .post('/api/v1/transfers')
        .set(alice.auth)
        .set('Idempotency-Key', 'not-a-uuid')
        .send(toBob(100));
      expect(invalid.status).toBe(400);
    });
  });

  describe('authorization', () => {
    test('cannot transfer FROM another user\'s account by guessing its id', async () => {
      const res = await postTransfer(app, bob.auth, {
        sourceAccountId: alice.account.id, // Bob names Alice's account
        destinationAccountNumber: bob.account.accountNumber,
        amountMinor: 50000,
      });

      expect(res.status).toBe(404);
      expect(res.body.error.code).toBe('NOT_FOUND');
      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(100000);
      expect(await getBalance(app, bob.auth, bob.account.id)).toBe(100000);
    });

    test('cannot read another user\'s account or history', async () => {
      const account = await request(app).get(`/api/v1/accounts/${alice.account.id}`).set(bob.auth);
      const history = await request(app).get(`/api/v1/accounts/${alice.account.id}/transactions`).set(bob.auth);

      expect(account.status).toBe(404);
      expect(history.status).toBe(404);
    });

    test('"not yours" is indistinguishable from "does not exist"', async () => {
      const notYours = await request(app).get(`/api/v1/accounts/${alice.account.id}`).set(bob.auth);
      const nonexistent = await request(app).get(`/api/v1/accounts/${crypto.randomUUID()}`).set(bob.auth);

      expect(notYours.status).toBe(nonexistent.status);
      expect(notYours.body.error.code).toBe(nonexistent.body.error.code);
    });

    test('a user id in the body is ignored; the token decides who is acting', async () => {
      const res = await postTransfer(app, bob.auth, {
        ...toBob(1000),
        initiatedByUserId: alice.userId,
        userId: alice.userId,
      });
      // Bob cannot spend from Alice's account even while claiming to be Alice.
      expect(res.status).toBe(404);
    });
  });

  describe('business rules', () => {
    test('rejects source and destination being the same account', async () => {
      const res = await postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        destinationAccountId: alice.account.id,
        amountMinor: 100,
      });
      expect(res.status).toBe(422);
      expect(res.body.error.code).toBe('SAME_ACCOUNT');
    });

    test('rejects an unknown destination account number', async () => {
      const res = await postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        destinationAccountNumber: '999999999999',
        amountMinor: 100,
      });
      expect(res.status).toBe(404);
    });

    test('rejects a frozen destination account', async () => {
      db.prepare(`UPDATE accounts SET status = 'FROZEN' WHERE id = ?`).run(bob.account.id);
      const res = await postTransfer(app, alice.auth, toBob(100));

      expect(res.status).toBe(422);
      expect(res.body.error.code).toBe('ACCOUNT_INACTIVE');
    });

    test('enforces the daily limit on transfers to other users, with details', async () => {
      db.prepare('UPDATE users SET daily_transfer_limit_minor = 5000 WHERE id = ?').run(alice.userId);

      expect((await postTransfer(app, alice.auth, toBob(3000))).status).toBe(201);

      const over = await postTransfer(app, alice.auth, toBob(3000));
      expect(over.status).toBe(422);
      expect(over.body.error.code).toBe('LIMIT_EXCEEDED');
      expect(over.body.error.details).toEqual({ limitMinor: 5000, usedTodayMinor: 3000, remainingMinor: 2000 });

      expect((await postTransfer(app, alice.auth, toBob(2000))).status).toBe(201); // exactly the remainder
    });

    test('the daily limit does not apply between your own accounts', async () => {
      db.prepare('UPDATE users SET daily_transfer_limit_minor = 5000 WHERE id = ?').run(alice.userId);
      const savingsId = addCustomerAccount(db, alice.userId, { number: '300000000002' });

      const res = await postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        destinationAccountId: savingsId,
        amountMinor: 50000,
      });
      expect(res.status).toBe(201);
    });
  });

  describe('input validation', () => {
    test.each([
      ['a fractional amount', 12.5],
      ['a string amount', '100'],
      ['zero', 0],
      ['a negative amount', -100],
      ['more than the per-transfer maximum', 100_000_001],
    ])('rejects %s', async (_label, amountMinor) => {
      const res = await postTransfer(app, alice.auth, toBob(amountMinor));
      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    });

    test('requires exactly one destination form', async () => {
      const both = await postTransfer(app, alice.auth, {
        ...toBob(100),
        destinationAccountId: crypto.randomUUID(),
      });
      const neither = await postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        amountMinor: 100,
      });

      expect(both.status).toBe(400);
      expect(neither.status).toBe(400);
    });

    test('requires authentication', async () => {
      const res = await request(app)
        .post('/api/v1/transfers')
        .set('Idempotency-Key', crypto.randomUUID())
        .send(toBob(100));
      expect(res.status).toBe(401);
    });
  });

  describe('atomicity and database guarantees', () => {
    test('if the credit side fails, the debit is rolled back too', async () => {
      db.exec(`
        CREATE TRIGGER trg_test_fail_credit
        BEFORE INSERT ON ledger_entries
        WHEN NEW.direction = 'CREDIT'
        BEGIN SELECT RAISE(ABORT, 'simulated crash'); END;
      `);
      const key = crypto.randomUUID();

      const res = await postTransfer(app, alice.auth, toBob(5000), key);

      expect(res.status).toBe(500);
      expect(res.body.error.code).toBe('INTERNAL_ERROR');
      expect(JSON.stringify(res.body)).not.toContain('simulated crash'); // internals never leak

      expect(await getBalance(app, alice.auth, alice.account.id)).toBe(100000);
      expect(await getBalance(app, bob.auth, bob.account.id)).toBe(100000);
      expect(db.prepare('SELECT 1 FROM transfers WHERE idempotency_key = ?').get(key)).toBeUndefined();
      assertLedgerConsistent(db);
    });

    test('ledger entries are immutable', () => {
      expect(() => db.prepare('UPDATE ledger_entries SET amount_minor = 1').run()).toThrow(/immutable/);
      expect(() => db.prepare('DELETE FROM ledger_entries').run()).toThrow(/immutable/);
    });

    test('the database itself refuses a negative customer balance', () => {
      expect(() =>
        db.prepare('UPDATE accounts SET balance_minor = -1 WHERE id = ?').run(alice.account.id)
      ).toThrow(/CHECK constraint/);
    });
  });
});