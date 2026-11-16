'use strict';

const express = require('express');
const rateLimit = require('express-rate-limit');
const { z } = require('zod');

const config = require('../config');
const { Errors } = require('../errors');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');

const createSchema = z.object({
  nickname: z
    .string({ required_error: 'Nickname is required' })
    .trim()
    .min(1, 'Nickname is required')
    .max(40, 'Nickname is too long'),
  accountNumber: z
    .string({ required_error: 'Account number is required' })
    .trim()
    .regex(/^\d{12}$/, 'Account number must be 12 digits'),
});


function createBeneficiaryRoutes({ beneficiaryService, requireAuth }) {
  const router = express.Router();

  const createLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: config.rateLimit.lookupMaxPerMinute,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.isTest,
    keyGenerator: (req) => req.auth.userId,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });

  router.get(
    '/beneficiaries',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({ beneficiaries: beneficiaryService.list(req.auth.userId) });
    })
  );

  router.post(
    '/beneficiaries',
    requireAuth,
    createLimiter,
    validate(createSchema),
    asyncHandler(async (req, res) => {
      res.status(201).json({ beneficiary: beneficiaryService.create(req.auth.userId, req.body) });
    })
  );

  router.delete(
    '/beneficiaries/:beneficiaryId',
    requireAuth,
    asyncHandler(async (req, res) => {
      beneficiaryService.remove(req.auth.userId, req.params.beneficiaryId);
      res.status(204).end();
    })
  );

  return router;
}

module.exports = { createBeneficiaryRoutes, createSchema };