'use strict';

/**
 * Express guard for protected routes.
 *
 * Expects:  Authorization: Bearer <accessToken>
 * On success sets:  req.auth = { userId, sessionId }
 * On failure the AppError (UNAUTHORIZED / TOKEN_EXPIRED) goes to the error handler.
 *
 * Routes must ALWAYS take the user id from req.auth.userId, never from the
 * request body or URL. That is what stops a user from reading another user's
 * data by changing an ID.
 */

const { Errors } = require('../errors');

/**
 * @param {{ verifyAccessToken: (token: string) => { userId: string, sessionId: string } }} authService
 */
function createRequireAuth(authService) {
  return function requireAuth(req, _res, next) {
    try {
      const header = req.get('Authorization') || '';
      const [scheme, token] = header.split(' ');

      if (scheme !== 'Bearer' || !token) {
        throw Errors.unauthorized('Missing or malformed Authorization header');
      }

      req.auth = authService.verifyAccessToken(token);
      next();
    } catch (err) {
      next(err);
    }
  };
}

module.exports = { createRequireAuth };