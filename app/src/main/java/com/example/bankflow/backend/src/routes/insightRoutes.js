'use strict';

const express = require('express');
const { z } = require('zod');

const config = require('../config');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');
const { MAX_RANGE_DAYS } = require('../services/insightsService');

const DAY_MS = 24 * 60 * 60 * 1000;

const isoInstant = z
  .string({ required_error: 'Required' })
  .datetime({ offset: true, message: 'Must be an ISO-8601 date-time' })
  .transform((value) => new Date(value).toISOString());

const summaryQuerySchema = z
  .object({
    from: isoInstant,
    to: isoInstant,
    currency: z
      .string()
      .trim()
      .toUpperCase()
      .regex(/^[A-Z]{3}$/, 'Currency must be a 3-letter code')
      .default(config.banking.defaultCurrency),
  })
  .refine((q) => q.from < q.to, { message: '"from" must be before "to"', path: ['from'] })
  .refine((q) => new Date(q.to) - new Date(q.from) <= MAX_RANGE_DAYS * DAY_MS, {
    message: `The range can be at most ${MAX_RANGE_DAYS} days`,
    path: ['to'],
  });

function createInsightsRoutes({ insightsService, requireAuth }) {
  const router = express.Router();

  router.get(
    '/insights/summary',
    requireAuth,
    validate(summaryQuerySchema, 'query'),
    asyncHandler(async (req, res) => {
      res.json({ summary: insightsService.getSummary(req.auth.userId, req.query) });
    })
  );

  return router;
}

module.exports = { createInsightsRoutes, summaryQuerySchema };