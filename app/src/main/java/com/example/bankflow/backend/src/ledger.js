'use strict';

/**
 * The ONLY place that moves money between accounts.
 *
 * postDoubleEntry debits one account, credits another, and writes the two
 * balanced ledger rows. It must be called inside an IMMEDIATE transaction
 * (see inImmediateTx) so the whole movement is atomic and race-free.
 *
 * Invariants it maintains:
 *   - debit amount === credit amount
 *   - accounts.balance_minor always equals the sum of its ledger entries
 *   - each ledger row stores the balance after it was applied
 */

const { newId } = require('./utils/ids');
const RESERVE_ACCOUNT_NUMBER = 'SYSTEM-RESERVE';

/** The bank's own account: funds welcome bonuses and receives account-closure payouts. Created on first use. */
function ensureReserveAccount(db, currency) {
  const find = () => db.prepare('SELECT * FROM accounts WHERE account_number = ?').get(RESERVE_ACCOUNT_NUMBER);
  let reserve = find();
  if (!reserve) {
    db.prepare(
      `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor)
       VALUES (?, NULL, 'SYSTEM', 'SYSTEM', 'Bank Reserve', ?, ?, 0)`
    ).run(newId(), RESERVE_ACCOUNT_NUMBER, currency);
    reserve = find();
  }
  return reserve;
}
/**
 * @param {import('better-sqlite3').Database} db
 * @param {object} p
 * @param {string} p.transferId  id of an already-inserted transfers row
 * @param {string} p.sourceAccountId
 * @param {string} p.destinationAccountId
 * @param {number} p.amountMinor positive integer cents
 * @param {string} p.now ISO timestamp
 * @returns {{ sourceBalanceAfterMinor: number, destinationBalanceAfterMinor: number }}
 */
function postDoubleEntry(db, { transferId, sourceAccountId, destinationAccountId, amountMinor, now }) {
  if (!Number.isSafeInteger(amountMinor) || amountMinor <= 0) {
    throw new Error('postDoubleEntry: amountMinor must be a positive integer');
  }

  // If a customer balance would go negative, the CHECK constraint on accounts
  // aborts the statement. Callers check first; this is the last line of defence.
  db.prepare('UPDATE accounts SET balance_minor = balance_minor - ? WHERE id = ?').run(
    amountMinor,
    sourceAccountId
  );
  db.prepare('UPDATE accounts SET balance_minor = balance_minor + ? WHERE id = ?').run(
    amountMinor,
    destinationAccountId
  );

  const readBalance = db.prepare('SELECT balance_minor FROM accounts WHERE id = ?');
  const sourceBalanceAfterMinor = readBalance.get(sourceAccountId).balance_minor;
  const destinationBalanceAfterMinor = readBalance.get(destinationAccountId).balance_minor;

  const insertEntry = db.prepare(
    `INSERT INTO ledger_entries
       (id, transfer_id, account_id, direction, amount_minor, balance_after_minor, created_at)
     VALUES (?, ?, ?, ?, ?, ?, ?)`
  );
  insertEntry.run(newId(), transferId, sourceAccountId, 'DEBIT', amountMinor, sourceBalanceAfterMinor, now);
  insertEntry.run(newId(), transferId, destinationAccountId, 'CREDIT', amountMinor, destinationBalanceAfterMinor, now);

  return { sourceBalanceAfterMinor, destinationBalanceAfterMinor };
}

module.exports = { postDoubleEntry, ensureReserveAccount, RESERVE_ACCOUNT_NUMBER };