'use strict';

const fs = require('fs');
const os = require('os');
const path = require('path');

const { createDb } = require('../src/db');
const { createAuthService } = require('../src/services/authService');
const { createTransferService } = require('../src/services/transferService');
const { backupDatabase, verifyBackup, isHealthy } = require('../src/backup');

describe('backups', () => {
  let dir;
  let db;

  beforeEach(() => {
    dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bankflow-backup-'));
    db = createDb(path.join(dir, 'live.db'));
  });
  afterEach(() => {
    db.close();
    fs.rmSync(dir, { recursive: true, force: true });
  });

  async function populate() {
    const auth = createAuthService(db);
    const alice = await auth.register({ email: 'alice@example.com', password: 'Passw0rd123', fullName: 'Alice A' });
    const bob = await auth.register({ email: 'bob@example.com', password: 'Passw0rd123', fullName: 'Bob B' });
    const accountOf = (userId) =>
      db.prepare(`SELECT id, account_number FROM accounts WHERE user_id = ? AND kind = 'CUSTOMER'`).get(userId);
    const from = accountOf(alice.user.id);
    const to = accountOf(bob.user.id);
    createTransferService(db).createTransfer(alice.user.id, {
      idempotencyKey: '7d1f0c64-5b0e-4a63-9a36-2f3c1d9e8b11',
      sourceAccountId: from.id,
      destinationAccountNumber: to.account_number,
      amountMinor: 2500,
    });
  }

  test('a backup of a live database is complete, healthy and balanced', async () => {
    await populate();

    const copy = await backupDatabase(db, path.join(dir, 'nested', 'backup.db'));
    const report = verifyBackup(copy);

    expect(report).toMatchObject({
      integrity: 'ok',
      reconciliationErrors: 0,
      balanceSum: 0,
      users: 2,
      accounts: 3, // two customers and the bank reserve
      transfers: 3, // two welcome bonuses and one transfer
      ledgerEntries: 6,
    });
    expect(isHealthy(report)).toBe(true);
  });

  test('the copy opens as a normal database with the same balances', async () => {
    await populate();
    const copy = await backupDatabase(db, path.join(dir, 'backup.db'));

    const restored = createDb(copy);
    const balances = restored.prepare(`SELECT balance_minor AS b FROM accounts WHERE kind = 'CUSTOMER' ORDER BY b`).all();
    restored.close();

    expect(balances.map((row) => row.b)).toEqual([97500, 102500]);
  });

  test('it never overwrites an existing file', async () => {
    const target = path.join(dir, 'backup.db');
    fs.writeFileSync(target, 'do not touch');

    await expect(backupDatabase(db, target)).rejects.toThrow(/Refusing to overwrite/);
    expect(fs.readFileSync(target, 'utf8')).toBe('do not touch');
  });

  test('corruption in the ledger is reported as unhealthy', async () => {
    await populate();
    const copy = await backupDatabase(db, path.join(dir, 'backup.db'));

    const Database = require('better-sqlite3');
    const damaged = new Database(copy);
    damaged.prepare(`UPDATE accounts SET balance_minor = balance_minor + 1 WHERE kind = 'CUSTOMER' AND balance_minor > 100000`).run();
    damaged.close();

    const report = verifyBackup(copy);
    expect(report.reconciliationErrors).toBeGreaterThan(0);
    expect(isHealthy(report)).toBe(false);
  });
});