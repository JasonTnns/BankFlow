'use strict';

const crypto = require('crypto');
const request = require('supertest');

const { createDb } = require('../src/db');
const { createApp } = require('../src/app');

/** Fresh app on a brand-new in-memory database. Every test gets its own. */
function createTestApp() {
  const db = createDb(':memory:');
  const app = createApp({ db });
  return { db, app };
}

/** Register a user through the real API and return everything tests need. */
async function registerUser(app, overrides = {}) {
  const email = overrides.email ?? `user-${crypto.randomUUID()}@example.com`;
  const res = await request(app)
    .post('/api/v1/auth/register')
    .send({ email, password: 'Passw0rd123', fullName: 'Test User', ...overrides });
  expect(res.status).toBe(201);

  const auth = { Authorization: `Bearer ${res.body.accessToken}` };
  const accounts = await request(app).get('/api/v1/accounts').set(auth);

  return {
    session: res.body,
    auth,
    email,
    userId: res.body.user.id,
    account: accounts.body.accounts[0], // the Everyday Checking account
  };
}

/** POST /transfers with a fresh idempotency key unless one is given. */
function postTransfer(app, auth, body, key = crypto.randomUUID()) {
  return request(app).post('/api/v1/transfers').set(auth).set('Idempotency-Key', key).send(body);
}

async function getBalance(app, auth, accountId) {
  const res = await request(app).get(`/api/v1/accounts/${accountId}`).set(auth);
  expect(res.status).toBe(200);
  return res.body.account.balanceMinor;
}

/** Insert an extra customer account directly (registration only creates one). */
function addCustomerAccount(db, userId, { name = 'Savings', number, type = 'SAVINGS' } = {}) {
  const id = crypto.randomUUID();
  db.prepare(
    `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor)
     VALUES (?, ?, 'CUSTOMER', ?, ?, ?, 'USD', 0)`
  ).run(id, userId, type, name, number);
  return id;
}

/** The ledger invariants that must hold after ANY sequence of operations. */
function assertLedgerConsistent(db) {
  expect(db.prepare('SELECT * FROM v_reconciliation_errors').all()).toEqual([]);
  expect(db.prepare('SELECT COALESCE(SUM(balance_minor), 0) AS total FROM accounts').get().total).toBe(0);
}

module.exports = {
  createTestApp,
  registerUser,
  postTransfer,
  getBalance,
  addCustomerAccount,
  assertLedgerConsistent,
};