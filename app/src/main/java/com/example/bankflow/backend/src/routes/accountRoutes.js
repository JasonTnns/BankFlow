'use strict';


const express = require('express');
const rateLimit = require('express-rate-limit');
const { z } = require('zod');

const config = require('../config');
const { CATEGORIES } = require('../constants');
const { Errors, IssueCode } = require('../errors');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');


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
      params: { code: IssueCode.RANGE_INVALID },
    })
    .refine(
      (q) =>
        q.minAmountMinor === undefined ||
        q.maxAmountMinor === undefined ||
        q.minAmountMinor <= q.maxAmountMinor,
      {
        message: 'minAmountMinor must not exceed maxAmountMinor',
        path: ['minAmountMinor'],
        params: { code: IssueCode.AMOUNT_RANGE_INVALID },
      }
    );

const recipientLookupSchema = z.object({
  accountNumber: z
    .string({ required_error: 'Account number is required' })
    .trim()
    .regex(/^\d{12}$/, 'Account number must be 12 digits'),
});


function createAccountRoutes({ accountService, requireAuth }) {
  const router = express.Router();

  const lookupLimiter = rateLimit({
    windowMs: 60 * 1000,
    limit: config.rateLimit.lookupMaxPerMinute,
    standardHeaders: 'draft-7',
    legacyHeaders: false,
    skip: () => config.isTest,
    keyGenerator: (req) => req.auth.userId,
    handler: (_req, _res, next) => next(Errors.rateLimited()),
  });



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

  router.get(
    '/accounts/:accountId/transactions/:transferId',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({
        transaction: accountService.getTransaction(
          req.auth.userId,
          req.params.accountId,
          req.params.transferId
        ),
      });
    })
  );

  router.get(
    '/recipients/lookup',
    requireAuth,
    lookupLimiter,
    validate(recipientLookupSchema, 'query'),
    asyncHandler(async (req, res) => {
      res.json({
        recipient: accountService.lookupRecipient(req.auth.userId, req.query.accountNumber),
      });
    })
  );

  return router;
}

module.exports = { createAccountRoutes, transactionQuerySchema, recipientLookupSchema };