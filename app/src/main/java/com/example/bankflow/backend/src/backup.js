'use strict';

/**
 * Consistent online backup of the SQLite database, then a check that the copy is healthy.
 *
 *   npm run backup                       writes backups/bank-<timestamp>.db
 *   npm run backup -- /path/to/file.db   writes there (refuses to overwrite)
 *
 * Copying the .db file by hand while the server runs can capture a half-written state (the database is in
 * WAL mode). SQLite's backup API takes a consistent snapshot without stopping the server.
 * To RESTORE: stop the server, replace data/bank.db with the backup, start it.
 */

const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

async function backupDatabase(db, destination) {
  const target = path.resolve(destination);
  if (fs.existsSync(target)) throw new Error(`Refusing to overwrite ${target}`);
  fs.mkdirSync(path.dirname(target), { recursive: true });
  await db.backup(target);
  return target;
}

/** Opens the copy read-only and checks it: SQLite integrity, the ledger invariants, and some counts. */
function verifyBackup(file) {
  const copy = new Database(file, { readonly: true, fileMustExist: true });
  try {
    const count = (sql) => copy.prepare(sql).get().n;
    return {
      integrity: copy.pragma('integrity_check', { simple: true }),
      reconciliationErrors: count('SELECT COUNT(*) AS n FROM v_reconciliation_errors'),
      balanceSum: count('SELECT COALESCE(SUM(balance_minor), 0) AS n FROM accounts'),
      users: count('SELECT COUNT(*) AS n FROM users'),
      accounts: count('SELECT COUNT(*) AS n FROM accounts'),
      transfers: count('SELECT COUNT(*) AS n FROM transfers'),
      ledgerEntries: count('SELECT COUNT(*) AS n FROM ledger_entries'),
    };
  } finally {
    copy.close();
  }
}

function isHealthy(report) {
  return report.integrity === 'ok' && report.reconciliationErrors === 0 && report.balanceSum === 0;
}

module.exports = { backupDatabase, verifyBackup, isHealthy };

if (require.main === module) {
  (async () => {
    const config = require('./config');
    const { createDb } = require('./db');

    const stamp = new Date().toISOString().replace(/[:.]/g, '-');
    const destination = process.argv[2] || path.join(__dirname, '..', 'backups', `bank-${stamp}.db`);

    const db = createDb(config.dbPath);
    let written;
    try {
      written = await backupDatabase(db, destination);
    } finally {
      db.close();
    }

    const report = verifyBackup(written);
    console.log(`Backup written to ${written}`);
    console.log(JSON.stringify(report));
    if (!isHealthy(report)) {
      console.error('BACKUP FAILED VERIFICATION: do not rely on this file.');
      process.exit(1);
    }
  })().catch((error) => {
    console.error(error.message);
    process.exit(1);
  });
}