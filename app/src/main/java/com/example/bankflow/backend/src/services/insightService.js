'use strict';

const MAX_RANGE_DAYS = 370;

function createInsightsService(db) {
  const aggregate = db.prepare(
    `SELECT l.direction AS direction,
            t.category AS category,
            SUM(l.amount_minor) AS total,
            COUNT(*) AS entry_count
       FROM ledger_entries l
       JOIN accounts mine ON mine.id = l.account_id
       JOIN transfers t ON t.id = l.transfer_id
       JOIN ledger_entries o ON o.transfer_id = l.transfer_id AND o.direction <> l.direction
       JOIN accounts cp ON cp.id = o.account_id
      WHERE mine.user_id = ?
        AND mine.currency = ?
        AND l.created_at >= ?
        AND l.created_at < ?
        AND (cp.user_id IS NULL OR cp.user_id <> mine.user_id)
      GROUP BY l.direction, t.category`
  );

  function getSummary(userId, { from, to, currency }) {
    const rows = aggregate.all(userId, currency, from, to);

    let incomeMinor = 0;
    let spentMinor = 0;
    let transactionCount = 0;
    const byCategory = [];

    for (const row of rows) {
      transactionCount += row.entry_count;
      if (row.direction === 'CREDIT') {
        incomeMinor += row.total;
      } else {
        spentMinor += row.total;
        byCategory.push({ category: row.category, spentMinor: row.total, count: row.entry_count });
      }
    }

    byCategory.sort((a, b) => b.spentMinor - a.spentMinor || a.category.localeCompare(b.category));

    return {
      from,
      to,
      currency,
      incomeMinor,
      spentMinor,
      netMinor: incomeMinor - spentMinor,
      transactionCount,
      byCategory,
    };
  }

  return { getSummary };
}

module.exports = { createInsightsService, MAX_RANGE_DAYS };