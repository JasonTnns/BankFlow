'use strict';

const request = require('supertest');

const { createTestApp, registerUser, postTransfer, addCustomerAccount } = require('./helpers');
const { seedDatabase, DEMO_PASSWORD, ALICE_EMAIL } = require('../src/seed');

const DAY_MS = 24 * 60 * 60 * 1000;

function aroundNow() {
  return {
    from: new Date(Date.now() - DAY_MS).toISOString(),
    to: new Date(Date.now() + DAY_MS).toISOString(),
  };
}

describe('insights API', () => {
  let app;
  let db;
  let alice;
  let bob;

  beforeEach(async () => {
    ({ app, db } = createTestApp());
    alice = await registerUser(app, { email: 'alice@example.com', fullName: 'Alice Tester' });
    bob = await registerUser(app, { email: 'bob@example.com', fullName: 'Bob Builder' });
  });
  afterEach(() => db.close());

  const summary = (headers, query = aroundNow()) =>
    request(app).get('/api/v1/insights/summary').query(query).set(headers);

  const pay = (amountMinor, category) =>
    postTransfer(app, alice.auth, {
      sourceAccountId: alice.account.id,
      destinationAccountNumber: bob.account.accountNumber,
      amountMinor,
      category,
    });

  test('a new user has only the welcome bonus as income', async () => {
    const res = await summary(alice.auth);

    expect(res.status).toBe(200);
    expect(res.body.summary).toMatchObject({
      currency: 'USD',
      incomeMinor: 100000,
      spentMinor: 0,
      netMinor: 100000,
      transactionCount: 1,
      byCategory: [],
    });
  });

  test('spending is grouped by category, biggest first', async () => {
    await pay(2500, 'FOOD');
    await pay(4000, 'FOOD');
    await pay(1000, 'BILLS');
    await pay(9000, 'SHOPPING');

    const { body } = await summary(alice.auth);

    expect(body.summary.spentMinor).toBe(16500);
    expect(body.summary.incomeMinor).toBe(100000);
    expect(body.summary.netMinor).toBe(83500);
    expect(body.summary.transactionCount).toBe(5);
    expect(body.summary.byCategory).toEqual([
      { category: 'SHOPPING', spentMinor: 9000, count: 1 },
      { category: 'FOOD', spentMinor: 6500, count: 2 },
      { category: 'BILLS', spentMinor: 1000, count: 1 },
    ]);
  });

  test('categories with equal totals are ordered by name', async () => {
    await pay(1000, 'HEALTH');
    await pay(1000, 'BILLS');

    const { body } = await summary(alice.auth);

    expect(body.summary.byCategory.map((c) => c.category)).toEqual(['BILLS', 'HEALTH']);
  });

  test('money received is income, never spending', async () => {
    await pay(2500, 'FOOD');

    const { body } = await summary(bob.auth);

    expect(body.summary.incomeMinor).toBe(102500);
    expect(body.summary.spentMinor).toBe(0);
    expect(body.summary.byCategory).toEqual([]);
  });

  test('moving money between your own accounts is neither income nor spending', async () => {
    const savingsId = addCustomerAccount(db, alice.userId, { number: '300000000001' });
    const move = await postTransfer(app, alice.auth, {
      sourceAccountId: alice.account.id,
      destinationAccountId: savingsId,
      amountMinor: 30000,
    });
    expect(move.status).toBe(201);

    const { body } = await summary(alice.auth);

    expect(body.summary).toMatchObject({ incomeMinor: 100000, spentMinor: 0, transactionCount: 1 });
  });

  test('only your own activity is counted', async () => {
    await pay(2500, 'FOOD');
    const carol = await registerUser(app, { email: 'carol@example.com', fullName: 'Carol Test' });

    const { body } = await summary(carol.auth);

    expect(body.summary).toMatchObject({ incomeMinor: 100000, spentMinor: 0, byCategory: [] });
  });

  test('a range with no activity is all zeros', async () => {
    await pay(2500, 'FOOD');
    const past = {
      from: new Date(Date.now() - 60 * DAY_MS).toISOString(),
      to: new Date(Date.now() - 30 * DAY_MS).toISOString(),
    };

    const { body } = await summary(alice.auth, past);

    expect(body.summary).toMatchObject({
      incomeMinor: 0,
      spentMinor: 0,
      netMinor: 0,
      transactionCount: 0,
      byCategory: [],
    });
  });

  test('the range is half-open: an instant exactly at "to" belongs to the next period', async () => {
    const entry = db.prepare('SELECT created_at FROM ledger_entries ORDER BY seq LIMIT 1').get();
    const at = entry.created_at;
    const before = new Date(Date.parse(at) - DAY_MS).toISOString();
    const after = new Date(Date.parse(at) + DAY_MS).toISOString();

    const endsAtIt = await summary(alice.auth, { from: before, to: at });
    const startsAtIt = await summary(alice.auth, { from: at, to: after });

    expect(endsAtIt.body.summary.transactionCount).toBe(0);
    expect(startsAtIt.body.summary.transactionCount).toBe(1);
  });

  test('offsets in the range are normalised to UTC', async () => {
    const res = await summary(alice.auth, {
      from: '2020-01-01T00:00:00+02:00',
      to: '2020-01-02T00:00:00+02:00',
    });

    expect(res.status).toBe(200);
    expect(res.body.summary.from).toBe('2019-12-31T22:00:00.000Z');
    expect(res.body.summary.to).toBe('2020-01-01T22:00:00.000Z');
  });

  test('another currency has its own, separate numbers', async () => {
    const eur = await summary(alice.auth, { ...aroundNow(), currency: 'eur' });

    expect(eur.status).toBe(200);
    expect(eur.body.summary).toMatchObject({ currency: 'EUR', incomeMinor: 0, spentMinor: 0, transactionCount: 0 });
  });

  test.each([
    ['a missing range', {}],
    ['a missing "to"', { from: '2026-10-01T00:00:00Z' }],
    ['a bad date', { from: 'yesterday', to: '2026-10-01T00:00:00Z' }],
    ['"from" after "to"', { from: '2026-11-01T00:00:00Z', to: '2026-10-01T00:00:00Z' }],
    ['an empty range', { from: '2026-10-01T00:00:00Z', to: '2026-10-01T00:00:00Z' }],
    ['a range longer than 370 days', { from: '2024-01-01T00:00:00Z', to: '2026-01-01T00:00:00Z' }],
    ['a bad currency', { from: '2026-10-01T00:00:00Z', to: '2026-11-01T00:00:00Z', currency: 'DOLLARS' }],
  ])('rejects %s', async (_label, query) => {
    const res = await summary(alice.auth, query);

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('requires authentication', async () => {
    const res = await request(app).get('/api/v1/insights/summary').query(aroundNow());

    expect(res.status).toBe(401);
  });

  describe('seeded demo data', () => {
    test('a real month adds up and balances against the history', async () => {
      const seeded = createTestApp();
      seedDatabase(seeded.db);
      const login = await request(seeded.app)
        .post('/api/v1/auth/login')
        .send({ email: ALICE_EMAIL, password: DEMO_PASSWORD });
      const auth = { Authorization: `Bearer ${login.body.accessToken}` };

      const res = await request(seeded.app)
        .get('/api/v1/insights/summary')
        .query({
          from: new Date(Date.now() - 90 * DAY_MS).toISOString(),
          to: new Date(Date.now() + DAY_MS).toISOString(),
        })
        .set(auth);

      const { summary: s } = res.body;
      expect(res.status).toBe(200);
      expect(s.spentMinor).toBeGreaterThan(0);
      expect(s.incomeMinor).toBeGreaterThan(0);
      expect(s.netMinor).toBe(s.incomeMinor - s.spentMinor);
      expect(s.byCategory.reduce((sum, c) => sum + c.spentMinor, 0)).toBe(s.spentMinor);
      expect(s.byCategory.every((c, i, all) => i === 0 || all[i - 1].spentMinor >= c.spentMinor)).toBe(true);
      seeded.db.close();
    });
  });
});