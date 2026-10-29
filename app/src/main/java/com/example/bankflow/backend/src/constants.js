'use strict';

/** Must match the CHECK constraint on transfers.category in schema.sql. */
const CATEGORIES = [
  'FOOD',
  'TRANSPORT',
  'SHOPPING',
  'BILLS',
  'ENTERTAINMENT',
  'HEALTH',
  'INCOME',
  'TRANSFER',
  'OTHER',
];

module.exports = { CATEGORIES };