
CREATE TABLE IF NOT EXISTS users (
    id                          TEXT PRIMARY KEY,                -- UUID
    email                       TEXT NOT NULL UNIQUE COLLATE NOCASE,
    full_name                   TEXT NOT NULL CHECK (length(trim(full_name)) > 0),
    password_hash               TEXT NOT NULL,                   -- bcrypt/argon2, never plaintext
    daily_transfer_limit_minor  INTEGER NOT NULL DEFAULT 500000  -- $5,000.00
                                CHECK (daily_transfer_limit_minor >= 0),
    theme_preference            TEXT NOT NULL DEFAULT 'SYSTEM'
                                CHECK (theme_preference IN ('SYSTEM', 'LIGHT', 'DARK')),
    created_at                  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    updated_at                  TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now'))
);

CREATE TABLE IF NOT EXISTS sessions (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    refresh_token_hash  TEXT NOT NULL UNIQUE,
    device_name         TEXT,
    created_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    expires_at          TEXT NOT NULL,
    revoked_at          TEXT
);

CREATE INDEX IF NOT EXISTS idx_sessions_user ON sessions(user_id);



CREATE TABLE IF NOT EXISTS accounts (
    id              TEXT PRIMARY KEY,
    user_id         TEXT REFERENCES users(id) ON DELETE RESTRICT,
    kind            TEXT NOT NULL DEFAULT 'CUSTOMER'
                    CHECK (kind IN ('CUSTOMER', 'SYSTEM')),
    type            TEXT NOT NULL DEFAULT 'CHECKING'
                    CHECK (type IN ('CHECKING', 'SAVINGS', 'SYSTEM')),
    name            TEXT NOT NULL,
    account_number  TEXT NOT NULL UNIQUE,
    currency        TEXT NOT NULL DEFAULT 'USD' CHECK (length(currency) = 3),
    balance_minor   INTEGER NOT NULL DEFAULT 0,
    status          TEXT NOT NULL DEFAULT 'ACTIVE'
                    CHECK (status IN ('ACTIVE', 'FROZEN', 'CLOSED')),
    created_at      TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),


    CHECK ((kind = 'CUSTOMER' AND user_id IS NOT NULL) OR
           (kind = 'SYSTEM'   AND user_id IS NULL)),

    CHECK (kind = 'SYSTEM' OR balance_minor >= 0)
);

CREATE INDEX IF NOT EXISTS idx_accounts_user ON accounts(user_id);


CREATE TABLE IF NOT EXISTS transfers (
    id                      TEXT PRIMARY KEY,
    idempotency_key         TEXT NOT NULL,
    initiated_by_user_id    TEXT REFERENCES users(id),
    kind                    TEXT NOT NULL DEFAULT 'TRANSFER'
                            CHECK (kind IN ('TRANSFER', 'DEPOSIT')),
    source_account_id       TEXT NOT NULL REFERENCES accounts(id),
    destination_account_id  TEXT NOT NULL REFERENCES accounts(id),
    amount_minor            INTEGER NOT NULL CHECK (amount_minor > 0),
    currency                TEXT NOT NULL CHECK (length(currency) = 3),
    status                  TEXT NOT NULL DEFAULT 'PENDING'
                            CHECK (status IN ('PENDING', 'COMPLETED', 'FAILED', 'REVERSED')),
    failure_reason          TEXT,
    category                TEXT NOT NULL DEFAULT 'OTHER'
                            CHECK (category IN ('FOOD', 'TRANSPORT', 'SHOPPING', 'BILLS',
                                                'ENTERTAINMENT', 'HEALTH', 'INCOME',
                                                'TRANSFER', 'OTHER')),
    description             TEXT,
    created_at              TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),
    completed_at            TEXT,

    CHECK (source_account_id <> destination_account_id),

    UNIQUE (initiated_by_user_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_transfers_source      ON transfers(source_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transfers_destination ON transfers(destination_account_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_transfers_user_day    ON transfers(initiated_by_user_id, created_at);


CREATE TABLE IF NOT EXISTS ledger_entries (
    seq                   INTEGER PRIMARY KEY AUTOINCREMENT,
    id                    TEXT NOT NULL UNIQUE,
    transfer_id           TEXT NOT NULL REFERENCES transfers(id),
    account_id            TEXT NOT NULL REFERENCES accounts(id),
    direction             TEXT NOT NULL CHECK (direction IN ('DEBIT', 'CREDIT')),
    amount_minor          INTEGER NOT NULL CHECK (amount_minor > 0),
    balance_after_minor   INTEGER NOT NULL,
    created_at            TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

    UNIQUE (transfer_id, direction)
);


CREATE INDEX IF NOT EXISTS idx_ledger_account_seq ON ledger_entries(account_id, seq DESC);


CREATE TRIGGER IF NOT EXISTS trg_ledger_no_update
BEFORE UPDATE ON ledger_entries
BEGIN
    SELECT RAISE(ABORT, 'ledger_entries are immutable');
END;

CREATE TRIGGER IF NOT EXISTS trg_ledger_no_delete
BEFORE DELETE ON ledger_entries
BEGIN
    SELECT RAISE(ABORT, 'ledger_entries are immutable');
END;


CREATE TRIGGER IF NOT EXISTS trg_transfers_frozen_core
BEFORE UPDATE OF amount_minor, source_account_id, destination_account_id, currency, idempotency_key
ON transfers
BEGIN
    SELECT RAISE(ABORT, 'transfer core fields are immutable');
END;


CREATE TABLE IF NOT EXISTS beneficiaries (
    id                  TEXT PRIMARY KEY,
    user_id             TEXT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nickname            TEXT NOT NULL,
    account_number      TEXT NOT NULL REFERENCES accounts(account_number),
    created_at          TEXT NOT NULL DEFAULT (strftime('%Y-%m-%dT%H:%M:%fZ', 'now')),

    UNIQUE (user_id, account_number)
);

CREATE INDEX IF NOT EXISTS idx_beneficiaries_user ON beneficiaries(user_id);


CREATE VIEW IF NOT EXISTS v_account_reconciliation AS
SELECT a.id                                   AS account_id,
       a.balance_minor                        AS stored_balance_minor,
       COALESCE(SUM(CASE l.direction WHEN 'CREDIT' THEN l.amount_minor
                                     ELSE -l.amount_minor END), 0) AS ledger_balance_minor
FROM accounts a
LEFT JOIN ledger_entries l ON l.account_id = a.id
GROUP BY a.id;

CREATE VIEW IF NOT EXISTS v_reconciliation_errors AS
SELECT * FROM v_account_reconciliation
WHERE stored_balance_minor <> ledger_balance_minor;


PRAGMA user_version = 1;
