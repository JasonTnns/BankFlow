'use strict';

/**
 * Builds the Express app. Nothing here listens on a port, so tests can import createApp({ db, options })
 * with an in-memory database and drive it with supertest.
 *
 *   /health          liveness + database check
 *   /docs            Swagger UI (development; DOCS_ENABLED in production)
 *   /api/v1/auth/*   register, login, refresh, logout
 *   /api/v1/*        dashboard, accounts, transactions, transfers, recipients, beneficiaries, insights, profile
 *
 * `options` override the HTTP settings from config (tests use this to exercise production behaviour).
 */

const fs = require('fs');
const path = require('path');
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

const config = require('./config');
const { createAuthService } = require('./services/authService');
const { createAccountService } = require('./services/accountService');
const { createTransferService } = require('./services/transferService');
const { createBeneficiaryService } = require('./services/beneficiaryService');
const { createInsightsService } = require('./services/insightsService');
const { createProfileService } = require('./services/profileService');
const { createRequireAuth } = require('./middleware/auth');
const { requireHttps } = require('./middleware/requireHttps');
const { createRequestLogger } = require('./middleware/requestLogger');
const { notFoundHandler, errorHandler } = require('./middleware/errorHandler');
const { createAuthRoutes } = require('./routes/authRoutes');
const { createAccountRoutes } = require('./routes/accountRoutes');
const { createTransferRoutes } = require('./routes/transferRoutes');
const { createBeneficiaryRoutes } = require('./routes/beneficiaryRoutes');
const { createInsightsRoutes } = require('./routes/insightsRoutes');
const { createProfileRoutes } = require('./routes/profileRoutes');

const OPENAPI_PATH = path.join(__dirname, '..', 'openapi.yaml');

/**
 * @param {{ db: import('better-sqlite3').Database, options?: object }} deps
 * @returns {import('express').Express}
 */
function createApp({ db, options = {} }) {
  const settings = { ...config.server, ...options };
  const app = express();
  app.disable('x-powered-by');

  // The real client address and protocol, but only from proxies we were told about.
  if (settings.trustProxyHops > 0) app.set('trust proxy', settings.trustProxyHops);

  // First, so refused requests are logged too.
  if (settings.accessLog) app.use(createRequestLogger(settings.logger));
  if (settings.requireHttps) app.use(requireHttps);

  // ---- Services (one shared database connection)
  const authService = createAuthService(db);
  const accountService = createAccountService(db);
  const transferService = createTransferService(db);
  const beneficiaryService = createBeneficiaryService(db);
  const insightsService = createInsightsService(db);
  const profileService = createProfileService(db);
  const requireAuth = createRequireAuth(authService);

  // ---- API documentation. Mounted BEFORE helmet because Swagger UI needs inline scripts that
  //      helmet's default CSP would block. Off in production unless DOCS_ENABLED=true.
  if (settings.docsEnabled && fs.existsSync(OPENAPI_PATH)) {
    const swaggerUi = require('swagger-ui-express');
    const YAML = require('yaml');
    const spec = YAML.parse(fs.readFileSync(OPENAPI_PATH, 'utf8'));
    app.use('/docs', swaggerUi.serve, swaggerUi.setup(spec));
  }

  // ---- Global middleware
  app.use(helmet());
  if (settings.corsOrigins.length > 0) {
    app.use(cors({ origin: settings.corsOrigins }));
  } else if (settings.corsOpen) {
    app.use(cors());
  }
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
  api.use('/', createBeneficiaryRoutes({ beneficiaryService, requireAuth }));
  api.use('/', createInsightsRoutes({ insightsService, requireAuth }));
  api.use('/', createProfileRoutes({ profileService, requireAuth }));
  app.use('/api/v1', api);

  // ---- Errors (must be last)
  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };