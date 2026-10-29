'use strict';

/**
 * Identifier, token and timestamp helpers.
 * All randomness comes from Node's crypto module (cryptographically secure),
 * never Math.random().
 */

const crypto = require('crypto');

/** New UUID v4 for primary keys. */
function newId() {
  return crypto.randomUUID();
}

/**
 * New customer-facing account number: 12 digits, e.g. "483920175531".
 * The column has a UNIQUE constraint; callers should retry on the (very
 * unlikely) collision.
 */
function generateAccountNumber() {
  let digits = '';
  for (let i = 0; i < 12; i++) {
    digits += crypto.randomInt(0, 10);
  }
  // Avoid a leading zero so the number never looks truncated in the UI.
  return digits[0] === '0' ? '1' + digits.slice(1) : digits;
}

/** Opaque random refresh token (sent to the client once, never stored raw). */
function generateOpaqueToken() {
  return crypto.randomBytes(48).toString('base64url');
}

/**
 * SHA-256 hash of a token, hex encoded. Only this hash is stored in the
 * database, so a leaked DB can't be used to impersonate users. SHA-256 is
 * appropriate here (unlike for passwords) because the token is already
 * high-entropy random data.
 */
function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

/** Current time as UTC ISO-8601, matching the schema's default format. */
function nowIso() {
  return new Date().toISOString();
}


function isoFromNow(ms) {
  return new Date(Date.now() + ms).toISOString();
}


function startOfUtcDayIso(date = new Date()) {
  return new Date(
    Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
  ).toISOString();
}

module.exports = {
  newId,
  generateAccountNumber,
  generateOpaqueToken,
  hashToken,
  nowIso,
  isoFromNow,
  startOfUtcDayIso,
};
