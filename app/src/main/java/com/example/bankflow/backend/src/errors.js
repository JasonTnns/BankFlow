'use strict';

const ErrorCode = Object.freeze({

  VALIDATION_ERROR: 'VALIDATION_ERROR',
  RATE_LIMITED: 'RATE_LIMITED',


  UNAUTHORIZED: 'UNAUTHORIZED',
  TOKEN_EXPIRED: 'TOKEN_EXPIRED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  INVALID_REFRESH_TOKEN: 'INVALID_REFRESH_TOKEN',
  FORBIDDEN: 'FORBIDDEN',


  NOT_FOUND: 'NOT_FOUND',
  EMAIL_TAKEN: 'EMAIL_TAKEN',


  INSUFFICIENT_FUNDS: 'INSUFFICIENT_FUNDS',
  LIMIT_EXCEEDED: 'LIMIT_EXCEEDED',
  SAME_ACCOUNT: 'SAME_ACCOUNT',
  ACCOUNT_INACTIVE: 'ACCOUNT_INACTIVE',
  CURRENCY_MISMATCH: 'CURRENCY_MISMATCH',
  IDEMPOTENCY_CONFLICT: 'IDEMPOTENCY_CONFLICT',


  INTERNAL_ERROR: 'INTERNAL_ERROR',
});

class AppError extends Error {

  constructor(status, code, message, details) {
    super(message);
    this.name = 'AppError';
    this.status = status;
    this.code = code;
    this.details = details;
    Error.captureStackTrace?.(this, AppError);
  }


  toResponseBody() {
    const body = { code: this.code, message: this.message };
    if (this.details !== undefined) body.details = this.details;
    return { error: body };
  }
}


const Errors = Object.freeze({
  validation: (message = 'Invalid request', details) =>
    new AppError(400, ErrorCode.VALIDATION_ERROR, message, details),

  unauthorized: (message = 'Authentication required') =>
    new AppError(401, ErrorCode.UNAUTHORIZED, message),

  tokenExpired: () =>
    new AppError(401, ErrorCode.TOKEN_EXPIRED, 'Access token has expired'),

  invalidCredentials: () =>

    new AppError(401, ErrorCode.INVALID_CREDENTIALS, 'Incorrect email or password'),

  invalidRefreshToken: () =>
    new AppError(401, ErrorCode.INVALID_REFRESH_TOKEN, 'Session expired, please sign in again'),

  forbidden: (message = 'You do not have access to this resource') =>
    new AppError(403, ErrorCode.FORBIDDEN, message),

  notFound: (message = 'Resource not found') =>
    new AppError(404, ErrorCode.NOT_FOUND, message),

  emailTaken: () =>
    new AppError(409, ErrorCode.EMAIL_TAKEN, 'An account with this email already exists'),

  idempotencyConflict: () =>
    new AppError(
      409,
      ErrorCode.IDEMPOTENCY_CONFLICT,
      'This idempotency key was already used with different transfer details'
    ),

  insufficientFunds: () =>
    new AppError(422, ErrorCode.INSUFFICIENT_FUNDS, 'Insufficient funds for this transfer'),

  limitExceeded: (details) =>
    new AppError(422, ErrorCode.LIMIT_EXCEEDED, 'This transfer exceeds your daily limit', details),

  sameAccount: () =>
    new AppError(422, ErrorCode.SAME_ACCOUNT, 'Source and destination must be different accounts'),

  accountInactive: () =>
    new AppError(422, ErrorCode.ACCOUNT_INACTIVE, 'One of the accounts is not active'),

  currencyMismatch: () =>
    new AppError(422, ErrorCode.CURRENCY_MISMATCH, 'Accounts must use the same currency'),

  rateLimited: () =>
    new AppError(429, ErrorCode.RATE_LIMITED, 'Too many attempts, please try again later'),

  internal: () =>
    new AppError(500, ErrorCode.INTERNAL_ERROR, 'Something went wrong on our side'),
});

module.exports = { AppError, ErrorCode, Errors };
