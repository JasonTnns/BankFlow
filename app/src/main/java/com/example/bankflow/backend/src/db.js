'use strict';



const fs = require('fs');
const path = require('path');
const Database = require('better-sqlite3');

const SCHEMA_PATH = path.join(__dirname, '..', 'db', 'schema.sql');
const DEFAULT_DB_PATH = path.join(__dirname, '..', 'data', 'bank.db');


function createDb(dbPath = process.env.DB_PATH || DEFAULT_DB_PATH) {
  if (dbPath !== ':memory:') {
    fs.mkdirSync(path.dirname(dbPath), { recursive: true });
  }

  const db = new Database(dbPath);


  db.pragma('foreign_keys = ON');

  db.pragma('journal_mode = WAL');

  db.pragma('busy_timeout = 5000');

  db.pragma('synchronous = NORMAL');

  applySchema(db);
  return db;
}

function applySchema(db) {
  const sql = fs.readFileSync(SCHEMA_PATH, 'utf8');
  db.exec(sql);
}


function inImmediateTx(db, fn) {
  const run = db.transaction(() => fn(db));
  return run.immediate();
}

let singleton = null;

function getDb() {
  if (!singleton) {
    singleton = createDb();
  }
  return singleton;
}


function closeDb() {
  if (singleton) {
    singleton.close();
    singleton = null;
  }
}

module.exports = { createDb, getDb, closeDb, inImmediateTx };
