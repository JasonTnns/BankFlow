'use strict';

/**
 * Transfer service: the heart of the backend.
 *
 * createTransfer guarantees, in one BEGIN IMMEDIATE transaction:
 *   - Authorization : the source account must belong to the caller. The
 *                     response for "not yours" and "doesn't exist" is identical.
 *   - Idempotency   : same (user, idempotencyKey) returns the original result
 *                     instead of moving money twice. Same key with a different
 *                     payload is rejected (IDEMPOTENCY_CONFLICT).
 *   - Rules         : accounts active, same currency, enough funds, daily limit
 *                     (limit applies to transfers to OTHER users only).
 *   - Atomicity     : debit + credit + ledger rows commit together or not at all.
 *   - Auditability  : rule failures are stored as FAILED transfers with a reason.
 *
 * Request-side input is assumed validated by zod; amounts are integer cents.
 */

const { inImmediateTx } = require('../db');
const { Errors, IssueCode, validationDetails } = require('../errors');
const { postDoubleEntry } = require('../ledger');
const { newId, nowIso, startOfUtcDayIso } = require('../utils/ids');
const { isValidPositiveMinor } = require('../utils/money');

/** Failure reasons that are recorded as FAILED transfers, and how to rebuild their error. */
const FAILURE_ERRORS = {
  ACCOUNT_INACTIVE: () => Errors.accountInactive(),
  CURRENCY_MISMATCH: () => Errors.currencyMismatch(),
  INSUFFICIENT_FUNDS: () => Errors.insufficientFunds(),
  LIMIT_EXCEEDED: (details) => Errors.limitExceeded(details),
};

function toTransferDto(row) {
  return {
    id: row.id,
    kind: row.kind,
    status: row.status,
    failureReason: row.failure_reason,
    sourceAccountId: row.source_account_id,
    destinationAccountId: row.destination_account_id,
    amountMinor: row.amount_minor,
    currency: row.currency,
    category: row.category,
    description: row.description,
    createdAt: row.created_at,
    completedAt: row.completed_at,
  };
}

/**
 * @param {import('better-sqlite3').Database} db
 */
function createTransferService(db) {
  /** Total sent today by this user to OTHER users' accounts (completed only). */
  function sentTodayToOthersMinor(userId) {
    const row = db
      .prepare(
        `SELECT COALESCE(SUM(t.amount_minor), 0) AS total
           FROM transfers t
           JOIN accounts d ON d.id = t.destination_account_id
          WHERE t.initiated_by_user_id = ?
            AND t.kind = 'TRANSFER'
            AND t.status = 'COMPLETED'
            AND t.created_at >= ?
            AND d.user_id <> ?`
      )
      .get(userId, startOfUtcDayIso(), userId);
    return row.total;
  }

  /** Returns { reason, details? } for the first violated rule, or null. */
  function findRuleViolation(userId, source, destination, amountMinor) {
    if (source.status !== 'ACTIVE' || destination.status !== 'ACTIVE') {
      return { reason: 'ACCOUNT_INACTIVE' };
    }
    if (source.currency !== destination.currency) {
      return { reason: 'CURRENCY_MISMATCH' };
    }
    // Balances were read inside the write transaction, so this can't be stale.
    if (source.balance_minor < amountMinor) {
      return { reason: 'INSUFFICIENT_FUNDS' };
    }
    if (destination.user_id !== userId) {
      const limit = db
        .prepare('SELECT daily_transfer_limit_minor AS limit_minor FROM users WHERE id = ?')
        .get(userId).limit_minor;
      const used = sentTodayToOthersMinor(userId);
      if (used + amountMinor > limit) {
        return {
          reason: 'LIMIT_EXCEEDED',
          details: {
            limitMinor: limit,
            usedTodayMinor: used,
            remainingMinor: Math.max(0, limit - used),
          },
        };
      }
    }
    return null;
  }

  /**
   * Move money from one of the caller's accounts to another account.
   *
   * @param {string} userId from req.auth, never from the request body
   * @param {object} input
   * @param {string} input.idempotencyKey client-generated UUID, one per user intent
   * @param {string} input.sourceAccountId
   * @param {string} [input.destinationAccountId]     one of the caller's own accounts, OR
   * @param {string} [input.destinationAccountNumber] any customer account number
   * @param {number} input.amountMinor positive integer cents
   * @param {string} [input.category]
   * @param {string} [input.description]
   * @returns {{ transfer: object, sourceAccountBalanceMinor: number, replayed: boolean }}
   */
  function createTransfer(userId, input) {
    const {
      idempotencyKey,
      sourceAccountId,
      destinationAccountId,
      destinationAccountNumber,
      amountMinor,
      category = 'TRANSFER',
      description = null,
    } = input;

    // Defence in depth: the route already validated this.
        if (!isValidPositiveMinor(amountMinor)) {
          const message = 'Amount must be a positive whole number of cents';
          throw Errors.validation(
            message,
            validationDetails([{ field: 'amountMinor', code: IssueCode.INVALID, message }])
          );
        }

    const outcome = inImmediateTx(db, () => {
      // --- Authorization: the source must be one of the caller's accounts.
      const source = db
        .prepare(`SELECT * FROM accounts WHERE id = ? AND user_id = ? AND kind = 'CUSTOMER'`)
        .get(sourceAccountId, userId);
      if (!source) throw Errors.notFound('Source account not found');

      // --- Destination: an own account by id, or any customer account by number.
      const destination = destinationAccountId
        ? db
            .prepare(`SELECT * FROM accounts WHERE id = ? AND user_id = ? AND kind = 'CUSTOMER'`)
            .get(destinationAccountId, userId)
        : db
            .prepare(`SELECT * FROM accounts WHERE account_number = ? AND kind = 'CUSTOMER'`)
            .get(destinationAccountNumber);
      if (!destination) throw Errors.notFound('Destination account not found');

      if (source.id === destination.id) throw Errors.sameAccount();

      // --- Idempotency: a retry of the same intent returns the original result.
      const existing = db
        .prepare('SELECT * FROM transfers WHERE initiated_by_user_id = ? AND idempotency_key = ?')
        .get(userId, idempotencyKey);
      if (existing) {
        const samePayload =
          existing.source_account_id === source.id &&
          existing.destination_account_id === destination.id &&
          existing.amount_minor === amountMinor;
        if (!samePayload) throw Errors.idempotencyConflict();
        return { replayed: true, transferId: existing.id };
      }

      // --- Business rules.
      const violation = findRuleViolation(userId, source, destination, amountMinor);
      const transferId = newId();
      const now = nowIso();
      const insert = db.prepare(
        `INSERT INTO transfers
           (id, idempotency_key, initiated_by_user_id, kind, source_account_id,
            destination_account_id, amount_minor, currency, status, failure_reason,
            category, description, created_at, completed_at)
         VALUES (?, ?, ?, 'TRANSFER', ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      );

      if (violation) {
        // Keep an audit record of the rejected attempt. The transaction COMMITS;
        // the error is thrown after it (throwing here would roll the record back).
        insert.run(
          transferId, idempotencyKey, userId, source.id, destination.id, amountMinor,
          source.currency, 'FAILED', violation.reason, category, description, now, null
        );
        return { replayed: false, transferId, violation };
      }

      // --- Success: record the transfer and move the money atomically.
      insert.run(
        transferId, idempotencyKey, userId, source.id, destination.id, amountMinor,
        source.currency, 'COMPLETED', null, category, description, now, now
      );
      postDoubleEntry(db, {
        transferId,
        sourceAccountId: source.id,
        destinationAccountId: destination.id,
        amountMinor,
        now,
      });
      return { replayed: false, transferId };
    });

    // The transaction has committed. Now surface any recorded failure.
    if (outcome.violation) {
      throw FAILURE_ERRORS[outcome.violation.reason](outcome.violation.details);
    }

    const row = db.prepare('SELECT * FROM transfers WHERE id = ?').get(outcome.transferId);

    // A replay of a FAILED attempt fails the same way again.
    if (row.status === 'FAILED') {
      throw (FAILURE_ERRORS[row.failure_reason] ?? (() => Errors.internal()))();
    }

    const sourceBalance = db
      .prepare('SELECT balance_minor FROM accounts WHERE id = ?')
      .get(row.source_account_id).balance_minor;

    return {
      transfer: toTransferDto(row),
      sourceAccountBalanceMinor: sourceBalance,
      replayed: outcome.replayed,
    };
  }

  /**
   * Fetch one transfer. The caller must own the source or destination account;
   * otherwise it is reported as not found.
   */
  function getTransfer(userId, transferId) {
    const row = db
      .prepare(
        `SELECT t.*, s.user_id AS source_user_id
           FROM transfers t
           JOIN accounts s ON s.id = t.source_account_id
           JOIN accounts d ON d.id = t.destination_account_id
          WHERE t.id = ? AND (s.user_id = ? OR d.user_id = ?)`
      )
      .get(transferId, userId, userId);
    if (!row) throw Errors.notFound('Transfer not found');

    return { ...toTransferDto(row), direction: row.source_user_id === userId ? 'OUT' : 'IN' };
  }

  return { createTransfer, getTransfer };
}

module.exports = { createTransferService };