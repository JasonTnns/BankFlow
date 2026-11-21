'use strict';

/**
 * Process entry point: check the configuration, open the database, build the app, listen, and shut down
 * cleanly on Ctrl+C / SIGTERM. A crash exits non-zero so a supervisor (systemd, Docker, ...) restarts it.
 */

const config = require('./config');
const { getDb, closeDb } = require('./db');
const { createApp } = require('./app');

config.assertDeployable();

process.on('unhandledRejection', (reason) => {
  console.error('[fatal] unhandled rejection', reason);
  process.exit(1);
});
process.on('uncaughtException', (error) => {
  console.error('[fatal] uncaught exception', error);
  process.exit(1);
});

const app = createApp({ db: getDb() });

const server = app.listen(config.port, () => {
  console.log(`BankFlow API listening on port ${config.port} (${config.env})`);
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