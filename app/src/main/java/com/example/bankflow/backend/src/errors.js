'use strict';

const ErrorCode = Object.freeze({

  VALIDATION_ERROR: 'VALIDATION_ERROR',
  RATE_LIMITED: 'RATE_LIMITED',


  UNAUTHORIZED: 'UNAUTHORIZED',
  TOKEN_EXPIRED: 'TOKEN_EXPIRED',
  INVALID_CREDENTIALS: 'INVALID_CREDENTIALS',
  INVALID_REFRESH_TOKEN: 'INVALID_REFRESH_TOKEN',
  PASSWORD_INCORRECT: 'PASSWORD_INCORRECT',
  FORBIDDEN: 'FORBIDDEN',


  NOT_FOUND: 'NOT_FOUND',
  EMAIL_TAKEN: 'EMAIL_TAKEN',
  BENEFICIARY_EXISTS: 'BENEFICIARY_EXISTS',


  INSUFFICIENT_FUNDS: 'INSUFFICIENT_FUNDS',
  LIMIT_EXCEEDED: 'LIMIT_EXCEEDED',
  SAME_ACCOUNT: 'SAME_ACCOUNT',
  ACCOUNT_INACTIVE: 'ACCOUNT_INACTIVE',
  CURRENCY_MISMATCH: 'CURRENCY_MISMATCH',
  IDEMPOTENCY_CONFLICT: 'IDEMPOTENCY_CONFLICT',


  INTERNAL_ERROR: 'INTERNAL_ERROR',
});

const IssueCode = Object.freeze({
  REQUIRED: 'REQUIRED',
  INVALID_TYPE: 'INVALID_TYPE',
  NOT_INTEGER: 'NOT_INTEGER',
  TOO_SHORT: 'TOO_SHORT',
  TOO_LONG: 'TOO_LONG',
  TOO_SMALL: 'TOO_SMALL',
  TOO_LARGE: 'TOO_LARGE',
  INVALID_EMAIL: 'INVALID_EMAIL',
  INVALID_UUID: 'INVALID_UUID',
  INVALID_DATETIME: 'INVALID_DATETIME',
  INVALID_FORMAT: 'INVALID_FORMAT',
  INVALID_OPTION: 'INVALID_OPTION',
  PASSWORD_NEEDS_LETTER_AND_NUMBER: 'PASSWORD_NEEDS_LETTER_AND_NUMBER',
  PASSWORD_TOO_LONG: 'PASSWORD_TOO_LONG',
  OWN_ACCOUNT: 'OWN_ACCOUNT',
  DESTINATION_CONFLICT: 'DESTINATION_CONFLICT',
  RANGE_INVALID: 'RANGE_INVALID',
  RANGE_TOO_LONG: 'RANGE_TOO_LONG',
  AMOUNT_RANGE_INVALID: 'AMOUNT_RANGE_INVALID',
  NOTHING_TO_UPDATE: 'NOTHING_TO_UPDATE',
  INVALID_CURSOR: 'INVALID_CURSOR',
  INVALID: 'INVALID',
});

function validationDetails(items) {
  const fields = {};
  const issues = {};
  for (const { field, code, message, params } of items) {
    (fields[field] ??= []).push(message);
    (issues[field] ??= []).push(params ? { code, params } : { code });
  }
  return { fields, issues };
}

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
      beneficiaryLimitReached: () =>
        new AppError(
          422,
          ErrorCode.BENEFICIARY_LIMIT_REACHED,
          'You have reached the maximum number of saved recipients'
        ),

  unauthorized: (message = 'Authentication required') =>
    new AppError(401, ErrorCode.UNAUTHORIZED, message),

  tokenExpired: () =>
    new AppError(401, ErrorCode.TOKEN_EXPIRED, 'Access token has expired'),

  invalidCredentials: () =>

    new AppError(401, ErrorCode.INVALID_CREDENTIALS, 'Incorrect email or password'),

  invalidRefreshToken: () =>
    new AppError(401, ErrorCode.INVALID_REFRESH_TOKEN, 'Session expired, please sign in again'),
    // 403 on purpose: a 401 would make the mobile client try to refresh its session and retry.
    passwordIncorrect: () => new AppError(403, ErrorCode.PASSWORD_INCORRECT, 'Incorrect password'),
  forbidden: (message = 'You do not have access to this resource') =>
    new AppError(403, ErrorCode.FORBIDDEN, message),

  notFound: (message = 'Resource not found') =>
    new AppError(404, ErrorCode.NOT_FOUND, message),

  emailTaken: () =>
    new AppError(409, ErrorCode.EMAIL_TAKEN, 'An account with this email already exists'),

  beneficiaryExists: () =>
    new AppError(409, ErrorCode.BENEFICIARY_EXISTS, 'This recipient is already saved'),
      BENEFICIARY_LIMIT_REACHED: 'BENEFICIARY_LIMIT_REACHED',

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
