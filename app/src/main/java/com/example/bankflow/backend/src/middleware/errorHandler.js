'use strict';

/**
 * Error handling + async helper.
 *
 *  - asyncHandler : Express 4 does not catch rejected promises from async route
 *                   handlers; this wrapper forwards them to the error handler.
 *  - notFoundHandler : unknown routes return our standard error shape.
 *  - errorHandler : converts AppError to JSON; everything else is logged and
 *                   returned as a generic INTERNAL_ERROR (no stack traces or
 *                   SQL text ever reach the client).
 */

const config = require('../config');
const { AppError, Errors } = require('../errors');

/** Wrap an async (req, res) handler so thrown errors reach errorHandler. */
function asyncHandler(fn) {
  return (req, res, next) => {
    Promise.resolve(fn(req, res, next)).catch(next);
  };
}

function notFoundHandler(req, _res, next) {
  next(Errors.notFound(`Route not found: ${req.method} ${req.path}`));
}

// Express identifies error middleware by its 4-argument signature; keep `_next`.
// eslint-disable-next-line no-unused-vars
function errorHandler(err, req, res, _next) {
  // Malformed JSON body (thrown by express.json())
  if (err.type === 'entity.parse.failed') {
    err = Errors.validation('Request body is not valid JSON');
  }
  // Body larger than the configured limit
  if (err.type === 'entity.too.large') {
    err = Errors.validation('Request body is too large');
  }

  if (err instanceof AppError) {
    return res.status(err.status).json(err.toResponseBody());
  }

  // Unexpected: a bug or infrastructure failure. Log the details server-side only.
  if (!config.isTest) {
    console.error(`[error] ${req.method} ${req.originalUrl}`, err);
  }
  const internal = Errors.internal();
  return res.status(internal.status).json(internal.toResponseBody());
}

module.exports = { asyncHandler, notFoundHandler, errorHandler };