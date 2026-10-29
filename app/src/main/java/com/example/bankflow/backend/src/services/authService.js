'use strict';

/**
 * Authentication service: register, login, refresh (with rotation), logout,
 * and access-token verification.
 *
 * Session model
 *  - Access token : short-lived JWT {sub: userId, sid: sessionId}, never stored.
 *  - Refresh token: opaque random string; only its SHA-256 hash is stored.
 *  - Every refresh ROTATES the token. Replaying an already-rotated token is
 *    treated as theft and revokes all of that user's sessions.
 *  - Logout revokes the session, which also invalidates its access tokens
 *    immediately (verifyAccessToken checks the session row).
 */

const bcrypt = require('bcryptjs');
const jwt = require('jsonwebtoken');
const { postDoubleEntry } = require('../ledger');

const config = require('../config');
const { inImmediateTx } = require('../db');
const { Errors } = require('../errors');
const {
  newId,
  generateAccountNumber,
  generateOpaqueToken,
  hashToken,
  nowIso,
  isoFromNow,
} = require('../utils/ids');

const RESERVE_ACCOUNT_NUMBER = 'SYSTEM-RESERVE';
const DAY_MS = 24 * 60 * 60 * 1000;

// Compared against when an email is unknown, so "no such user" takes about as
// long as "wrong password" and timing can't reveal which emails exist.
const DUMMY_HASH = bcrypt.hashSync('not-a-real-password', config.auth.bcryptRounds);

function normalizeEmail(email) {
  return String(email).trim().toLowerCase();
}

function toUserDto(row) {
  return {
    id: row.id,
    email: row.email,
    fullName: row.full_name,
    themePreference: row.theme_preference,
  };
}

function isUniqueViolation(err, column) {
  return (
    err &&
    err.code === 'SQLITE_CONSTRAINT_UNIQUE' &&
    (!column || String(err.message).includes(column))
  );
}

/**
 * @param {import('better-sqlite3').Database} db
 */
function createAuthService(db) {
  // ---------------------------------------------------------------- helpers

  /** Find the bank reserve account (creating it on first use). */
  function ensureReserveAccount(currency) {
    let reserve = db
      .prepare('SELECT * FROM accounts WHERE account_number = ?')
      .get(RESERVE_ACCOUNT_NUMBER);
    if (!reserve) {
      db.prepare(
        `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor)
         VALUES (?, NULL, 'SYSTEM', 'SYSTEM', 'Bank Reserve', ?, ?, 0)`
      ).run(newId(), RESERVE_ACCOUNT_NUMBER, currency);
      reserve = db
        .prepare('SELECT * FROM accounts WHERE account_number = ?')
        .get(RESERVE_ACCOUNT_NUMBER);
    }
    return reserve;
  }

  function uniqueAccountNumber() {
    const exists = db.prepare('SELECT 1 FROM accounts WHERE account_number = ?');
    for (let attempt = 0; attempt < 10; attempt++) {
      const candidate = generateAccountNumber();
      if (!exists.get(candidate)) return candidate;
    }
    throw new Error('Could not generate a unique account number');
  }

  /**
   * Record the welcome bonus as a proper double-entry deposit:
   * debit the bank reserve, credit the new account.
   * (Runs inside the registration transaction.)
   */
    function postWelcomeDeposit(accountId, amountMinor, currency) {
      const reserve = ensureReserveAccount(currency);
      const transferId = newId();
      const now = nowIso();

      db.prepare(
        `INSERT INTO transfers
           (id, idempotency_key, initiated_by_user_id, kind, source_account_id,
            destination_account_id, amount_minor, currency, status, category,
            description, created_at, completed_at)
         VALUES (?, ?, NULL, 'DEPOSIT', ?, ?, ?, ?, 'COMPLETED', 'INCOME', 'Welcome bonus', ?, ?)`
      ).run(transferId, `welcome-${accountId}`, reserve.id, accountId, amountMinor, currency, now, now);

      postDoubleEntry(db, {
        transferId,
        sourceAccountId: reserve.id,
        destinationAccountId: accountId,
        amountMinor,
        now,
      });
    }

  /** Create a session row and return the token pair. Call inside a transaction if needed. */
  function issueSession(userRow, deviceName) {
    const sessionId = newId();
    const refreshToken = generateOpaqueToken();

    db.prepare(
      `INSERT INTO sessions (id, user_id, refresh_token_hash, device_name, expires_at)
       VALUES (?, ?, ?, ?, ?)`
    ).run(
      sessionId,
      userRow.id,
      hashToken(refreshToken),
      deviceName ?? null,
      isoFromNow(config.auth.refreshTokenTtlDays * DAY_MS)
    );

    const accessToken = jwt.sign({ sub: userRow.id, sid: sessionId }, config.auth.jwtSecret, {
      algorithm: 'HS256',
      expiresIn: config.auth.accessTokenTtlSeconds,
    });

    return {
      accessToken,
      refreshToken,
      expiresInSeconds: config.auth.accessTokenTtlSeconds,
      user: toUserDto(userRow),
    };
  }

  function revokeAllSessionsForUser(userId) {
    db.prepare(
      'UPDATE sessions SET revoked_at = ? WHERE user_id = ? AND revoked_at IS NULL'
    ).run(nowIso(), userId);
  }

  // ---------------------------------------------------------------- public API

  /**
   * Register a new user, give them a checking account with the welcome bonus
   * (via the ledger), and sign them in.
   * Input is assumed already validated by the route layer (zod).
   */
  async function register({ email, password, fullName, deviceName }) {
    const normalizedEmail = normalizeEmail(email);
    // Hash BEFORE opening the write transaction so the DB lock is held briefly.
    const passwordHash = await bcrypt.hash(password, config.auth.bcryptRounds);
    const currency = config.banking.defaultCurrency;

    try {
      return inImmediateTx(db, () => {
        const taken = db.prepare('SELECT 1 FROM users WHERE email = ?').get(normalizedEmail);
        if (taken) throw Errors.emailTaken();

        const userId = newId();
        db.prepare(
          'INSERT INTO users (id, email, full_name, password_hash) VALUES (?, ?, ?, ?)'
        ).run(userId, normalizedEmail, fullName.trim(), passwordHash);

        const accountId = newId();
        db.prepare(
          `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor)
           VALUES (?, ?, 'CUSTOMER', 'CHECKING', 'Everyday Checking', ?, ?, 0)`
        ).run(accountId, userId, uniqueAccountNumber(), currency);

        postWelcomeDeposit(accountId, config.banking.welcomeBonusMinor, currency);

        const userRow = db.prepare('SELECT * FROM users WHERE id = ?').get(userId);
        return issueSession(userRow, deviceName);
      });
    } catch (err) {
      if (isUniqueViolation(err, 'users.email')) throw Errors.emailTaken();
      throw err;
    }
  }

  /** Verify credentials and open a new session. */
  async function login({ email, password, deviceName }) {
    const userRow = db
      .prepare('SELECT * FROM users WHERE email = ?')
      .get(normalizeEmail(email));

    const passwordOk = await bcrypt.compare(password, userRow ? userRow.password_hash : DUMMY_HASH);
    if (!userRow || !passwordOk) throw Errors.invalidCredentials();

    return inImmediateTx(db, () => issueSession(userRow, deviceName));
  }

  /**
   * Exchange a refresh token for a new token pair (rotation).
   * The old session is revoked; a replayed old token revokes everything.
   */
    function refresh({ refreshToken, deviceName }) {
      const tokenHash = hashToken(refreshToken);

      const result = inImmediateTx(db, () => {
        const session = db
          .prepare('SELECT * FROM sessions WHERE refresh_token_hash = ?')
          .get(tokenHash);
        if (!session) throw Errors.invalidRefreshToken();

        if (session.revoked_at) {
          revokeAllSessionsForUser(session.user_id);
          return { replayed: true };
        }
        if (session.expires_at <= nowIso()) throw Errors.invalidRefreshToken();

        const userRow = db.prepare('SELECT * FROM users WHERE id = ?').get(session.user_id);
        if (!userRow) throw Errors.invalidRefreshToken();

        db.prepare('UPDATE sessions SET revoked_at = ? WHERE id = ?').run(nowIso(), session.id);
        return issueSession(userRow, deviceName ?? session.device_name);
      });

      // The revocation above is committed; now reject the replayed token.
      if (result.replayed) throw Errors.invalidRefreshToken();
      return result;
    }

  /** Revoke a session. Idempotent: logging out twice is fine. */
  function logout(sessionId) {
    db.prepare('UPDATE sessions SET revoked_at = ? WHERE id = ? AND revoked_at IS NULL').run(
      nowIso(),
      sessionId
    );
  }

  /**
   * Validate an access token and confirm its session is still active.
   * @returns {{ userId: string, sessionId: string }}
   */
  function verifyAccessToken(token) {
    let payload;
    try {
      payload = jwt.verify(token, config.auth.jwtSecret, { algorithms: ['HS256'] });
    } catch (err) {
      if (err.name === 'TokenExpiredError') throw Errors.tokenExpired();
      throw Errors.unauthorized('Invalid access token');
    }

    const session = db
      .prepare('SELECT 1 FROM sessions WHERE id = ? AND user_id = ? AND revoked_at IS NULL')
      .get(payload.sid, payload.sub);
    if (!session) throw Errors.unauthorized('Session is no longer active');

    return { userId: payload.sub, sessionId: payload.sid };
  }

  return { register, login, refresh, logout, verifyAccessToken };
}

module.exports = { createAuthService };