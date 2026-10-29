'use strict';

/**
 * Builds the Express app. Nothing here listens on a port, so tests can import
 * createApp({ db }) with an in-memory database and drive it with supertest.
 *
 *   /health          liveness + database check
 *   /docs            Swagger UI (only if backend/openapi.yaml exists)
 *   /api/v1/auth/*   register, login, refresh, logout
 *   /api/v1/*        dashboard, accounts, transactions, transfers
 */

const fs = require('fs');
const path = require('path');
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

const { createAuthService } = require('./services/authService');
const { createAccountService } = require('./services/accountService');
const { createTransferService } = require('./services/transferService');
const { createRequireAuth } = require('./middleware/auth');
const { notFoundHandler, errorHandler } = require('./middleware/errorHandler');
const { createAuthRoutes } = require('./routes/authRoutes');
const { createAccountRoutes } = require('./routes/accountRoutes');
const { createTransferRoutes } = require('./routes/transferRoutes');

const OPENAPI_PATH = path.join(__dirname, '..', 'openapi.yaml');

/**
 * @param {{ db: import('better-sqlite3').Database }} deps
 * @returns {import('express').Express}
 */
function createApp({ db }) {
  const app = express();
  app.disable('x-powered-by');

  // ---- Services (one shared database connection)
  const authService = createAuthService(db);
  const accountService = createAccountService(db);
  const transferService = createTransferService(db);
  const requireAuth = createRequireAuth(authService);

  // ---- API documentation. Mounted BEFORE helmet because Swagger UI needs
  //      inline scripts that helmet's default CSP would block.
  if (fs.existsSync(OPENAPI_PATH)) {
    const swaggerUi = require('swagger-ui-express');
    const YAML = require('yaml');
    const spec = YAML.parse(fs.readFileSync(OPENAPI_PATH, 'utf8'));
    app.use('/docs', swaggerUi.serve, swaggerUi.setup(spec));
  }

  // ---- Global middleware
  app.use(helmet());
  // The Android app is not a browser and ignores CORS. Enabled so a web client
  // or Swagger "Try it out" from another origin works during development.
  app.use(cors());
  // Banking payloads are tiny; a small limit blocks oversized-body abuse.
  app.use(express.json({ limit: '10kb' }));

  // ---- Health check (no auth)
  app.get('/health', (_req, res) => {
    try {
      db.prepare('SELECT 1').get();
      res.json({ status: 'ok' });
    } catch {
      res.status(503).json({ status: 'unavailable' });
    }
  });

  // ---- API v1
  const api = express.Router();
  api.use('/auth', createAuthRoutes({ authService, requireAuth }));
  api.use('/', createAccountRoutes({ accountService, requireAuth }));
  api.use('/', createTransferRoutes({ transferService, requireAuth }));
  app.use('/api/v1', api);

  // ---- Errors (must be last)
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };