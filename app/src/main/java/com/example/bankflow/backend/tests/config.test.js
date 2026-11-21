'use strict';

const KEYS = [
  'NODE_ENV', 'JWT_SECRET', 'CORS_ORIGINS', 'TRUST_PROXY_HOPS', 'REQUIRE_HTTPS',
  'DOCS_ENABLED', 'ACCESS_LOG', 'ALLOW_SEED',
];
const SECRET = 'x'.repeat(40);

/** Loads src/config.js fresh with exactly these environment variables set (and the project's .env ignored). */
function loadConfig(env) {
  const saved = Object.fromEntries(KEYS.map((key) => [key, process.env[key]]));
  try {
    for (const key of KEYS) delete process.env[key];
    Object.assign(process.env, env);

    let config;
    jest.isolateModules(() => {
      jest.doMock('dotenv', () => ({ config: () => ({}) }));
      config = require('../src/config');
    });
    return config;
  } finally {
    for (const key of KEYS) {
      if (saved[key] === undefined) delete process.env[key];
      else process.env[key] = saved[key];
    }
  }
}

describe('server settings', () => {
  test('development is relaxed: open CORS, docs on, seeding allowed, no HTTPS requirement', () => {
    expect(loadConfig({ NODE_ENV: 'development' }).server).toMatchObject({
      corsOpen: true,
      corsOrigins: [],
      docsEnabled: true,
      allowSeed: true,
      requireHttps: false,
      accessLog: false,
      trustProxyHops: 0,
    });
  });

  test('production is strict by default', () => {
    expect(loadConfig({ NODE_ENV: 'production', JWT_SECRET: SECRET }).server).toMatchObject({
      corsOpen: false,
      corsOrigins: [],
      docsEnabled: false,
      allowSeed: false,
      requireHttps: true,
      accessLog: true,
    });
  });

  test('every default can be overridden on purpose', () => {
    const server = loadConfig({
      NODE_ENV: 'production', JWT_SECRET: SECRET, DOCS_ENABLED: 'true', ALLOW_SEED: 'yes',
      REQUIRE_HTTPS: 'false', ACCESS_LOG: '0',
    }).server;

    expect(server).toMatchObject({ docsEnabled: true, allowSeed: true, requireHttps: false, accessLog: false });
  });

  test('listed CORS origins replace the open default, even in development', () => {
    const server = loadConfig({
      NODE_ENV: 'development',
      CORS_ORIGINS: 'https://a.example.com, https://b.example.com',
    }).server;

    expect(server.corsOrigins).toEqual(['https://a.example.com', 'https://b.example.com']);
    expect(server.corsOpen).toBe(false);
  });

  test.each(['https://a.example.com/', 'https://a.example.com/path', 'not a url', 'a.example.com'])(
    'a CORS origin like %p is rejected at startup',
    (origin) => {
      expect(() => loadConfig({ NODE_ENV: 'development', CORS_ORIGINS: origin })).toThrow(/CORS_ORIGINS/);
    }
  );

  test('invalid values fail loudly instead of being guessed', () => {
    expect(() => loadConfig({ NODE_ENV: 'development', REQUIRE_HTTPS: 'maybe' })).toThrow(/REQUIRE_HTTPS/);
    expect(() => loadConfig({ NODE_ENV: 'development', TRUST_PROXY_HOPS: 'abc' })).toThrow(/TRUST_PROXY_HOPS/);
  });
});

describe('production secrets', () => {
  test('a JWT secret is required, and it must be long', () => {
    expect(() => loadConfig({ NODE_ENV: 'production' })).toThrow(/JWT_SECRET is required/);
    expect(() => loadConfig({ NODE_ENV: 'production', JWT_SECRET: 'short' })).toThrow(/at least 32/);
  });
});

describe('assertDeployable', () => {
  test('HTTPS enforcement without a trusted proxy would refuse every request, so it refuses to start', () => {
    const config = loadConfig({ NODE_ENV: 'production', JWT_SECRET: SECRET });

    expect(() => config.assertDeployable()).toThrow(/TRUST_PROXY_HOPS/);
  });

  test('a proxy, or switching the requirement off, is accepted', () => {
    expect(() =>
      loadConfig({ NODE_ENV: 'production', JWT_SECRET: SECRET, TRUST_PROXY_HOPS: '1' }).assertDeployable()
    ).not.toThrow();
    expect(() =>
      loadConfig({ NODE_ENV: 'production', JWT_SECRET: SECRET, REQUIRE_HTTPS: 'false' }).assertDeployable()
    ).not.toThrow();
  });

  test('development needs nothing', () => {
    expect(() => loadConfig({ NODE_ENV: 'development' }).assertDeployable()).not.toThrow();
  });
});