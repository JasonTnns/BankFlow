'use strict';

const crypto = require('crypto');
const request = require('supertest');
const jwt = require('jsonwebtoken');

const config = require('../src/config');
const { createTestApp, registerUser } = require('./helpers');

describe('auth API', () => {
  let app;
  let db;

  beforeEach(() => {
    ({ app, db } = createTestApp());
  });
  afterEach(() => db.close());

  describe('POST /auth/register', () => {
    test('creates the user, a checking account with the welcome bonus, and a session', async () => {
      const { session, account } = await registerUser(app, { email: 'new@example.com', fullName: 'New Person' });

      expect(session.user).toMatchObject({ email: 'new@example.com', fullName: 'New Person' });
      expect(session.accessToken).toEqual(expect.any(String));
      expect(session.refreshToken.length).toBeGreaterThanOrEqual(20);
      expect(account).toMatchObject({ type: 'CHECKING', balanceMinor: 100000, currency: 'USD' });
    });

    test('never stores the password or the raw refresh token', async () => {
      const { session } = await registerUser(app, { email: 'safe@example.com' });

      const user = db.prepare('SELECT password_hash FROM users WHERE email = ?').get('safe@example.com');
      expect(user.password_hash).not.toContain('Passw0rd123');
      expect(user.password_hash.startsWith('$2')).toBe(true); // bcrypt

      const stored = db.prepare('SELECT refresh_token_hash FROM sessions').all().map((r) => r.refresh_token_hash);
      expect(stored).not.toContain(session.refreshToken);
    });

    test('rejects a duplicate email, case-insensitively', async () => {
      await registerUser(app, { email: 'dup@example.com' });
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({ email: 'DUP@Example.com', password: 'Passw0rd123', fullName: 'Other' });

      expect(res.status).toBe(409);
      expect(res.body.error.code).toBe('EMAIL_TAKEN');
    });

    test.each([
      ['too short', 'Ab1'],
      ['no number', 'OnlyLetters'],
      ['no letter', '12345678'],
    ])('rejects a weak password (%s)', async (_label, password) => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({ email: 'weak@example.com', password, fullName: 'Weak' });

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
      expect(res.body.error.details.fields.password).toBeDefined();
    });

    test('rejects an invalid email and a missing name', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .send({ email: 'not-an-email', password: 'Passw0rd123' });

      expect(res.status).toBe(400);
      expect(res.body.error.details.fields.email).toBeDefined();
      expect(res.body.error.details.fields.fullName).toBeDefined();
    });

    test('rejects a malformed JSON body with 400, not 500', async () => {
      const res = await request(app)
        .post('/api/v1/auth/register')
        .set('Content-Type', 'application/json')
        .send('{"email": ');

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
    });
  });

  describe('POST /auth/login', () => {
    test('signs in with correct credentials', async () => {
      await registerUser(app, { email: 'login@example.com' });
      const res = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: 'LOGIN@example.com', password: 'Passw0rd123' });

      expect(res.status).toBe(200);
      expect(res.body.user.email).toBe('login@example.com');
      expect(res.body.accessToken).toEqual(expect.any(String));
    });

    test('wrong password and unknown email give the same response', async () => {
      await registerUser(app, { email: 'real@example.com' });

      const wrongPassword = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: 'real@example.com', password: 'WrongPass999' });
      const unknownEmail = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: 'ghost@example.com', password: 'WrongPass999' });

      expect(wrongPassword.status).toBe(401);
      expect(unknownEmail.status).toBe(401);
      expect(wrongPassword.body).toEqual(unknownEmail.body);
      expect(wrongPassword.body.error.code).toBe('INVALID_CREDENTIALS');
    });
  });

  describe('access tokens', () => {
    test('protected routes require a token', async () => {
      const res = await request(app).get('/api/v1/dashboard');
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('UNAUTHORIZED');
    });

    test('a garbage token is rejected', async () => {
      const res = await request(app).get('/api/v1/dashboard').set('Authorization', 'Bearer not.a.jwt');
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('UNAUTHORIZED');
    });

    test('a token signed with the wrong secret is rejected', async () => {
      const forged = jwt.sign({ sub: crypto.randomUUID(), sid: crypto.randomUUID() }, 'attacker-secret');
      const res = await request(app).get('/api/v1/dashboard').set('Authorization', `Bearer ${forged}`);
      expect(res.status).toBe(401);
    });

    test('an expired token returns TOKEN_EXPIRED so the app knows to refresh', async () => {
      const expired = jwt.sign(
        { sub: crypto.randomUUID(), sid: crypto.randomUUID(), exp: Math.floor(Date.now() / 1000) - 10 },
        config.auth.jwtSecret
      );
      const res = await request(app).get('/api/v1/dashboard').set('Authorization', `Bearer ${expired}`);
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('TOKEN_EXPIRED');
    });

    test('logout revokes the session immediately', async () => {
      const { auth } = await registerUser(app);

      expect((await request(app).get('/api/v1/dashboard').set(auth)).status).toBe(200);
      expect((await request(app).post('/api/v1/auth/logout').set(auth)).status).toBe(204);
      expect((await request(app).get('/api/v1/dashboard').set(auth)).status).toBe(401);
    });
  });

  describe('POST /auth/refresh', () => {
    test('rotates tokens: the new pair works and the old refresh token does not', async () => {
      const { session: first } = await registerUser(app);

      const refreshed = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: first.refreshToken });
      expect(refreshed.status).toBe(200);
      expect(refreshed.body.refreshToken).not.toBe(first.refreshToken);

      const ok = await request(app)
        .get('/api/v1/dashboard')
        .set('Authorization', `Bearer ${refreshed.body.accessToken}`);
      expect(ok.status).toBe(200);
    });

    test('replaying an already-used refresh token revokes ALL of the user\'s sessions', async () => {
      const { session: first } = await registerUser(app);

      const second = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: first.refreshToken });
      expect(second.status).toBe(200);

      // An attacker (or buggy client) replays the old token.
      const replay = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: first.refreshToken });
      expect(replay.status).toBe(401);
      expect(replay.body.error.code).toBe('INVALID_REFRESH_TOKEN');

      // The legitimate, newer session was revoked as a precaution.
      const stolenSession = await request(app)
        .get('/api/v1/dashboard')
        .set('Authorization', `Bearer ${second.body.accessToken}`);
      expect(stolenSession.status).toBe(401);

      const newerRefresh = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: second.body.refreshToken });
      expect(newerRefresh.status).toBe(401);
    });

    test('an unknown refresh token is rejected', async () => {
      const res = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: 'x'.repeat(40) });
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('INVALID_REFRESH_TOKEN');
    });

    test('an expired session cannot be refreshed', async () => {
      const { session } = await registerUser(app);
      db.prepare('UPDATE sessions SET expires_at = ?').run('2000-01-01T00:00:00.000Z');

      const res = await request(app).post('/api/v1/auth/refresh').send({ refreshToken: session.refreshToken });
      expect(res.status).toBe(401);
      expect(res.body.error.code).toBe('INVALID_REFRESH_TOKEN');
    });
  });
});