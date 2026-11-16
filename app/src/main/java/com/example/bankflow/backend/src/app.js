'use strict';

const fs = require('fs');
const path = require('path');
const express = require('express');
const helmet = require('helmet');
const cors = require('cors');

const { createAuthService } = require('./services/authService');
const { createAccountService } = require('./services/accountService');
const { createTransferService } = require('./services/transferService');
const { createBeneficiaryService } = require('./services/beneficiaryService');
const { createRequireAuth } = require('./middleware/auth');
const { notFoundHandler, errorHandler } = require('./middleware/errorHandler');
const { createAuthRoutes } = require('./routes/authRoutes');
const { createAccountRoutes } = require('./routes/accountRoutes');
const { createTransferRoutes } = require('./routes/transferRoutes');
const { createBeneficiaryRoutes } = require('./routes/beneficiaryRoutes');
const { createInsightsService } = require('./services/insightsService');
const { createInsightsRoutes } = require('./routes/insightsRoutes');
const { createProfileService } = require('./services/profileService');
const { createProfileRoutes } = require('./routes/profileRoutes');

const OPENAPI_PATH = path.join(__dirname, '..', 'openapi.yaml');


function createApp({ db }) {
  const app = express();
  app.disable('x-powered-by');

  const authService = createAuthService(db);
  const accountService = createAccountService(db);
  const transferService = createTransferService(db);
  const beneficiaryService = createBeneficiaryService(db);
  const insightsService = createInsightsService(db);
  const profileService = createProfileService(db);
  const requireAuth = createRequireAuth(authService);

  if (fs.existsSync(OPENAPI_PATH)) {
    const swaggerUi = require('swagger-ui-express');
    const YAML = require('yaml');
    const spec = YAML.parse(fs.readFileSync(OPENAPI_PATH, 'utf8'));
    app.use('/docs', swaggerUi.serve, swaggerUi.setup(spec));
  }

  app.use(helmet());
  app.use(cors());
  app.use(express.json({ limit: '10kb' }));

  app.get('/health', (_req, res) => {
    try {
      db.prepare('SELECT 1').get();
      res.json({ status: 'ok' });
    } catch {
      res.status(503).json({ status: 'unavailable' });
    }
  });

  const api = express.Router();
  api.use('/auth', createAuthRoutes({ authService, requireAuth }));
  api.use('/', createAccountRoutes({ accountService, requireAuth }));
  api.use('/', createTransferRoutes({ transferService, requireAuth }));
  api.use('/', createBeneficiaryRoutes({ beneficiaryService, requireAuth }));
  app.use('/api/v1', api);
  api.use('/', createInsightsRoutes({ insightsService, requireAuth }));
    api.use('/', createProfileRoutes({ profileService, requireAuth }));

  app.use(notFoundHandler);
  app.use(errorHandler);

  return app;
}

module.exports = { createApp };