'use strict';

/**
 * Request validation with zod.
 *
 *   router.post('/transfers', validate(transferSchema), handler)
 *
 * On success, req.body (or req.query / req.params) is REPLACED with the parsed
 * value, so handlers only ever see validated, correctly typed data (and zod
 * strips fields we didn't declare).
 *
 * On failure responds 400 VALIDATION_ERROR with per-field messages:
 *   { error: { code, message, details: { fields: { amountMinor: ["..."] } } } }
 */

const { Errors } = require('../errors');

/**
 * @param {import('zod').ZodTypeAny} schema
 * @param {'body' | 'query' | 'params'} [source]
 */
function validate(schema, source = 'body') {
  return function validateRequest(req, _res, next) {
    const result = schema.safeParse(req[source]);

    if (!result.success) {
      const { fieldErrors, formErrors } = result.error.flatten();
      return next(
        Errors.validation('Some fields are invalid', {
          fields: fieldErrors,
          ...(formErrors.length > 0 && { form: formErrors }),
        })
      );
    }

    // In Express 4, req.query is a plain writable property, so this is safe.
    req[source] = result.data;
    next();
  };
}

module.exports = { validate };