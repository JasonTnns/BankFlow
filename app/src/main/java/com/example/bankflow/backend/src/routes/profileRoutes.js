'use strict';

/**
 * Profile endpoints (all require a valid access token)
 *
 *   GET    /me  -> { profile }
 *   PATCH  /me  -> { profile }   body: { fullName?, themePreference? }  (at least one)
 *   DELETE /me  -> 204           body: { password }   closes and anonymises the account
 *
 * Anything else in a PATCH body (email, limits, id...) is dropped by validation, so it can never reach
 * the database.
 */

const express = require('express');
const rateLimit = require('express-rate-limit');
const { z } = require('zod');

const config = require('../config');
const { Errors, IssueCode } = require('../errors');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');

const updateProfileSchema = z
  .object({
    fullName: z
      .string()
      .trim()
      .min(1, 'Full name is required')
      .max(100, 'Full name is too long')
      .optional(),
    themePreference: z.enum(['SYSTEM', 'LIGHT', 'DARK']).optional(),
  })
  .refine((body) => body.fullName !== undefined || body.themePreference !== undefined, {
    message: 'Provide fullName or themePreference',
    params: { code: IssueCode.NOTHING_TO_UPDATE },
  });

const deleteAccountSchema = z.object({
  password: z.string({ required_error: 'Password is required' }).min(1, 'Password is required').max(200),
});

/**
 * @param {object} deps
 * @param {ReturnType<import('../services/profileService').createProfileService>} deps.profileService
 * @param {import('express').RequestHandler} deps.requireAuth
 */
function createProfileRoutes({ profileService, requireAuth }) {
  const router = express.Router();

  // A stolen access token must not become a way to guess the password: same budget as login, per user.
  const deleteLimiter = rateLimit({
    windowMs: config.rateLimit.authWindowMinutes * 60 * 1000,
    limit: config.rateLimit.authMaxAttempts,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.isTest,
    keyGenerator: (req) => req.auth.userId,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });

  router.get(
    '/me',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({ profile: profileService.getProfile(req.auth.userId) });
    })
  );

  router.patch(
    '/me',
    requireAuth,
    validate(updateProfileSchema),
    asyncHandler(async (req, res) => {
      res.json({ profile: profileService.updateProfile(req.auth.userId, req.body) });
    })
  );

  router.delete(
    '/me',
    requireAuth,
    deleteLimiter,
    validate(deleteAccountSchema),
    asyncHandler(async (req, res) => {
      await profileService.deleteProfile(req.auth.userId, req.body.password);
      res.status(204).end();
    })
  );

  return router;
}

module.exports = { createProfileRoutes, updateProfileSchema, deleteAccountSchema };