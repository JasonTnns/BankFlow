'use strict';

const crypto = require('crypto');

const { createDb } = require('../../src/db');
const { createTransferService } = require('../../src/services/transferService');

const [mode, dbPath, userId, sourceAccountId, destinationAccountNumber, attemptsArg, amountArg, startAtArg] =
  process.argv.slice(2);

const SHARED_KEY = '7d1f0c64-5b0e-4a63-9a36-2f3c1d9e8b11';

async function main() {
  const db = createDb(dbPath);
  const transfers = createTransferService(db);
  const attempts = Number(attemptsArg);
  const amountMinor = Number(amountArg);

  const wait = Number(startAtArg) - Date.now();
  if (wait > 0) await new Promise((resolve) => setTimeout(resolve, wait));

  const tally = { created: 0, replayed: 0, insufficient: 0, busy: 0, other: [] };
  for (let i = 0; i < attempts; i++) {
    try {
      const result = transfers.createTransfer(userId, {
        idempotencyKey: mode === 'same-key' ? SHARED_KEY : crypto.randomUUID(),
        sourceAccountId,
        destinationAccountNumber,
        amountMinor,
      });
      if (result.replayed) tally.replayed++;
      else tally.created++;
    } catch (error) {
      if (error.code === 'INSUFFICIENT_FUNDS') tally.insufficient++;
      else if (String(error.code).startsWith('SQLITE_BUSY')) tally.busy++;
      else tally.other.push(String(error.code || error.message));
    }
  }

  db.close();
  process.stdout.write(`${JSON.stringify(tally)}\n`);
}

main().catch((error) => {
  process.stderr.write(`${error.stack || error}\n`);
  process.exit(1);
});