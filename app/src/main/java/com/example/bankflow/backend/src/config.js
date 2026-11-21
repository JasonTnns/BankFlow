'use strict';

/**
 * Central configuration. Every environment-dependent value is read here once, validated, and exported
 * as a frozen object. No other file should touch process.env directly.
 */

require('dotenv').config();

const env = process.env.NODE_ENV || 'development';
const isProduction = env === 'production';
const isTest = env === 'test';

/** Read a positive integer from the environment, or fall back to a default. */
function intFromEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`Invalid ${name}: expected a positive integer, got "${raw}"`);
  }
  return value;
}

function boolFromEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const value = raw.trim().toLowerCase();
  if (['1', 'true', 'yes'].includes(value)) return true;
  if (['0', 'false', 'no'].includes(value)) return false;
  throw new Error(`Invalid ${name}: expected true or false, got "${raw}"`);
}

/** A comma-separated list of web origins, each exactly like https://app.example.com (no path, no trailing slash). */
function originsFromEnv(name) {
  const origins = (process.env[name] || '')
    .split(',')
    .map((item) => item.trim())
    .filter(Boolean);
  for (const origin of origins) {
    let valid = false;
    try {
      valid = new URL(origin).origin === origin;
    } catch {
      valid = false;
    }
    if (!valid) {
      throw new Error(
        `Invalid ${name} entry "${origin}": expected an origin such as https://app.example.com (no path, no trailing slash)`
      );
    }
  }
  return origins;
}

/**
 * The JWT signing secret must never be guessable in production, so the server refuses to start without
 * one. In development and tests we allow a fixed fallback so the project runs out of the box.
 */
function resolveJwtSecret() {
  const secret = process.env.JWT_SECRET;
  if (secret) {
    if (isProduction && secret.length < 32) {
      throw new Error('JWT_SECRET must be at least 32 characters in production');
    }
    return secret;
  }
  if (isProduction) {
    throw new Error('JWT_SECRET is required in production');
  }
  return 'dev-only-insecure-secret-change-me';
}

const corsOrigins = originsFromEnv('CORS_ORIGINS');

const config = Object.freeze({
  env,
  isProduction,
  isTest,

  port: intFromEnv('PORT', 3000),

  /** SQLite file path. src/db.js falls back to data/bank.db when unset. */
  dbPath: process.env.DB_PATH || undefined,

  auth: Object.freeze({
    jwtSecret: resolveJwtSecret(),
    /** Access tokens are short-lived; the refresh token renews them. */
    accessTokenTtlSeconds: intFromEnv('ACCESS_TOKEN_TTL_SECONDS', 15 * 60),
    refreshTokenTtlDays: intFromEnv('REFRESH_TOKEN_TTL_DAYS', 30),
    /** Low cost factor in tests keeps the suite fast; 10+ everywhere else. */
    bcryptRounds: isTest ? 4 : intFromEnv('BCRYPT_ROUNDS', 10),
    minPasswordLength: 8,
  }),

  rateLimit: Object.freeze({
    /** Login/register attempts allowed per IP per window. */
    authMaxAttempts: intFromEnv('AUTH_RATE_LIMIT_MAX', 10),
    authWindowMinutes: intFromEnv('AUTH_RATE_LIMIT_WINDOW_MINUTES', 15),
    /** Recipient lookups and saves per user per minute. */
    lookupMaxPerMinute: intFromEnv('LOOKUP_RATE_LIMIT_PER_MINUTE', 30),
  }),

  banking: Object.freeze({
    defaultCurrency: 'USD',
    /** Starting balance given to a new demo user's checking account, in cents. */
    welcomeBonusMinor: intFromEnv('WELCOME_BONUS_MINOR', 100000), // $1,000.00
    /** Page size bounds for transaction history. */
    defaultPageSize: 20,
    maxPageSize: 50,
  }),

  /** How the HTTP server exposes itself. The defaults are strict in production and relaxed everywhere else. */
  server: Object.freeze({
    /** Web origins allowed to call the API from a browser. The Android app is not a browser and needs none. */
    corsOrigins,
    /** Development only: with no origins listed, allow any, so Swagger "Try it out" works from anywhere. */
    corsOpen: !isProduction && corsOrigins.length === 0,
    /** How many reverse proxies sit in front of the server (0 = none). Needed to see real client IPs and HTTPS. */
    trustProxyHops: intFromEnv('TRUST_PROXY_HOPS', 0),
    /** Refuse plain HTTP (except /health). Needs a TLS-terminating proxy that sets X-Forwarded-Proto. */
    requireHttps: boolFromEnv('REQUIRE_HTTPS', isProduction),
    docsEnabled: boolFromEnv('DOCS_ENABLED', !isProduction),
    /** One JSON line per request: method, path, status, duration. Never headers, query strings or bodies. */
    accessLog: boolFromEnv('ACCESS_LOG', isProduction),
    /** The seed script creates users with a known password and --reset deletes the database. */
    allowSeed: boolFromEnv('ALLOW_SEED', !isProduction),
  }),

  /** Called by server.js before listening. Fails loudly on a combination that would lock everyone out. */
  assertDeployable() {
    if (this.server.requireHttps && this.server.trustProxyHops === 0) {
      throw new Error(
        'REQUIRE_HTTPS is on but TRUST_PROXY_HOPS is not set, so every request would look like plain HTTP. ' +
          'Set TRUST_PROXY_HOPS=1 behind a TLS-terminating proxy, or REQUIRE_HTTPS=false.'
      );
    }
  },
});

module.exports = config;