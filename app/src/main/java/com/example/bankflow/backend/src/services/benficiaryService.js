'use strict';


const { inImmediateTx } = require('../db');
const { Errors } = require('../errors');
const { newId } = require('../utils/ids');
const { shortName, maskAccountNumber } = require('./accountService');

const MAX_BENEFICIARIES = 50;

const SELECT_COLUMNS = `
  SELECT b.id, b.nickname, b.account_number, b.created_at,
         a.currency, a.status, u.full_name
    FROM beneficiaries b
    JOIN accounts a ON a.account_number = b.account_number
    JOIN users u ON u.id = a.user_id`;

function toDto(row) {
  return {
    id: row.id,
    nickname: row.nickname,
    accountNumber: row.account_number,
    accountNumberMasked: maskAccountNumber(row.account_number),
    displayName: shortName(row.full_name),
    currency: row.currency,
    status: row.status,
    createdAt: row.created_at,
  };
}

function createBeneficiaryService(db) {
  function list(userId) {
    return db
      .prepare(
        `${SELECT_COLUMNS}
          WHERE b.user_id = ?
          ORDER BY b.nickname COLLATE NOCASE ASC, b.created_at ASC`
      )
      .all(userId)
      .map(toDto);
  }

  function create(userId, { nickname, accountNumber }) {
    return inImmediateTx(db, () => {
      const target = db
        .prepare(`SELECT user_id FROM accounts WHERE account_number = ? AND kind = 'CUSTOMER'`)
        .get(accountNumber);
      if (!target) throw Errors.notFound('No account found with that number');

      if (target.user_id === userId) {
        throw Errors.validation('That is one of your own accounts', {
          fields: { accountNumber: ['That is one of your own accounts'] },
        });
      }

      const { count } = db
        .prepare('SELECT COUNT(*) AS count FROM beneficiaries WHERE user_id = ?')
        .get(userId);
      if (count >= MAX_BENEFICIARIES) {
        throw Errors.validation(`You can save up to ${MAX_BENEFICIARIES} recipients`);
      }

      const existing = db
        .prepare('SELECT 1 FROM beneficiaries WHERE user_id = ? AND account_number = ?')
        .get(userId, accountNumber);
      if (existing) throw Errors.beneficiaryExists();

      const id = newId();
      db.prepare(
        'INSERT INTO beneficiaries (id, user_id, nickname, account_number) VALUES (?, ?, ?, ?)'
      ).run(id, userId, nickname, accountNumber);

      return toDto(db.prepare(`${SELECT_COLUMNS} WHERE b.id = ?`).get(id));
    });
  }

  function remove(userId, beneficiaryId) {
    const result = db
      .prepare('DELETE FROM beneficiaries WHERE id = ? AND user_id = ?')
      .run(beneficiaryId, userId);
    if (result.changes === 0) throw Errors.notFound('Recipient not found');
  }

  return { list, create, remove };
}

module.exports = { createBeneficiaryService, MAX_BENEFICIARIES };