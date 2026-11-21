'use strict';

/**
 * Demo data: two users, three accounts, and ~90 days of realistic history.
 *
 *   npm run seed                 add demo data (skips if it already exists)
 *   npm run seed -- --reset      DELETE the database file first, then seed
 *                                (stop the server before using --reset)
 *
 * Logins (password for all: Passw0rd123)
 *   alice@example.com   Everyday Checking (100000000001), High-Yield Savings (100000000002)
 *   bob@example.com     Everyday Checking (200000000001)
 *
 * History is inserted in chronological order through the same double-entry
 * ledger code the API uses, so balances, running balances and reconciliation
 * are all consistent. Data is deterministic: the same on every run.
 */

const fs = require('fs');
const path = require('path');
const bcrypt = require('bcryptjs');

const config = require('./config');
const { createDb, inImmediateTx } = require('./db');
const { postDoubleEntry } = require('./ledger');
const { newId } = require('./utils/ids');
const { formatMinor } = require('./utils/money');

const DEMO_PASSWORD = 'Passw0rd123';
const ALICE_EMAIL = 'alice@example.com';
const BOB_EMAIL = 'bob@example.com';
const DAY_MS = 24 * 60 * 60 * 1000;
const CURRENCY = 'USD';

/** Small seeded PRNG so the generated history is identical on every run. */
function mulberry32(seed) {
  let a = seed >>> 0;
  return function next() {
    a = (a + 0x6d2b79f5) >>> 0;
    let t = a;
    t = Math.imul(t ^ (t >>> 15), t | 1);
    t ^= t + Math.imul(t ^ (t >>> 7), t | 61);
    return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
  };
}

/**
 * Insert demo data into `db`. Exported so tests can reuse it.
 * @param {import('better-sqlite3').Database} db
 * @returns {{ seeded: boolean, accounts?: object[] }}
 */
function seedDatabase(db, { now = Date.now() } = {}) {
  const already = db
    .prepare('SELECT 1 FROM users WHERE email IN (?, ?)')
    .get(ALICE_EMAIL, BOB_EMAIL);
  if (already) return { seeded: false };

  const rng = mulberry32(20261029);
  const randInt = (min, max) => min + Math.floor(rng() * (max - min + 1));
  const passwordHash = bcrypt.hashSync(DEMO_PASSWORD, config.auth.bcryptRounds);
  const isoAt = (daysAgo, hour = 12, minute = 0) => {
    const d = new Date(now - daysAgo * DAY_MS);
    d.setUTCHours(hour, minute, 0, 0);
    return d;
  };

  inImmediateTx(db, () => {
    // ------------------------------------------------------------ users
    const insertUser = db.prepare(
      `INSERT INTO users (id, email, full_name, password_hash, created_at, updated_at)
       VALUES (?, ?, ?, ?, ?, ?)`
    );
    const createdAt = isoAt(95).toISOString();
    const aliceId = newId();
    const bobId = newId();
    insertUser.run(aliceId, ALICE_EMAIL, 'Alice Demo', passwordHash, createdAt, createdAt);
    insertUser.run(bobId, BOB_EMAIL, 'Bob Demo', passwordHash, createdAt, createdAt);

    // ------------------------------------------------------------ accounts
    const accountInfo = new Map(); // id -> { userId, kind }
    const insertAccount = db.prepare(
      `INSERT INTO accounts (id, user_id, kind, type, name, account_number, currency, balance_minor, created_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, 0, ?)`
    );
    const addAccount = ({ userId = null, kind = 'CUSTOMER', type, name, number }) => {
      const id = newId();
      insertAccount.run(id, userId, kind, type, name, number, CURRENCY, createdAt);
      accountInfo.set(id, { userId, kind });
      return id;
    };

    const aliceChecking = addAccount({ userId: aliceId, type: 'CHECKING', name: 'Everyday Checking', number: '100000000001' });
    const aliceSavings = addAccount({ userId: aliceId, type: 'SAVINGS', name: 'High-Yield Savings', number: '100000000002' });
    const bobChecking = addAccount({ userId: bobId, type: 'CHECKING', name: 'Everyday Checking', number: '200000000001' });

    // Merchants and the employer are SYSTEM accounts (allowed to go negative).
    const merchant = (key, name) =>
      addAccount({ kind: 'SYSTEM', type: 'SYSTEM', name, number: `MERCHANT-${key}` });
    const employer = merchant('EMPLOYER', 'Acme Corp Payroll');
    const landlord = merchant('LANDLORD', 'Maple Street Rentals');
    const grocery = merchant('GROCERY', 'Fresh Market');
    const transit = merchant('TRANSIT', 'Metro Transit');
    const streaming = merchant('STREAMING', 'StreamFlix');
    const power = merchant('POWER', 'City Power & Light');
    const pharmacy = merchant('PHARMACY', 'CarePlus Pharmacy');
    const bookshop = merchant('BOOKSHOP', 'Corner Bookshop');
    const bistro = merchant('BISTRO', 'Luna Bistro');
    const cafe = merchant('CAFE', 'Bean There Cafe');
    const cinema = merchant('CINEMA', 'Starlight Cinema');

    // ------------------------------------------------------------ events
    const events = [];
    const add = (at, from, to, amountMinor, category, description) =>
      events.push({ at, from, to, amountMinor, category, description });

    // Monthly rhythm, three months: payroll, rent, utilities, subscription, savings.
    for (const base of [85, 55, 25]) {
      add(isoAt(base, 9), employer, aliceChecking, 320000, 'INCOME', 'Salary');
      add(isoAt(base - 2, 10), aliceChecking, landlord, 110000, 'BILLS', 'Rent');
      add(isoAt(base - 5, 11), aliceChecking, power, randInt(6000, 9500), 'BILLS', 'Electricity');
      add(isoAt(base - 7, 8), aliceChecking, streaming, 1549, 'ENTERTAINMENT', 'Streaming subscription');
      add(isoAt(base - 1, 9, 30), aliceChecking, aliceSavings, 30000, 'TRANSFER', 'Monthly savings');

      add(isoAt(base + 1, 9), employer, bobChecking, 250000, 'INCOME', 'Salary');
      add(isoAt(base - 2, 10), bobChecking, landlord, 90000, 'BILLS', 'Rent');
    }

    // Weekly groceries.
    for (let d = 82; d >= 3; d -= 7) {
      add(isoAt(d, 17, randInt(0, 59)), aliceChecking, grocery, randInt(3500, 12000), 'FOOD', 'Weekly groceries');
      add(isoAt(d - 1, 18, randInt(0, 59)), bobChecking, grocery, randInt(2500, 9000), 'FOOD', 'Groceries');
    }

    // Commuting, coffee and meals on random days.
    for (let d = 88; d >= 1; d--) {
      if (rng() < 0.35) add(isoAt(d, 8, randInt(0, 59)), aliceChecking, transit, randInt(275, 2500), 'TRANSPORT', 'Metro fare');
      if (rng() < 0.2) add(isoAt(d, 9, randInt(0, 59)), aliceChecking, cafe, randInt(450, 900), 'FOOD', 'Coffee');
      if (rng() < 0.1) add(isoAt(d, 20, randInt(0, 59)), aliceChecking, bistro, randInt(1800, 6500), 'FOOD', 'Dinner');
    }

    // Occasional one-offs.
    for (const d of [70, 41, 9]) add(isoAt(d, 14), aliceChecking, pharmacy, randInt(800, 3200), 'HEALTH', 'Pharmacy');
    for (const d of [64, 33, 15]) add(isoAt(d, 15), aliceChecking, bookshop, randInt(1500, 4500), 'SHOPPING', 'Books');
    for (const d of [52, 19]) add(isoAt(d, 19), aliceChecking, cinema, randInt(1800, 3600), 'ENTERTAINMENT', 'Movie night');

    // Person-to-person transfers between the two demo users.
    add(isoAt(40, 21), aliceChecking, bobChecking, 4500, 'FOOD', 'Dinner split');
    add(isoAt(30, 13), bobChecking, aliceChecking, 1500, 'FOOD', 'Lunch, paid back');
    add(isoAt(12, 20), aliceChecking, bobChecking, 2000, 'ENTERTAINMENT', 'Concert tickets');
    add(isoAt(5, 16), bobChecking, aliceChecking, 6000, 'FOOD', 'Shared groceries');

    events.sort((a, b) => a.at - b.at);

    // ------------------------------------------------------------ post them
    const insertTransfer = db.prepare(
      `INSERT INTO transfers
         (id, idempotency_key, initiated_by_user_id, kind, source_account_id,
          destination_account_id, amount_minor, currency, status, category,
          description, created_at, completed_at)
       VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'COMPLETED', ?, ?, ?, ?)`
    );
    const readBalance = db.prepare('SELECT balance_minor FROM accounts WHERE id = ?');

    events.forEach((event, index) => {
      const source = accountInfo.get(event.from);
      // Never overdraw a customer account while generating history.
      if (source.kind === 'CUSTOMER' && readBalance.get(event.from).balance_minor < event.amountMinor) {
        return;
      }

      const at = event.at.toISOString();
      const transferId = newId();
      insertTransfer.run(
        transferId,
        `seed-${index}`,
        source.kind === 'CUSTOMER' ? source.userId : null,
        source.kind === 'SYSTEM' ? 'DEPOSIT' : 'TRANSFER',
        event.from,
        event.to,
        event.amountMinor,
        CURRENCY,
        event.category,
        event.description,
        at,
        at
      );
      postDoubleEntry(db, {
        transferId,
        sourceAccountId: event.from,
        destinationAccountId: event.to,
        amountMinor: event.amountMinor,
        now: at,
      });
    });

    // ------------------------------------------------------------ self-check
    const broken = db.prepare('SELECT * FROM v_reconciliation_errors').all();
    if (broken.length > 0) throw new Error('Seed produced unreconciled accounts');
    const total = db.prepare('SELECT SUM(balance_minor) AS total FROM accounts').get().total;
    if (total !== 0) throw new Error(`Ledger does not balance (sum = ${total})`);
  });

  const accounts = db
    .prepare(
      `SELECT a.name, a.account_number, a.balance_minor, u.email
         FROM accounts a JOIN users u ON u.id = a.user_id
        WHERE a.kind = 'CUSTOMER' ORDER BY u.email, a.account_number`
    )
    .all();
  return { seeded: true, accounts };
}

// ---------------------------------------------------------------- CLI

if (require.main === module) {
  const dbPath = config.dbPath || path.join(__dirname, '..', 'data', 'bank.db');
    if (!config.server.allowSeed) {
      console.error(
        'Refusing to run the seed script: it creates demo users with a known password and --reset deletes ' +
          'the database. Set ALLOW_SEED=true only if you are sure this is not a real database.'
      );
      process.exit(1);
    }

  if (process.argv.includes('--reset')) {
    for (const suffix of ['', '-wal', '-shm']) {
      fs.rmSync(dbPath + suffix, { force: true });
    }
    console.log(`Deleted ${dbPath}`);
  }

  const db = createDb(config.dbPath);
  const result = seedDatabase(db);
  db.close();

  if (!result.seeded) {
    console.log('Demo data already exists. Run "npm run seed -- --reset" to start over.');
  } else {
    console.log('Seeded demo data. Password for all users: ' + DEMO_PASSWORD);
    for (const a of result.accounts) {
      console.log(`  ${a.email}  ${a.name} (${a.account_number})  ${formatMinor(a.balance_minor)}`);
    }
  }
}

module.exports = { seedDatabase, DEMO_PASSWORD, ALICE_EMAIL, BOB_EMAIL };