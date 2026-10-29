'use strict';

/**
 * Process entry point: open the database, build the app, listen, and shut
 * down cleanly on Ctrl+C / SIGTERM.
 */

const config = require('./config');
const { getDb, closeDb } = require('./db');
const { createApp } = require('./app');

const app = createApp({ db: getDb() });

const server = app.listen(config.port, () => {
  console.log(`BankFlow API listening on http://localhost:${config.port} (${config.env})`);
  console.log(`Health check: http://localhost:${config.port}/health`);
});

function shutdown(signal) {
  console.log(`${signal} received, shutting down...`);
  server.close(() => {
    closeDb();
    process.exit(0);
  });
  // Force-exit if connections refuse to drain.
  setTimeout(() => process.exit(1), 10_000).unref();
}

process.on('SIGINT', () => shutdown('SIGINT'));
process.on('SIGTERM', () => shutdown('SIGTERM'));