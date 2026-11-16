'use strict';

const express = require('express');
const { z } = require('zod');

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
  });

function createProfileRoutes({ profileService, requireAuth }) {
  const router = express.Router();

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

  return router;
}

module.exports = { createProfileRoutes, updateProfileSchema };