'use strict';

const request = require('supertest');
const { z } = require('zod');

const { toIssue } = require('../src/middleware/validate');
const { createTestApp, registerUser, postTransfer } = require('./helpers');

function issueFor(schema, value) {
  const result = schema.safeParse(value);
  expect(result.success).toBe(false);
  return toIssue(result.error.issues[0]);
}

describe('validation codes (the mapping from zod)', () => {
  test('a missing field is REQUIRED, and so is a blank string', () => {
    expect(issueFor(z.object({ a: z.string() }), {})).toEqual({ code: 'REQUIRED' });
    expect(issueFor(z.string().trim().min(1), '   ')).toEqual({ code: 'REQUIRED' });
  });

  test('length limits carry their numbers', () => {
    expect(issueFor(z.string().min(8), 'abc')).toEqual({ code: 'TOO_SHORT', params: { min: 8 } });
    expect(issueFor(z.string().max(5), 'abcdefg')).toEqual({ code: 'TOO_LONG', params: { max: 5 } });
  });

  test('numbers: not whole, wrong type, too small, too large', () => {
    expect(issueFor(z.number().int(), 12.5)).toEqual({ code: 'NOT_INTEGER' });
    expect(issueFor(z.number(), '100')).toEqual({ code: 'INVALID_TYPE' });
    expect(issueFor(z.number().positive(), 0)).toEqual({ code: 'TOO_SMALL', params: { min: 0 } });
    expect(issueFor(z.number().max(10), 11)).toEqual({ code: 'TOO_LARGE', params: { max: 10 } });
  });

  test('formats', () => {
    expect(issueFor(z.string().email(), 'nope')).toEqual({ code: 'INVALID_EMAIL' });
    expect(issueFor(z.string().uuid(), 'nope')).toEqual({ code: 'INVALID_UUID' });
    expect(issueFor(z.string().datetime(), 'nope')).toEqual({ code: 'INVALID_DATETIME' });
    expect(issueFor(z.string().regex(/^\d+$/), 'abc')).toEqual({ code: 'INVALID_FORMAT' });
    expect(issueFor(z.enum(['A', 'B']), 'C')).toEqual({ code: 'INVALID_OPTION' });
  });

  test('custom rules choose their own code, and extra parameters pass through', () => {
    const named = z.string().refine(() => false, { message: 'x', params: { code: 'OWN_ACCOUNT' } });
    const withParams = z.string().refine(() => false, { message: 'x', params: { code: 'TOO_SHORT', min: 3 } });
    const unnamed = z.string().refine(() => false, 'x');

    expect(issueFor(named, 'a')).toEqual({ code: 'OWN_ACCOUNT' });
    expect(issueFor(withParams, 'a')).toEqual({ code: 'TOO_SHORT', params: { min: 3 } });
    expect(issueFor(unnamed, 'a')).toEqual({ code: 'INVALID' });
  });
});

describe('validation codes (over the API)', () => {
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

  const detailsOf = (res) => res.body.error.details;

  test('registering with nothing sent: every field is REQUIRED', async () => {
    const res = await request(app).post('/api/v1/auth/register').send({});

    expect(res.status).toBe(400);
    expect(detailsOf(res).issues).toEqual({
      email: [{ code: 'REQUIRED' }],
      password: [{ code: 'REQUIRED' }],
      fullName: [{ code: 'REQUIRED' }],
    });
  });

  test('weak passwords: too short, and no letter or number', async () => {
    const short = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'a@b.com', password: 'Ab1', fullName: 'A' });
    const noNumber = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'a@b.com', password: 'OnlyLetters', fullName: 'A' });

    expect(detailsOf(short).issues.password).toEqual(
      expect.arrayContaining([{ code: 'TOO_SHORT', params: { min: 8 } }])
    );
    expect(detailsOf(noNumber).issues.password).toEqual([{ code: 'PASSWORD_NEEDS_LETTER_AND_NUMBER' }]);
  });

  test('an invalid email and a blank name', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'not-an-email', password: 'Passw0rd123', fullName: '   ' });

    expect(detailsOf(res).issues.email).toEqual([{ code: 'INVALID_EMAIL' }]);
    expect(detailsOf(res).issues.fullName).toEqual([{ code: 'REQUIRED' }]);
  });

  test('the English messages are still there, entry for entry beside the codes', async () => {
    const res = await request(app)
      .post('/api/v1/auth/register')
      .send({ email: 'a@b.com', password: 'Ab1', fullName: 'A' });

    const { fields, issues } = detailsOf(res);
    expect(fields.password.length).toBeGreaterThan(0);
    expect(fields.password.length).toBe(issues.password.length);
  });

  test('transfer amounts: not whole, zero, and too large', async () => {
    const send = (amountMinor) =>
      postTransfer(app, alice.auth, {
        sourceAccountId: alice.account.id,
        destinationAccountNumber: bob.account.accountNumber,
        amountMinor,
      });

    expect(detailsOf(await send(12.5)).issues.amountMinor).toEqual([{ code: 'NOT_INTEGER' }]);
    expect(detailsOf(await send(0)).issues.amountMinor).toEqual([{ code: 'TOO_SMALL', params: { min: 0 } }]);
    expect(detailsOf(await send(100_000_001)).issues.amountMinor).toEqual([
      { code: 'TOO_LARGE', params: { max: 100000000 } },
    ]);
  });

  test('a transfer with two destinations, and a note that is too long', async () => {
    const both = await postTransfer(app, alice.auth, {
      sourceAccountId: alice.account.id,
      destinationAccountId: bob.account.id,
      destinationAccountNumber: bob.account.accountNumber,
      amountMinor: 100,
    });
    const longNote = await postTransfer(app, alice.auth, {
      sourceAccountId: alice.account.id,
      destinationAccountNumber: bob.account.accountNumber,
      amountMinor: 100,
      description: 'x'.repeat(141),
    });

    expect(detailsOf(both).issues.destinationAccountId).toEqual([{ code: 'DESTINATION_CONFLICT' }]);
    expect(detailsOf(longNote).issues.description).toEqual([{ code: 'TOO_LONG', params: { max: 140 } }]);
  });

  test('a missing Idempotency-Key header', async () => {
    const res = await request(app)
      .post('/api/v1/transfers')
      .set(alice.auth)
      .send({ sourceAccountId: alice.account.id, destinationAccountNumber: bob.account.accountNumber, amountMinor: 100 });

    expect(detailsOf(res).issues['Idempotency-Key']).toEqual([{ code: 'INVALID_UUID' }]);
  });

  test('saving your own account as a recipient', async () => {
    const res = await request(app)
      .post('/api/v1/beneficiaries')
      .set(alice.auth)
      .send({ nickname: 'Me', accountNumber: alice.account.accountNumber });

    expect(res.status).toBe(400);
    expect(detailsOf(res).issues.accountNumber).toEqual([{ code: 'OWN_ACCOUNT' }]);
  });

  test('a recipient with a blank name and a short account number', async () => {
    const res = await request(app)
      .post('/api/v1/beneficiaries')
      .set(alice.auth)
      .send({ nickname: '  ', accountNumber: '123' });

    expect(detailsOf(res).issues.nickname).toEqual([{ code: 'REQUIRED' }]);
    expect(detailsOf(res).issues.accountNumber).toEqual([{ code: 'INVALID_FORMAT' }]);
  });

  test('history: a bad cursor and contradictory ranges', async () => {
    const history = (query) =>
      request(app).get(`/api/v1/accounts/${alice.account.id}/transactions`).query(query).set(alice.auth);

    expect(detailsOf(await history({ cursor: '!!!not-a-cursor' })).issues.cursor).toEqual([{ code: 'INVALID_CURSOR' }]);
    expect(
      detailsOf(await history({ from: '2026-11-01T00:00:00Z', to: '2026-10-01T00:00:00Z' })).issues.from
    ).toEqual([{ code: 'RANGE_INVALID' }]);
    expect(detailsOf(await history({ minAmountMinor: 500, maxAmountMinor: 100 })).issues.minAmountMinor).toEqual([
      { code: 'AMOUNT_RANGE_INVALID' },
    ]);
  });

  test('insights: a backwards range and one that is too long', async () => {
    const summary = (query) => request(app).get('/api/v1/insights/summary').query(query).set(alice.auth);

    expect(
      detailsOf(await summary({ from: '2026-11-01T00:00:00Z', to: '2026-10-01T00:00:00Z' })).issues.from
    ).toEqual([{ code: 'RANGE_INVALID' }]);
    expect(
      detailsOf(await summary({ from: '2024-01-01T00:00:00Z', to: '2026-01-01T00:00:00Z' })).issues.to
    ).toEqual([{ code: 'RANGE_TOO_LONG' }]);
  });

  test('a profile update with nothing in it is a problem with the body as a whole', async () => {
    const res = await request(app).patch('/api/v1/me').set(alice.auth).send({});

    expect(detailsOf(res).formIssues).toEqual([{ code: 'NOTHING_TO_UPDATE' }]);
    expect(detailsOf(res).form).toEqual(['Provide fullName or themePreference']);
  });

  test('an unknown theme is INVALID_OPTION', async () => {
    const res = await request(app).patch('/api/v1/me').set(alice.auth).send({ themePreference: 'NEON' });

    expect(detailsOf(res).issues.themePreference).toEqual([{ code: 'INVALID_OPTION' }]);
  });
});