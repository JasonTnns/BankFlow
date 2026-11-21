'use strict';

const request = require('supertest');

const { createDb } = require('../src/db');
const { createApp } = require('../src/app');

const open = [];

/** An app with strict defaults (like production) unless a test relaxes something. */
function appWith(options = {}) {
  const db = createDb(':memory:');
  open.push(db);
  return createApp({
    db,
    options: {
      corsOrigins: [], corsOpen: false, docsEnabled: false,
      requireHttps: false, trustProxyHops: 0, accessLog: false,
      ...options,
    },
  });
}

afterEach(() => {
  while (open.length > 0) open.pop().close();
});

describe('HTTPS enforcement', () => {
  test('plain HTTP is refused, except the health check', async () => {
    const app = appWith({ requireHttps: true, trustProxyHops: 1 });

    expect((await request(app).get('/health')).status).toBe(200);

    const res = await request(app).get('/api/v1/dashboard');
    expect(res.status).toBe(403);
    expect(res.body.error).toMatchObject({ code: 'FORBIDDEN', message: 'HTTPS is required' });
  });

  test('behind a trusted proxy that says https, requests pass this check', async () => {
    const app = appWith({ requireHttps: true, trustProxyHops: 1 });

    const res = await request(app).get('/api/v1/dashboard').set('X-Forwarded-Proto', 'https');

    expect(res.status).toBe(401); // got past HTTPS, stopped by authentication
  });

  test('the header is ignored when no proxy is trusted, because it could be forged', async () => {
    const app = appWith({ requireHttps: true, trustProxyHops: 0 });

    const res = await request(app).get('/api/v1/dashboard').set('X-Forwarded-Proto', 'https');

    expect(res.status).toBe(403);
  });

  test('registration and login are refused over plain HTTP too', async () => {
    const app = appWith({ requireHttps: true, trustProxyHops: 1 });

    const res = await request(app)
      .post('/api/v1/auth/login')
      .send({ email: 'a@b.com', password: 'Passw0rd123' });

    expect(res.status).toBe(403);
  });
});

describe('documentation', () => {
  test('is not served when disabled', async () => {
    expect((await request(appWith({ docsEnabled: false })).get('/docs')).status).toBe(404);
  });

  test('is served when enabled', async () => {
    expect((await request(appWith({ docsEnabled: true })).get('/docs')).status).toBe(200);
  });
});

describe('CORS', () => {
  const allowOrigin = (res) => res.headers['access-control-allow-origin'];

  test('by default no browser origin is allowed', async () => {
    const res = await request(appWith()).get('/health').set('Origin', 'https://evil.example.com');

    expect(allowOrigin(res)).toBeUndefined();
  });

  test('only the listed origins are allowed', async () => {
    const app = appWith({ corsOrigins: ['https://app.example.com'] });

    const allowed = await request(app).get('/health').set('Origin', 'https://app.example.com');
    const refused = await request(app).get('/health').set('Origin', 'https://evil.example.com');

    expect(allowOrigin(allowed)).toBe('https://app.example.com');
    expect(allowOrigin(refused)).toBeUndefined();
  });

  test('development can allow any origin', async () => {
    const res = await request(appWith({ corsOpen: true })).get('/health').set('Origin', 'https://anything.example.com');

    expect(allowOrigin(res)).toBe('*');
  });
});

describe('response headers', () => {
  test('security headers are set and the framework is not advertised', async () => {
    const res = await request(appWith()).get('/health');

    expect(res.headers['x-content-type-options']).toBe('nosniff');
    expect(res.headers['strict-transport-security']).toBeDefined();
    expect(res.headers['x-powered-by']).toBeUndefined();
  });
});

describe('access log', () => {
  test('records the request and nothing secret', async () => {
    const lines = [];
    const app = appWith({ accessLog: true, logger: (line) => lines.push(line) });

    await request(app)
      .get('/api/v1/dashboard?accountNumber=200000000001')
      .set('Authorization', 'Bearer secret-token-value');
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(lines).toHaveLength(1);
    expect(JSON.parse(lines[0])).toMatchObject({ method: 'GET', path: '/api/v1/dashboard', status: 401 });
    for (const secret of ['secret-token-value', '200000000001', 'accountNumber', 'Bearer', '127.0.0.1']) {
      expect(lines[0]).not.toContain(secret);
    }
  });

  test('is silent when switched off', async () => {
    const lines = [];
    const app = appWith({ accessLog: false, logger: (line) => lines.push(line) });

    await request(app).get('/health');
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(lines).toEqual([]);
  });

  test('refused requests are logged too', async () => {
    const lines = [];
    const app = appWith({ accessLog: true, requireHttps: true, trustProxyHops: 1, logger: (line) => lines.push(line) });

    await request(app).get('/api/v1/dashboard');
    await new Promise((resolve) => setTimeout(resolve, 30));

    expect(JSON.parse(lines[0]).status).toBe(403);
  });
});