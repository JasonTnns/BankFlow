'use strict';

/**
 * Read-only banking endpoints (all require a valid access token)
 *
 *   GET /dashboard                         -> { accounts, totals, recentTransactions }
 *   GET /accounts                          -> { accounts }
 *   GET /accounts/:accountId               -> { account }
 *   GET /accounts/:accountId/transactions  -> { items, nextCursor }
 *
 * Transaction filters (all optional, combinable):
 *   limit (1-50, default 20), cursor, direction=IN|OUT, category, search,
 *   from, to (ISO-8601 dates/times), minAmountMinor, maxAmountMinor
 */

const express = require('express');
const { z } = require('zod');

const config = require('../config');
const { CATEGORIES } = require('../constants');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');

// ------------------------------------------------------------------ schemas

/** Accept any ISO-8601 instant, normalise to UTC "...Z" so it compares correctly with stored text. */
const isoInstant = z
  .string()
  .datetime({ offset: true, message: 'Must be an ISO-8601 date-time' })
  .transform((value) => new Date(value).toISOString());

const minorAmount = z.coerce.number().int().min(0).max(Number.MAX_SAFE_INTEGER);

const transactionQuerySchema = z
  .object({
    limit: z.coerce.number().int().min(1).max(config.banking.maxPageSize).optional(),
    cursor: z.string().min(1).max(100).optional(),
    direction: z.enum(['IN', 'OUT']).optional(),
    category: z.enum(CATEGORIES).optional(),
    search: z
      .string()
      .trim()
      .max(100)
      .optional()
      .transform((value) => value || undefined),
    from: isoInstant.optional(),
    to: isoInstant.optional(),
    minAmountMinor: minorAmount.optional(),
    maxAmountMinor: minorAmount.optional(),
  })
  .refine((q) => !q.from || !q.to || q.from <= q.to, {
    message: '"from" must not be after "to"',
    path: ['from'],
  })
  .refine(
    (q) =>
      q.minAmountMinor === undefined ||
      q.maxAmountMinor === undefined ||
      q.minAmountMinor <= q.maxAmountMinor,
    { message: 'minAmountMinor must not exceed maxAmountMinor', path: ['minAmountMinor'] }
  );

// ------------------------------------------------------------------ router

/**
 * @param {object} deps
 * @param {ReturnType<import('../services/accountService').createAccountService>} deps.accountService
 * @param {import('express').RequestHandler} deps.requireAuth
 */
function createAccountRoutes({ accountService, requireAuth }) {
  const router = express.Router();

  // requireAuth is attached per route (not router.use) so this router, which is
  // mounted at the API root, can never accidentally protect or shadow other paths.

  router.get(
    '/dashboard',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json(accountService.getDashboard(req.auth.userId));
    })
  );

  router.get(
    '/accounts',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({ accounts: accountService.listAccounts(req.auth.userId) });
    })
  );

  router.get(
    '/accounts/:accountId',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({ account: accountService.getAccount(req.auth.userId, req.params.accountId) });
    })
  );

  router.get(
    '/accounts/:accountId/transactions',
    requireAuth,
    validate(transactionQuerySchema, 'query'),
    asyncHandler(async (req, res) => {
      res.json(accountService.listTransactions(req.auth.userId, req.params.accountId, req.query));
    })
  );

  return router;
}

module.exports = { createAccountRoutes, transactionQuerySchema };