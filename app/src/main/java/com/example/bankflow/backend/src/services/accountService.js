'use strict';

const config = require('../config');
const { Errors } = require('../errors');


function encodeCursor(seq) {
  return Buffer.from(String(seq), 'utf8').toString('base64url');
}

function decodeCursor(cursor) {
  const seq = Number(Buffer.from(cursor, 'base64url').toString('utf8'));
  if (!Number.isSafeInteger(seq) || seq <= 0) {
    throw Errors.validation('Invalid cursor', { fields: { cursor: ['Invalid cursor'] } });
  }
  return seq;
}


function toAccountDto(row) {
  return {
    id: row.id,
    name: row.name,
    type: row.type,
    accountNumber: row.account_number,
    currency: row.currency,
    balanceMinor: row.balance_minor,
    status: row.status,
    createdAt: row.created_at,
  };
}

function maskAccountNumber(accountNumber) {
  return `••••${accountNumber.slice(-4)}`;
}

function shortName(fullName) {
  const parts = fullName.trim().split(/\s+/);
  if (parts.length < 2) return parts[0];
  const [initial] = [...parts[parts.length - 1]];
  return `${parts[0]} ${initial.toUpperCase()}.`;
}

function toTransactionDto(row) {
  const isSystem = row.counterparty_kind === 'SYSTEM';
  return {
    transferId: row.transfer_id,
    accountId: row.account_id,
    direction: row.direction === 'DEBIT' ? 'OUT' : 'IN',
    amountMinor: row.amount_minor,
    balanceAfterMinor: row.balance_after_minor,
    currency: row.currency,
    status: row.status,
    kind: row.kind,
    category: row.category,
    description: row.description,
    createdAt: row.created_at,
    counterparty: {
      name: row.counterparty_user_name ?? row.counterparty_account_name,
      accountNumberMasked: isSystem ? null : maskAccountNumber(row.counterparty_account_number),
      isOwnAccount: row.counterparty_user_id !== null && row.counterparty_user_id === row.owner_user_id,
    },
  };
}

function escapeLike(text) {
  return text.replace(/[\\%_]/g, (ch) => `\\${ch}`);
}


function createAccountService(db) {

  function queryHistory(scope, filters = {}) {
    const limit = Math.min(
      filters.limit ?? config.banking.defaultPageSize,
      config.banking.maxPageSize
    );

    const where = [];
    const params = [];

    if (scope.accountId) {
      where.push('l.account_id = ?');
      params.push(scope.accountId);
    } else {
      where.push('l.account_id IN (SELECT id FROM accounts WHERE user_id = ?)');
      params.push(scope.userId);
    }

    if (filters.transferId) {
      where.push('l.transfer_id = ?');
      params.push(filters.transferId);
    }
    if (filters.cursor) {
      where.push('l.seq < ?');
      params.push(decodeCursor(filters.cursor));
    }
    if (filters.direction) {
      where.push('l.direction = ?');
      params.push(filters.direction === 'OUT' ? 'DEBIT' : 'CREDIT');
    }
    if (filters.category) {
      where.push('t.category = ?');
      params.push(filters.category);
    }
    if (filters.from) {
      where.push('l.created_at >= ?');
      params.push(filters.from);
    }
    if (filters.to) {
      where.push('l.created_at <= ?');
      params.push(filters.to);
    }
    if (filters.minAmountMinor !== undefined) {
      where.push('l.amount_minor >= ?');
      params.push(filters.minAmountMinor);
    }
    if (filters.maxAmountMinor !== undefined) {
      where.push('l.amount_minor <= ?');
      params.push(filters.maxAmountMinor);
    }
    if (filters.search) {
      const pattern = `%${escapeLike(filters.search)}%`;
      where.push(
        `(t.description LIKE ? ESCAPE '\\'
          OR cu.full_name LIKE ? ESCAPE '\\'
          OR cp.name LIKE ? ESCAPE '\\')`
      );
      params.push(pattern, pattern, pattern);
    }

    params.push(limit + 1);

    const rows = db
      .prepare(
        `SELECT l.seq, l.account_id, l.direction, l.amount_minor, l.balance_after_minor,
                l.created_at,
                t.id AS transfer_id, t.kind, t.status, t.category, t.description, t.currency,
                cp.name AS counterparty_account_name,
                cp.account_number AS counterparty_account_number,
                cp.kind AS counterparty_kind,
                cp.user_id AS counterparty_user_id,
                cu.full_name AS counterparty_user_name,
                mine.user_id AS owner_user_id
           FROM ledger_entries l
           JOIN accounts mine ON mine.id = l.account_id
           JOIN transfers t ON t.id = l.transfer_id
           JOIN ledger_entries o ON o.transfer_id = l.transfer_id AND o.direction <> l.direction
           JOIN accounts cp ON cp.id = o.account_id
           LEFT JOIN users cu ON cu.id = cp.user_id
          WHERE ${where.join(' AND ')}
          ORDER BY l.seq DESC
          LIMIT ?`
      )
      .all(...params);

    const hasMore = rows.length > limit;
    const page = hasMore ? rows.slice(0, limit) : rows;

    return {
      items: page.map(toTransactionDto),
      nextCursor: hasMore ? encodeCursor(page[page.length - 1].seq) : null,
    };
  }

  function requireOwnedAccount(userId, accountId) {
    const row = db
      .prepare(`SELECT * FROM accounts WHERE id = ? AND user_id = ? AND kind = 'CUSTOMER'`)
      .get(accountId, userId);
    if (!row) throw Errors.notFound('Account not found');
    return row;
  }

  function listAccounts(userId) {
    return db
      .prepare(
        `SELECT * FROM accounts
          WHERE user_id = ? AND kind = 'CUSTOMER'
          ORDER BY created_at ASC, id ASC`
      )
      .all(userId)
      .map(toAccountDto);
  }

  function getAccount(userId, accountId) {
    return toAccountDto(requireOwnedAccount(userId, accountId));
  }


  function listTransactions(userId, accountId, filters = {}) {
    requireOwnedAccount(userId, accountId);
    return queryHistory({ userId, accountId }, filters);
  }

  function getTransaction(userId, accountId, transferId) {
    requireOwnedAccount(userId, accountId);
    const { items } = queryHistory({ userId, accountId }, { transferId, limit: 1 });
    if (items.length === 0) throw Errors.notFound('Transaction not found');
    return items[0];
  }

  function lookupRecipient(userId, accountNumber) {
    const row = db
      .prepare(
        `SELECT a.account_number, a.currency, a.status, a.user_id, u.full_name
           FROM accounts a JOIN users u ON u.id = a.user_id
          WHERE a.account_number = ? AND a.kind = 'CUSTOMER'`
      )
      .get(accountNumber);
    if (!row) throw Errors.notFound('No account found with that number');

    return {
      displayName: shortName(row.full_name),
      accountNumberMasked: maskAccountNumber(row.account_number),
      currency: row.currency,
      status: row.status,
      isOwnAccount: row.user_id === userId,
    };
  }

  function getDashboard(userId, { recentLimit = 5 } = {}) {
    const accounts = listAccounts(userId);

    const totals = new Map();
    for (const account of accounts) {
      totals.set(account.currency, (totals.get(account.currency) ?? 0) + account.balanceMinor);
    }

    return {
      accounts,
      totals: [...totals].map(([currency, balanceMinor]) => ({ currency, balanceMinor })),
      recentTransactions: queryHistory({ userId }, { limit: recentLimit }).items,
    };
  }

  return {
    listAccounts,
    getAccount,
    listTransactions,
    getTransaction,
    lookupRecipient,
    getDashboard,
  };
}

module.exports = { createAccountService, shortName, maskAccountNumber };