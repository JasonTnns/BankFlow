'use strict';

const { createDb } = require('../src/db');

describe('query plans (schema guard)', () => {
  let db;

  beforeAll(() => {
    db = createDb(':memory:');
  });
  afterAll(() => db.close());

  const plan = (sql, ...params) =>
    db
      .prepare(`EXPLAIN QUERY PLAN ${sql}`)
      .all(...params)
      .map((row) => row.detail)
      .join('\n');

  test('account history pages through its index, with no sort step', () => {
    const details = plan(
      'SELECT * FROM ledger_entries l WHERE l.account_id = ? AND l.seq < ? ORDER BY l.seq DESC LIMIT 21',
      'account',
      100
    );

    expect(details).toMatch(/idx_ledger_account_seq/);
    expect(details).not.toMatch(/TEMP B-TREE/);
  });

  test('the daily-limit total is found through an index', () => {
    const details = plan(
      `SELECT COALESCE(SUM(amount_minor), 0) FROM transfers
        WHERE initiated_by_user_id = ? AND kind = 'TRANSFER' AND status = 'COMPLETED' AND created_at >= ?`,
      'user',
      '2026-01-01T00:00:00.000Z'
    );

    expect(details).toMatch(/SEARCH .* USING (COVERING )?INDEX/);
    expect(details).not.toMatch(/SCAN transfers/);
  });

  test('the idempotency lookup is an index lookup', () => {
    const details = plan(
      'SELECT * FROM transfers WHERE initiated_by_user_id = ? AND idempotency_key = ?',
      'user',
      'key'
    );

    expect(details).toMatch(/SEARCH .* USING (COVERING )?INDEX/);
    expect(details).not.toMatch(/SCAN transfers/);
  });

  test("an account's recent transfers are found through an index", () => {
    const details = plan(
      'SELECT * FROM transfers WHERE source_account_id = ? ORDER BY created_at DESC LIMIT 20',
      'account'
    );

    expect(details).toMatch(/idx_transfers_source/);
  });
});