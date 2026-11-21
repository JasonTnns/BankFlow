'use strict';

const request = require('supertest');

const { createTestApp, registerUser, postTransfer, assertLedgerConsistent } = require('./helpers');

describe('DELETE /me (closing an account)', () => {
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

  const del = (headers, password = 'Passw0rd123') => request(app).delete('/api/v1/me').set(headers).send({ password });
  const balanceOf = (accountId) => db.prepare('SELECT balance_minor AS b FROM accounts WHERE id = ?').get(accountId).b;
  const reserveBalance = () => db.prepare(`SELECT balance_minor AS b FROM accounts WHERE account_number = 'SYSTEM-RESERVE'`).get().b;
  const payouts = () =>
    db.prepare(`SELECT * FROM transfers WHERE description = 'Account closure payout'`).all();

  const sendToBob = (amountMinor) =>
    postTransfer(app, alice.auth, {
      sourceAccountId: alice.account.id,
      destinationAccountNumber: bob.account.accountNumber,
      amountMinor,
    });

  describe('confirmation', () => {
    test('a wrong password is refused with 403, not 401, and nothing changes', async () => {
      const res = await del(alice.auth, 'WrongPass999');

      expect(res.status).toBe(403);
      expect(res.body.error.code).toBe('PASSWORD_INCORRECT');
      expect((await request(app).get('/api/v1/me').set(alice.auth)).status).toBe(200);
      expect(balanceOf(alice.account.id)).toBe(100000);
    });

    test('a missing password is a validation error', async () => {
      const res = await request(app).delete('/api/v1/me').set(alice.auth).send({});

      expect(res.status).toBe(400);
      expect(res.body.error.details.issues.password).toEqual([{ code: 'REQUIRED' }]);
    });

    test('requires authentication', async () => {
      const res = await request(app).delete('/api/v1/me').send({ password: 'Passw0rd123' });

      expect(res.status).toBe(401);
    });
  });

  describe('after deleting', () => {
    test('every credential stops working, and the email can be registered again', async () => {
      expect((await del(alice.auth)).status).toBe(204);

      expect((await request(app).get('/api/v1/me').set(alice.auth)).status).toBe(401);
      expect((await request(app).post('/api/v1/auth/refresh').send({ refreshToken: alice.session.refreshToken })).status).toBe(401);
      const login = await request(app).post('/api/v1/auth/login').send({ email: 'alice@example.com', password: 'Passw0rd123' });
      expect(login.status).toBe(401);
      expect(login.body.error.code).toBe('INVALID_CREDENTIALS');

      const again = await registerUser(app, { email: 'alice@example.com', fullName: 'Alice Again' });
      expect(again.userId).not.toBe(alice.userId);
      expect(again.account.balanceMinor).toBe(100000);
    });

    test('personal details are gone: name, email, password, sessions, saved recipients', async () => {
      await request(app)
        .post('/api/v1/beneficiaries')
        .set(alice.auth)
        .send({ nickname: 'Bob', accountNumber: bob.account.accountNumber });

      await del(alice.auth);

      const user = db.prepare('SELECT * FROM users WHERE id = ?').get(alice.userId);
      expect(user.email).toMatch(/^deleted-.+@deleted\.invalid$/);
      expect(user.full_name).toBe('Deleted user');
      expect(user.password_hash).toBe('!');
      expect(db.prepare('SELECT COUNT(*) AS n FROM sessions WHERE user_id = ?').get(alice.userId).n).toBe(0);
      expect(db.prepare('SELECT COUNT(*) AS n FROM beneficiaries WHERE user_id = ?').get(alice.userId).n).toBe(0);
    });

    test('the accounts are closed', async () => {
      await del(alice.auth);

      const accounts = db.prepare(`SELECT status, name FROM accounts WHERE user_id = ?`).all(alice.userId);
      expect(accounts).toEqual([{ status: 'CLOSED', name: 'Closed account' }]);
    });

    test('other users are untouched', async () => {
      await del(alice.auth);

      const profile = await request(app).get('/api/v1/me').set(bob.auth);
      expect(profile.body.profile.fullName).toBe('Bob Builder');
      expect(balanceOf(bob.account.id)).toBe(100000);
    });

    test('deleting twice is impossible, the token is already dead', async () => {
      await del(alice.auth);

      expect((await del(alice.auth)).status).toBe(401);
    });
  });

  describe('money', () => {
    test('a remaining balance is paid out through the ledger, which stays balanced', async () => {
      const reserveBefore = reserveBalance();

      await del(alice.auth);

      expect(balanceOf(alice.account.id)).toBe(0);
      expect(reserveBalance()).toBe(reserveBefore + 100000);
      const [payout] = payouts();
      expect(payout).toMatchObject({
        kind: 'TRANSFER', status: 'COMPLETED', amount_minor: 100000, initiated_by_user_id: null,
      });
      expect(db.prepare('SELECT COUNT(*) AS n FROM ledger_entries WHERE transfer_id = ?').get(payout.id).n).toBe(2);
      assertLedgerConsistent(db);
    });

    test('an empty account needs no payout', async () => {
      await sendToBob(100000);

      await del(alice.auth);

      expect(payouts()).toEqual([]);
      assertLedgerConsistent(db);
    });

    test('only what is left is paid out', async () => {
      await sendToBob(2500);

      await del(alice.auth);

      expect(payouts().map((p) => p.amount_minor)).toEqual([97500]);
    });
  });

  describe('what others see', () => {
    test('a past transfer stays in the other person\'s history, anonymised', async () => {
      await sendToBob(2500);

      await del(alice.auth);

      const history = await request(app).get(`/api/v1/accounts/${bob.account.id}/transactions`).set(bob.auth);
      expect(history.body.items[0]).toMatchObject({
        direction: 'IN', amountMinor: 2500, counterparty: { name: 'Deleted user' },
      });
      expect(balanceOf(bob.account.id)).toBe(102500);
    });

    test('a closed account can no longer receive money', async () => {
      await del(alice.auth);

      const res = await postTransfer(app, bob.auth, {
        sourceAccountId: bob.account.id,
        destinationAccountNumber: alice.account.accountNumber,
        amountMinor: 100,
      });

      expect(res.status).toBe(422);
      expect(res.body.error.code).toBe('ACCOUNT_INACTIVE');
    });

    test('looking the number up shows it as closed', async () => {
      await del(alice.auth);

      const res = await request(app)
        .get('/api/v1/recipients/lookup')
        .query({ accountNumber: alice.account.accountNumber })
        .set(bob.auth);

      expect(res.body.recipient).toMatchObject({ status: 'CLOSED', displayName: 'Deleted U.' });
    });
  });
});