'use strict';

const { AppError, ErrorCode } = require('../errors');

function requireHttps(req, _res, next) {
  if (req.secure || req.path === '/health') return next();
  return next(new AppError(403, ErrorCode.FORBIDDEN, 'HTTPS is required'));
}

module.exports = { requireHttps };