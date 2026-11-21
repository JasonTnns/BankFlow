'use strict';

const { spawnSync } = require('child_process');
const fs = require('fs');
const os = require('os');
const path = require('path');

const ROOT = path.join(__dirname, '..');

function runSeed(env) {
  const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bankflow-seed-'));
  const dbPath = path.join(dir, 'bank.db');
  const result = spawnSync(process.execPath, ['src/seed.js'], {
    cwd: ROOT,
    encoding: 'utf8',
    env: { ...process.env, NODE_ENV: 'production', JWT_SECRET: 'x'.repeat(40), DB_PATH: dbPath, ...env },
  });
  return { ...result, dbPath, dir };
}

describe('the seed script in production', () => {
  test('refuses to run, and does not even create a database', () => {
    const run = runSeed({ ALLOW_SEED: 'false' });

    expect(run.status).toBe(1);
    expect(run.stderr).toContain('Refusing to run the seed script');
    expect(fs.existsSync(run.dbPath)).toBe(false);
    fs.rmSync(run.dir, { recursive: true, force: true });
  });

  test('refuses --reset too, which would delete the database', () => {
    const dir = fs.mkdtempSync(path.join(os.tmpdir(), 'bankflow-seed-'));
    const dbPath = path.join(dir, 'bank.db');
    fs.writeFileSync(dbPath, 'precious');

    const run = spawnSync(process.execPath, ['src/seed.js', '--reset'], {
      cwd: ROOT,
      encoding: 'utf8',
      env: { ...process.env, NODE_ENV: 'production', JWT_SECRET: 'x'.repeat(40), DB_PATH: dbPath, ALLOW_SEED: 'false' },
    });

    expect(run.status).toBe(1);
    expect(fs.readFileSync(dbPath, 'utf8')).toBe('precious');
    fs.rmSync(dir, { recursive: true, force: true });
  });

  test('runs when explicitly allowed', () => {
    const run = runSeed({ ALLOW_SEED: 'true' });

    expect(run.status).toBe(0);
    expect(fs.existsSync(run.dbPath)).toBe(true);
    fs.rmSync(run.dir, { recursive: true, force: true });
  });
});