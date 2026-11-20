'use strict';

/**
 * Transfer endpoints (all require a valid access token)
 *
 *   POST /transfers
 *     Header:  Idempotency-Key: <uuid>   (required)
 *     Body:    { sourceAccountId, destinationAccountId | destinationAccountNumber,
 *                amountMinor, category?, description? }
 *     201 -> new transfer completed
 *     200 -> same key seen before; original result returned (header Idempotent-Replayed: true)
 *     Errors: 400 VALIDATION_ERROR, 404 NOT_FOUND, 409 IDEMPOTENCY_CONFLICT,
 *             422 INSUFFICIENT_FUNDS | LIMIT_EXCEEDED | SAME_ACCOUNT |
 *                 ACCOUNT_INACTIVE | CURRENCY_MISMATCH
 *
 *   GET /transfers/:transferId  -> { transfer }   (caller must be a party to it)
 *
 * The caller's identity always comes from the access token. Nothing in the body
 * can name a different user.
 */

const express = require('express');
const { z } = require('zod');

const { CATEGORIES } = require('../constants');
const { Errors, IssueCode, validationDetails } = require('../errors');
const { MAX_TRANSFER_MINOR } = require('../utils/money');
const { validate } = require('../middleware/validate');
const { asyncHandler } = require('../middleware/errorHandler');

// ------------------------------------------------------------------ schemas

const idempotencyKeySchema = z.string().uuid('Idempotency-Key must be a UUID');

const transferSchema = z
  .object({
    sourceAccountId: z.string({ required_error: 'Source account is required' }).uuid('Invalid account id'),
    destinationAccountId: z.string().uuid('Invalid account id').optional(),
    destinationAccountNumber: z
      .string()
      .trim()
      .regex(/^\d{12}$/, 'Account number must be 12 digits')
      .optional(),
    amountMinor: z
      .number({
        required_error: 'Amount is required',
        invalid_type_error: 'Amount must be a number of cents',
      })
      .int('Amount must be a whole number of cents')
      .positive('Amount must be greater than zero')
      .max(MAX_TRANSFER_MINOR, 'Amount exceeds the maximum per transfer'),
    category: z.enum(CATEGORIES).optional(),
    description: z
      .string()
      .trim()
      .max(140, 'Description is too long')
      .optional()
      .transform((value) => value || undefined),
  })
    .refine((body) => Boolean(body.destinationAccountId) !== Boolean(body.destinationAccountNumber), {
      message: 'Provide exactly one of destinationAccountId or destinationAccountNumber',
      path: ['destinationAccountId'],
      params: { code: IssueCode.DESTINATION_CONFLICT },
    });

// ------------------------------------------------------------------ middleware

/** Reads and validates the Idempotency-Key header into req.idempotencyKey. */
function requireIdempotencyKey(req, _res, next) {
  const parsed = idempotencyKeySchema.safeParse(req.get('Idempotency-Key'));
    if (!parsed.success) {
      const message = parsed.error.issues[0].message;
      return next(
        Errors.validation(
          'A valid Idempotency-Key header is required',
          validationDetails([{ field: 'Idempotency-Key', code: IssueCode.INVALID_UUID, message }])
        )
      );
    }
  req.idempotencyKey = parsed.data;
  next();
}

// ------------------------------------------------------------------ router

/**
 * @param {object} deps
 * @param {ReturnType<import('../services/transferService').createTransferService>} deps.transferService
 * @param {import('express').RequestHandler} deps.requireAuth
 */
function createTransferRoutes({ transferService, requireAuth }) {
  const router = express.Router();

  router.post(
    '/transfers',
    requireAuth,
    requireIdempotencyKey,
    validate(transferSchema),
    asyncHandler(async (req, res) => {
      const result = transferService.createTransfer(req.auth.userId, {
        ...req.body,
        idempotencyKey: req.idempotencyKey,
      });

      if (result.replayed) res.set('Idempotent-Replayed', 'true');
      res.status(result.replayed ? 200 : 201).json({
        transfer: result.transfer,
        sourceAccountBalanceMinor: result.sourceAccountBalanceMinor,
      });
    })
  );

  router.get(
    '/transfers/:transferId',
    requireAuth,
    asyncHandler(async (req, res) => {
      res.json({ transfer: transferService.getTransfer(req.auth.userId, req.params.transferId) });
    })
  );

  return router;
}

module.exports = { createTransferRoutes, transferSchema };