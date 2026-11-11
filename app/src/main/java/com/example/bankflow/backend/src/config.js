'use strict';



require('dotenv').config();

const env = process.env.NODE_ENV || 'development';
const isProduction = env === 'production';
const isTest = env === 'test';


function intFromEnv(name, fallback) {
  const raw = process.env[name];
  if (raw === undefined || raw === '') return fallback;
  const value = Number.parseInt(raw, 10);
  if (!Number.isInteger(value) || value <= 0) {
    throw new Error(`Invalid ${name}: expected a positive integer, got "${raw}"`);
  }
  return value;
}


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

const config = Object.freeze({
  env,
  isProduction,
  isTest,

  port: intFromEnv('PORT', 3000),


  dbPath: process.env.DB_PATH || undefined,

  auth: Object.freeze({
    jwtSecret: resolveJwtSecret(),

    accessTokenTtlSeconds: intFromEnv('ACCESS_TOKEN_TTL_SECONDS', 15 * 60),
    refreshTokenTtlDays: intFromEnv('REFRESH_TOKEN_TTL_DAYS', 30),

    bcryptRounds: isTest ? 4 : intFromEnv('BCRYPT_ROUNDS', 10),
    minPasswordLength: 8,
  }),

  rateLimit: Object.freeze({

    authMaxAttempts: intFromEnv('AUTH_RATE_LIMIT_MAX', 10),
    authWindowMinutes: intFromEnv('AUTH_RATE_LIMIT_WINDOW_MINUTES', 15),
    lookupMaxPerMinute: intFromEnv('LOOKUP_RATE_LIMIT_PER_MINUTE', 30),
  }),

  banking: Object.freeze({
    defaultCurrency: 'USD',

    welcomeBonusMinor: intFromEnv('WELCOME_BONUS_MINOR', 100000),

    defaultPageSize: 20,
    maxPageSize: 50,
  }),
});

module.exports = config;
