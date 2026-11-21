'use strict';

/**
 * Profile: the signed-in user's own details, and closing their account.
 *
 * Only the display name and the theme preference can be changed. Email is the login identity and the
 * daily transfer limit is a risk control, so neither is editable here.
 */

const bcrypt = require('bcryptjs');

const { inImmediateTx } = require('../db');
const { Errors } = require('../errors');
const { postDoubleEntry, ensureReserveAccount } = require('../ledger');
const { newId, nowIso } = require('../utils/ids');

function toDto(row) {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    themePreference: row.theme_preference,
    dailyTransferLimitMinor: row.daily_transfer_limit_minor,
    createdAt: row.created_at,
  };
}

/**
 * @param {import('better-sqlite3').Database} db
 */
function createProfileService(db) {
  const read = db.prepare('SELECT * FROM users WHERE id = ?');

  function getProfile(userId) {
    const row = read.get(userId);
    if (!row) throw Errors.notFound('User not found');
    return toDto(row);
  }

  /**
   * @param {string} userId from req.auth
   * @param {{ fullName?: string, themePreference?: string }} changes already validated; at least one set
   */
  function updateProfile(userId, changes) {
    const sets = [];
    const params = [];
    if (changes.fullName !== undefined) {
      sets.push('full_name = ?');
      params.push(changes.fullName);
    }
    if (changes.themePreference !== undefined) {
      sets.push('theme_preference = ?');
      params.push(changes.themePreference);
    }
    sets.push('updated_at = ?');
    params.push(nowIso());

    // ONE statement touching only the fields that were sent. Reading the row first and writing every
    // column back would let two simultaneous requests (a rename and a theme change) overwrite each other.
    // The column names are fixed literals above; only the values come from the request.
    const result = db.prepare(`UPDATE users SET ${sets.join(', ')} WHERE id = ?`).run(...params, userId);
    if (result.changes === 0) throw Errors.notFound('User not found');

    return getProfile(userId);
  }

  /**
   * Closes the account after a password confirmation.
   *
   * The ledger is permanent (financial records), so the user is anonymised instead of deleted:
   *  - email, name and password are replaced, so the login is gone and the email is free again;
   *  - sessions and the user's own saved recipients are deleted;
   *  - accounts are closed, and any remaining balance is paid out through the ledger back to the bank;
   *  - other people's history keeps the transaction, with the counterparty shown as "Deleted user".
   */
  async function deleteProfile(userId, password) {
    const user = read.get(userId);
    if (!user) throw Errors.notFound('User not found');
    // Hash comparison happens before the write lock is taken (bcrypt is slow).
    if (!(await bcrypt.compare(password, user.password_hash))) throw Errors.passwordIncorrect();

    inImmediateTx(db, () => {
      const now = nowIso();
      const accounts = db
        .prepare(`SELECT * FROM accounts WHERE user_id = ? AND kind = 'CUSTOMER'`)
        .all(userId);

      for (const account of accounts) {
        if (account.balance_minor <= 0) continue;
        const reserve = ensureReserveAccount(db, account.currency);
        const transferId = newId();
        db.prepare(
          `INSERT INTO transfers
             (id, idempotency_key, initiated_by_user_id, kind, source_account_id, destination_account_id,
              amount_minor, currency, status, category, description, created_at, completed_at)
           VALUES (?, ?, NULL, 'TRANSFER', ?, ?, ?, ?, 'COMPLETED', 'OTHER', 'Account closure payout', ?, ?)`
        ).run(transferId, `closure-${account.id}`, account.id, reserve.id, account.balance_minor, account.currency, now, now);
        postDoubleEntry(db, {
          transferId,
          sourceAccountId: account.id,
          destinationAccountId: reserve.id,
          amountMinor: account.balance_minor,
          now,
        });
      }

      db.prepare(`UPDATE accounts SET status = 'CLOSED', name = 'Closed account' WHERE user_id = ? AND kind = 'CUSTOMER'`).run(userId);
      db.prepare('DELETE FROM beneficiaries WHERE user_id = ?').run(userId);
      db.prepare('DELETE FROM sessions WHERE user_id = ?').run(userId);
      db.prepare(
        `UPDATE users
            SET email = ?, full_name = 'Deleted user', password_hash = '!', theme_preference = 'SYSTEM', updated_at = ?
          WHERE id = ?`
      ).run(`deleted-${userId}@deleted.invalid`, now, userId);
    });
  }

  return { getProfile, updateProfile, deleteProfile };
}

module.exports = { createProfileService };