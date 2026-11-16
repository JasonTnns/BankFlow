'use strict';

const crypto = require('crypto');
const request = require('supertest');

const { createTestApp, registerUser, addCustomerAccount } = require('./helpers');
const { MAX_BENEFICIARIES } = require('../src/services/beneficiaryService');

describe('beneficiaries API', () => {
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

  const save = (headers, body) => request(app).post('/api/v1/beneficiaries').set(headers).send(body);
  const list = (headers) => request(app).get('/api/v1/beneficiaries').set(headers);
  const saveBob = (nickname = 'Bob') => save(alice.auth, { nickname, accountNumber: bob.account.accountNumber });

  test('starts empty', async () => {
    const res = await list(alice.auth);

    expect(res.status).toBe(200);
    expect(res.body).toEqual({ beneficiaries: [] });
  });

  test('saves a recipient and describes them without exposing their full name', async () => {
    const res = await saveBob('  Bob  ');

    expect(res.status).toBe(201);
    expect(res.body.beneficiary).toMatchObject({
      nickname: 'Bob',
      accountNumber: bob.account.accountNumber,
      accountNumberMasked: `••••${bob.account.accountNumber.slice(-4)}`,
      displayName: 'Bob B.',
      currency: 'USD',
      status: 'ACTIVE',
    });
    expect(JSON.stringify(res.body)).not.toContain('Builder');
  });

  test('lists saved recipients by nickname, ignoring case', async () => {
    const carol = await registerUser(app, { email: 'carol@example.com', fullName: 'Carol Test' });
    await saveBob('zed');
    await save(alice.auth, { nickname: 'Alpha', accountNumber: carol.account.accountNumber });

    const res = await list(alice.auth);

    expect(res.body.beneficiaries.map((b) => b.nickname)).toEqual(['Alpha', 'zed']);
  });

  test('the same account cannot be saved twice', async () => {
    await saveBob('Bob');
    const again = await saveBob('Bobby');

    expect(again.status).toBe(409);
    expect(again.body.error.code).toBe('BENEFICIARY_EXISTS');
    expect((await list(alice.auth)).body.beneficiaries).toHaveLength(1);
  });

  test('your own account cannot be saved, and the message names the field', async () => {
    const res = await save(alice.auth, { nickname: 'Me', accountNumber: alice.account.accountNumber });

    expect(res.status).toBe(400);
    expect(res.body.error.code).toBe('VALIDATION_ERROR');
    expect(res.body.error.details.fields.accountNumber).toBeDefined();
  });

  test('an unknown number is not found', async () => {
    const res = await save(alice.auth, { nickname: 'Ghost', accountNumber: '999999999999' });

    expect(res.status).toBe(404);
    expect(res.body.error.code).toBe('NOT_FOUND');
  });

  test('bank and merchant accounts cannot be saved', async () => {
    db.prepare(
      `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor)
       VALUES (?, NULL, 'SYSTEM', 'SYSTEM', 'Merchant', '555555555555', 'USD', 0)`
    ).run(crypto.randomUUID());

    const res = await save(alice.auth, { nickname: 'Shop', accountNumber: '555555555555' });

    expect(res.status).toBe(404);
  });

  test.each([
    ['a blank nickname', { nickname: '   ', accountNumber: '200000000001' }, 'nickname'],
    ['a nickname over 40 characters', { nickname: 'x'.repeat(41), accountNumber: '200000000001' }, 'nickname'],
    ['a short account number', { nickname: 'Bob', accountNumber: '12345' }, 'accountNumber'],
    ['letters in the account number', { nickname: 'Bob', accountNumber: 'MERCHANT-GROCERY' }, 'accountNumber'],
  ])('rejects %s', async (_label, body, field) => {
    const res = await save(alice.auth, body);

    expect(res.status).toBe(400);
    expect(res.body.error.details.fields[field]).toBeDefined();
  });

  test(`at most ${MAX_BENEFICIARIES} recipients can be saved`, async () => {
    const numbers = [];
    for (let i = 0; i <= MAX_BENEFICIARIES; i++) {
      const number = String(400000000000 + i);
      addCustomerAccount(db, bob.userId, { number, name: `Extra ${i}` });
      numbers.push(number);
    }

    for (const accountNumber of numbers.slice(0, MAX_BENEFICIARIES)) {
      expect((await save(alice.auth, { nickname: accountNumber, accountNumber })).status).toBe(201);
    }
    const over = await save(alice.auth, { nickname: 'One too many', accountNumber: numbers[MAX_BENEFICIARIES] });

    expect(over.status).toBe(400);
    expect(over.body.error.code).toBe('VALIDATION_ERROR');
  });

  test('a frozen account shows as unavailable straight away', async () => {
    await saveBob();
    db.prepare(`UPDATE accounts SET status = 'FROZEN' WHERE id = ?`).run(bob.account.id);

    const res = await list(alice.auth);

    expect(res.body.beneficiaries[0].status).toBe('FROZEN');
  });

  test('deleting removes only that recipient', async () => {
    const carol = await registerUser(app, { email: 'carol@example.com', fullName: 'Carol Test' });
    const bobSaved = (await saveBob()).body.beneficiary;
    await save(alice.auth, { nickname: 'Carol', accountNumber: carol.account.accountNumber });

    const res = await request(app).delete(`/api/v1/beneficiaries/${bobSaved.id}`).set(alice.auth);

    expect(res.status).toBe(204);
    expect((await list(alice.auth)).body.beneficiaries.map((b) => b.nickname)).toEqual(['Carol']);
  });

  test('deleting something that is already gone is not found', async () => {
    const res = await request(app).delete(`/api/v1/beneficiaries/${crypto.randomUUID()}`).set(alice.auth);

    expect(res.status).toBe(404);
  });

  test('recipients are private to their owner', async () => {
    const saved = (await saveBob()).body.beneficiary;

    expect((await list(bob.auth)).body.beneficiaries).toEqual([]);

    const stolenDelete = await request(app).delete(`/api/v1/beneficiaries/${saved.id}`).set(bob.auth);
    expect(stolenDelete.status).toBe(404);
    expect((await list(alice.auth)).body.beneficiaries).toHaveLength(1);
  });

  test('everything requires authentication', async () => {
    expect((await request(app).get('/api/v1/beneficiaries')).status).toBe(401);
    expect((await request(app).post('/api/v1/beneficiaries').send({})).status).toBe(401);
    expect((await request(app).delete(`/api/v1/beneficiaries/${crypto.randomUUID()}`)).status).toBe(401);
  });
});