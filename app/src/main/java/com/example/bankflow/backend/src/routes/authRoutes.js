'use strict';

/**
 * Auth endpoints
 *
 *   POST /auth/register   -> 201 { accessToken, refreshToken, expiresInSeconds, user }
 *   POST /auth/login      -> 200 same shape
 *   POST /auth/refresh    -> 200 same shape (old refresh token is revoked)
 *   POST /auth/logout     -> 204 (requires a valid access token)
 */

const express = require('express');
const rateLimit = require('express-rate-limit');
const { z } = require('zod');

const config = require('../config');
const { Errors } = require('../errors');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');

// ------------------------------------------------------------------ schemas

const emailSchema = z
  .string({ required_error: 'Email is required' })
  .trim()
  .toLowerCase()
  .max(254, 'Email is too long')
  .email('Enter a valid email address');

const passwordSchema = z
  .string({ required_error: 'Password is required' })
  .min(config.auth.minPasswordLength, `Password must be at least ${config.auth.minPasswordLength} characters`)
  // bcrypt silently ignores everything after 72 BYTES, so reject longer input.
  .refine((value) => Buffer.byteLength(value, 'utf8') <= 72, 'Password is too long (max 72 bytes)')
  .refine((value) => /[A-Za-z]/.test(value) && /\d/.test(value), 'Password must contain a letter and a number');

const deviceNameSchema = z.string().trim().max(100).optional();

const registerSchema = z.object({
  email: emailSchema,
  password: passwordSchema,
  fullName: z
    .string({ required_error: 'Full name is required' })
    .trim()
    .min(1, 'Full name is required')
    .max(100, 'Full name is too long'),
  deviceName: deviceNameSchema,
});

// Login does NOT re-apply the strength rules: it only needs "something was sent".
const loginSchema = z.object({
  email: emailSchema,
  password: z.string({ required_error: 'Password is required' }).min(1, 'Password is required').max(200),
  deviceName: deviceNameSchema,
});

const refreshSchema = z.object({
  refreshToken: z.string({ required_error: 'Refresh token is required' }).min(20).max(200),
  deviceName: deviceNameSchema,
});

// ------------------------------------------------------------------ router

/**
 * @param {object} deps
 * @param {ReturnType<import('../services/authService').createAuthService>} deps.authService
 * @param {import('express').RequestHandler} deps.requireAuth
 */
function createAuthRoutes({ authService, requireAuth }) {
  const router = express.Router();

  // Brute-force protection for credential endpoints (per IP).
  const credentialLimiter = rateLimit({
    windowMs: config.rateLimit.authWindowMinutes * 60 * 1000,
    limit: config.rateLimit.authMaxAttempts,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.isTest,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });

  router.post(
    '/register',
    credentialLimiter,
    validate(registerSchema),
    asyncHandler(async (req, res) => {
      const session = await authService.register(req.body);
      res.status(201).json(session);
    })
  );

  router.post(
    '/login',
    credentialLimiter,
    validate(loginSchema),
    asyncHandler(async (req, res) => {
      const session = await authService.login(req.body);
      res.status(200).json(session);
    })
  );

  router.post(
    '/refresh',
    validate(refreshSchema),
    asyncHandler(async (req, res) => {
      const session = authService.refresh(req.body);
      res.status(200).json(session);
    })
  );

  router.post(
    '/logout',
    requireAuth,
    asyncHandler(async (req, res) => {
      authService.logout(req.auth.sessionId);
      res.status(204).end();
    })
  );

  return router;
}

module.exports = { createAuthRoutes, registerSchema, loginSchema, refreshSchema };