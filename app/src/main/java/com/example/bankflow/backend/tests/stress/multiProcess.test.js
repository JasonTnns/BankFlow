'use strict';

const { spawn } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const { createDb } = require('../../src/db');
const { createAuthService } = require('../../src/services/authService');
const { assertLedgerConsistent } = require('../helpers');

jest.setTimeout(90_000);

const WORKER = path.join(__dirname, 'worker.js');
const START_DELAY_MS = 1500;

function runWorker(args) {
  return new Promise((resolve, reject) => {
    const child = spawn(process.execPath, [WORKER, ...args.map(String)], {
      env: { ...process.env, NODE_ENV: 'test' },
    });
    let stdout = '';
    let stderr = '';
    child.stdout.on('data', (chunk) => (stdout += chunk));
    child.stderr.on('data', (chunk) => (stderr += chunk));
    child.on('error', reject);
    child.on('close', (code) => {
      if (code !== 0) return reject(new Error(`worker exited with ${code}: ${stderr}`));
      try {
        resolve(JSON.parse(stdout.trim().split('\n').pop()));
      } catch {
        reject(new Error(`unreadable worker output: ${stdout}\n${stderr}`));
      }
    });
  });
}

const total = (results, key) => results.reduce((sum, result) => sum + result[key], 0);

describe('transfers across several processes sharing one database file', () => {
  let dir;
  let dbPath;
  let db;
  let alice;
  let bob;

  async function createUser(auth, email, fullName) {
    const session = await auth.register({ email, password: 'Passw0rd123', fullName });
    const account = db
      .prepare(`SELECT id, account_number FROM accounts WHERE user_id = ? AND kind = 'CUSTOMER'`)
      .get(session.user.id);
    return { userId: session.user.id, accountId: account.id, number: account.account_number };
  }

  const balanceOf = (accountId) =>
    db.prepare('SELECT balance_minor AS balance FROM accounts WHERE id = ?').get(accountId).balance;

  beforeEach(async () => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bankflow-stress-'));
    dbPath = path.join(dir, 'stress.db');
    db = createDb(dbPath);
    const auth = createAuthService(db);
    alice = await createUser(auth, 'alice@example.com', 'Alice Tester');
    bob = await createUser(auth, 'bob@example.com', 'Bob Builder');
  });

  afterEach(() => {
    db.close();
    fs.rmSync(dir, { recursive: true, force: true });
  });

  const startAt = () => Date.now() + START_DELAY_MS;

  test('four processes racing for $1,000: exactly the affordable number succeed', async () => {
    const at = startAt();

    const results = await Promise.all(
      Array.from({ length: 4 }, () => runWorker(['random', dbPath, alice.userId, alice.accountId, bob.number, 20, 3000, at]))
    );

    expect(results.flatMap((r) => r.other)).toEqual([]);
    expect(total(results, 'created')).toBe(33);
    expect(total(results, 'insufficient')).toBe(47);
    expect(balanceOf(alice.accountId)).toBe(100000 - 33 * 3000);
    expect(balanceOf(bob.accountId)).toBe(100000 + 33 * 3000);

    assertLedgerConsistent(db);
    const unbalanced = db
      .prepare(
        `SELECT transfer_id FROM ledger_entries GROUP BY transfer_id
          HAVING SUM(CASE direction WHEN 'CREDIT' THEN amount_minor ELSE -amount_minor END) <> 0`
      )
      .all();
    expect(unbalanced).toEqual([]);
  });

  test('the same idempotency key from several processes moves money once', async () => {
    const at = startAt();

    const results = await Promise.all(
      Array.from({ length: 4 }, () => runWorker(['same-key', dbPath, alice.userId, alice.accountId, bob.number, 5, 5000, at]))
    );

    expect(results.flatMap((r) => r.other)).toEqual([]);
    expect(total(results, 'created')).toBe(1);
    expect(total(results, 'replayed')).toBe(19);
    expect(balanceOf(alice.accountId)).toBe(95000);
    expect(db.prepare(`SELECT COUNT(*) AS c FROM transfers WHERE kind = 'TRANSFER'`).get().c).toBe(1);
    assertLedgerConsistent(db);
  });

  test('money flowing both ways at once is neither created nor destroyed', async () => {
    const at = startAt();

    const results = await Promise.all([
      runWorker(['random', dbPath, alice.userId, alice.accountId, bob.number, 15, 7000, at]),
      runWorker(['random', dbPath, alice.userId, alice.accountId, bob.number, 15, 7000, at]),
      runWorker(['random', dbPath, bob.userId, bob.accountId, alice.number, 15, 7000, at]),
      runWorker(['random', dbPath, bob.userId, bob.accountId, alice.number, 15, 7000, at]),
    ]);

    expect(results.flatMap((r) => r.other)).toEqual([]);
    expect(total(results, 'created')).toBeGreaterThan(0);
    expect(balanceOf(alice.accountId) + balanceOf(bob.accountId)).toBe(200000);
    expect(balanceOf(alice.accountId)).toBeGreaterThanOrEqual(0);
    expect(balanceOf(bob.accountId)).toBeGreaterThanOrEqual(0);
    assertLedgerConsistent(db);
  });
});