const { createDb } = require('./src/db');

const db = createDb(':memory:');
const tables = db
  .prepare("SELECT name FROM sqlite_master WHERE type = 'table'")
  .all();
console.log(tables);