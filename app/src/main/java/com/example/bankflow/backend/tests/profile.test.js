'use strict';

const request = require('supertest');

const { createTestApp, registerUser } = require('./helpers');

describe('profile API', () => {
  let app;
  let db;
  let alice;
  let bob;

  beforeEach(async () => {
    ({ app, db } = createTestApp());
    alice = await registerUser(app, { email: 'alice@example.com', fullName: 'Alice Tester' });
    bob = await registerUser(app, { email: 'bob@example.com', fullName: 'Bob Builder' });
  });
  afterEach(() => db.close());

  const getMe = (headers) => request(app).get('/api/v1/me').set(headers);
  const patchMe = (headers, body) => request(app).patch('/api/v1/me').set(headers).send(body);

  describe('GET /me', () => {
    test("returns the signed-in user's profile", async () => {
      const res = await getMe(alice.auth);

      expect(res.status).toBe(200);
      expect(res.body.profile).toEqual({
        id: alice.userId,
        email: 'alice@example.com',
        fullName: 'Alice Tester',
        themePreference: 'SYSTEM',
        dailyTransferLimitMinor: 500000,
        createdAt: expect.any(String),
      });
    });

    test('never exposes the password hash', async () => {
      const text = JSON.stringify((await getMe(alice.auth)).body);

      expect(text).not.toContain('password');
      expect(text).not.toContain('$2');
    });

    test("each user sees only their own profile", async () => {
      expect((await getMe(bob.auth)).body.profile.fullName).toBe('Bob Builder');
    });
  });

  describe('PATCH /me', () => {
    test('renames, trimming the name, and the change is stored', async () => {
      const res = await patchMe(alice.auth, { fullName: '  Alice Renamed  ' });

      expect(res.status).toBe(200);
      expect(res.body.profile.fullName).toBe('Alice Renamed');
      expect((await getMe(alice.auth)).body.profile.fullName).toBe('Alice Renamed');
    });

    test.each(['SYSTEM', 'LIGHT', 'DARK'])('accepts the %s theme', async (themePreference) => {
      const res = await patchMe(alice.auth, { themePreference });

      expect(res.status).toBe(200);
      expect(res.body.profile.themePreference).toBe(themePreference);
    });

    test('changes both at once', async () => {
      const res = await patchMe(alice.auth, { fullName: 'Alice 2', themePreference: 'DARK' });

      expect(res.body.profile).toMatchObject({ fullName: 'Alice 2', themePreference: 'DARK' });
    });

    test('changing one field leaves the other alone', async () => {
      await patchMe(alice.auth, { themePreference: 'DARK' });
      const res = await patchMe(alice.auth, { fullName: 'Alice 3' });

      expect(res.body.profile).toMatchObject({ fullName: 'Alice 3', themePreference: 'DARK' });
    });

    test('does not touch anyone else', async () => {
      await patchMe(alice.auth, { fullName: 'Alice Renamed', themePreference: 'DARK' });

      expect((await getMe(bob.auth)).body.profile).toMatchObject({
        fullName: 'Bob Builder',
        themePreference: 'SYSTEM',
      });
    });

    test('records when the profile changed', async () => {
      db.prepare('UPDATE users SET updated_at = ? WHERE id = ?').run('2000-01-01T00:00:00.000Z', alice.userId);

      await patchMe(alice.auth, { fullName: 'Alice Renamed' });

      const { updated_at: updatedAt } = db.prepare('SELECT updated_at FROM users WHERE id = ?').get(alice.userId);
      expect(updatedAt > '2000-01-01T00:00:00.000Z').toBe(true);
    });

    test.each([
      ['an empty body', {}, 'form'],
      ['a blank name', { fullName: '   ' }, 'fullName'],
      ['a name over 100 characters', { fullName: 'x'.repeat(101) }, 'fullName'],
      ['a null name', { fullName: null }, 'fullName'],
      ['a numeric name', { fullName: 123 }, 'fullName'],
      ['an unknown theme', { themePreference: 'NEON' }, 'themePreference'],
      ['a theme in the wrong case', { themePreference: 'dark' }, 'themePreference'],
    ])('rejects %s', async (_label, body, where) => {
      const res = await patchMe(alice.auth, body);

      expect(res.status).toBe(400);
      expect(res.body.error.code).toBe('VALIDATION_ERROR');
      const { details } = res.body.error;
      expect(where === 'form' ? details.form : details.fields[where]).toBeDefined();
    });

    test('a rejected request changes nothing', async () => {
      await patchMe(alice.auth, { fullName: 'Valid Name', themePreference: 'NEON' });

      expect((await getMe(alice.auth)).body.profile.fullName).toBe('Alice Tester');
    });

    test('only name and theme can change: email, limit and id in the body are ignored', async () => {
      const res = await patchMe(alice.auth, {
        fullName: 'Alice 2',
        email: 'evil@example.com',
        dailyTransferLimitMinor: 999999999,
        id: bob.userId,
      });

      expect(res.status).toBe(200);
      expect(res.body.profile).toMatchObject({
        id: alice.userId,
        email: 'alice@example.com',
        dailyTransferLimitMinor: 500000,
        fullName: 'Alice 2',
      });
    });

    test('a body with only forbidden fields has nothing to update', async () => {
      const res = await patchMe(alice.auth, { dailyTransferLimitMinor: 999999999 });

      expect(res.status).toBe(400);
      expect((await getMe(alice.auth)).body.profile.dailyTransferLimitMinor).toBe(500000);
    });
  });

  describe('the session reflects the profile', () => {
    test('signing in again returns the new name and theme', async () => {
      await patchMe(alice.auth, { fullName: 'Alice Renamed', themePreference: 'DARK' });

      const login = await request(app)
        .post('/api/v1/auth/login')
        .send({ email: 'alice@example.com', password: 'Passw0rd123' });

      expect(login.body.user).toMatchObject({ fullName: 'Alice Renamed', themePreference: 'DARK' });
    });

    test('refreshing the session returns the new name and theme', async () => {
      await patchMe(alice.auth, { fullName: 'Alice Renamed', themePreference: 'LIGHT' });

      const refreshed = await request(app)
        .post('/api/v1/auth/refresh')
        .send({ refreshToken: alice.session.refreshToken });

      expect(refreshed.body.user).toMatchObject({ fullName: 'Alice Renamed', themePreference: 'LIGHT' });
    });
  });

  describe('authentication', () => {
    test('both endpoints require a token', async () => {
      expect((await request(app).get('/api/v1/me')).status).toBe(401);
      expect((await request(app).patch('/api/v1/me').send({ fullName: 'X' })).status).toBe(401);
    });

    test('after logout the token stops working', async () => {
      await request(app).post('/api/v1/auth/logout').set(alice.auth);

      expect((await getMe(alice.auth)).status).toBe(401);
      expect((await patchMe(alice.auth, { fullName: 'X' })).status).toBe(401);
    });
  });
});