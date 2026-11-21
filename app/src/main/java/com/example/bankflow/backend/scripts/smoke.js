'use strict';

/**
 * End-to-end smoke test of a RUNNING server.
 *
 *   npm run smoke -- https://api.yourdomain.com
 *   npm run smoke                      (defaults to http://localhost:3000)
 *
 * Creates two throw-away users, exercises the main flows, then deletes both. Uses 5 of the 10 login and
 * registration attempts the rate limiter allows per 15 minutes, so don't run it in a tight loop.
 */

const base = (process.argv[2] || process.env.BASE_URL || 'http://localhost:3000').replace(/\/$/, '');
const api = `${base}/api/v1`;
const PASSWORD = 'Passw0rd123smoke';
let failures = 0;

function check(name, condition, detail = '') {
  console.log(`${condition ? 'PASS' : 'FAIL'}  ${name}${!condition && detail ? `  (${detail})` : ''}`);
  if (!condition) failures += 1;
}

async function call(method, path, { token, body, headers = {} } = {}) {
  const response = await fetch(`${api}${path}`, {
    method,
    headers: {
      ...(body ? { 'Content-Type': 'application/json' } : {}),
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...headers,
    },
    body: body ? JSON.stringify(body) : undefined,
  });
  const text = await response.text();
  return { status: response.status, headers: response.headers, body: text ? JSON.parse(text) : null };
}

const uuid = () => globalThis.crypto.randomUUID();
const code = (res) => res.body?.error?.code;

async function main() {
  const health = await fetch(`${base}/health`);
  check('health check', health.status === 200);

  const stamp = uuid().slice(0, 8);
  const register = (name) =>
    call('POST', '/auth/register', { body: { email: `smoke-${name}-${stamp}@example.com`, password: PASSWORD, fullName: `Smoke ${name}` } });

  const a = await register('a');
  const b = await register('b');
  check('registration', a.status === 201 && b.status === 201, `${a.status}/${b.status}`);
  if (a.status !== 201 || b.status !== 201) return;

  let tokenA = a.body.accessToken;
  const tokenB = b.body.accessToken;
  const emailA = a.body.user.email;

  const accountsA = (await call('GET', '/accounts', { token: tokenA })).body.accounts;
  const accountsB = (await call('GET', '/accounts', { token: tokenB })).body.accounts;
  check('welcome bonus in the ledger', accountsA[0].balanceMinor === 100000 && accountsB[0].balanceMinor === 100000);

  check('protected route needs a token', (await call('GET', '/dashboard')).status === 401);
  check('garbage token is refused', (await call('GET', '/dashboard', { token: 'nonsense' })).status === 401);

  // ---- transfer, duplicate protection, insufficient funds
  const transfer = { sourceAccountId: accountsA[0].id, destinationAccountNumber: accountsB[0].accountNumber, amountMinor: 2500, category: 'FOOD' };
  const key = uuid();
  const first = await call('POST', '/transfers', { token: tokenA, body: transfer, headers: { 'Idempotency-Key': key } });
  const replay = await call('POST', '/transfers', { token: tokenA, body: transfer, headers: { 'Idempotency-Key': key } });
  check('transfer is created', first.status === 201 && first.body.sourceAccountBalanceMinor === 97500);
  check('the same key replays instead of paying twice', replay.status === 200 && replay.headers.get('idempotent-replayed') === 'true' && replay.body.transfer.id === first.body.transfer.id);
  const afterReplay = (await call('GET', `/accounts/${accountsA[0].id}`, { token: tokenA })).body.account.balanceMinor;
  check('money moved exactly once', afterReplay === 97500, String(afterReplay));
  const broke = await call('POST', '/transfers', { token: tokenA, body: { ...transfer, amountMinor: 5000000 }, headers: { 'Idempotency-Key': uuid() } });
  check('insufficient funds is refused', broke.status === 422 && code(broke) === 'INSUFFICIENT_FUNDS');

  // ---- reads
  const history = await call('GET', `/accounts/${accountsA[0].id}/transactions`, { token: tokenA });
  check('history lists the welcome bonus and the transfer', history.status === 200 && history.body.items.length === 2);
  const details = await call('GET', `/accounts/${accountsA[0].id}/transactions/${first.body.transfer.id}`, { token: tokenA });
  check('transaction details', details.status === 200 && details.body.transaction.amountMinor === 2500);
  const now = new Date();
  const from = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), 1)).toISOString();
  const to = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth() + 1, 1)).toISOString();
  const insights = await call('GET', `/insights/summary?from=${from}&to=${to}`, { token: tokenA });
  check('insights count the spending', insights.status === 200 && insights.body.summary.spentMinor === 2500);

  // ---- recipients and profile
  const lookup = await call('GET', `/recipients/lookup?accountNumber=${accountsB[0].accountNumber}`, { token: tokenA });
  check('recipient lookup shows a shortened name', lookup.status === 200 && lookup.body.recipient.displayName.endsWith('.'));
  const saved = await call('POST', '/beneficiaries', { token: tokenA, body: { nickname: 'Smoke B', accountNumber: accountsB[0].accountNumber } });
  check('recipient can be saved', saved.status === 201);
  check('and removed', (await call('DELETE', `/beneficiaries/${saved.body.beneficiary.id}`, { token: tokenA })).status === 204);
  const patched = await call('PATCH', '/me', { token: tokenA, body: { themePreference: 'DARK' } });
  check('profile theme', patched.status === 200 && patched.body.profile.themePreference === 'DARK');

  // ---- sessions: rotation and replay detection
  const refreshed = await call('POST', '/auth/refresh', { body: { refreshToken: a.body.refreshToken } });
  check('refresh rotates the tokens', refreshed.status === 200 && refreshed.body.refreshToken !== a.body.refreshToken);
  const replayed = await call('POST', '/auth/refresh', { body: { refreshToken: a.body.refreshToken } });
  check('reusing an old refresh token is refused', replayed.status === 401 && code(replayed) === 'INVALID_REFRESH_TOKEN');
  const revoked = await call('GET', '/dashboard', { token: refreshed.body.accessToken });
  check('and revokes the newer session too', revoked.status === 401);
  const login = await call('POST', '/auth/login', { body: { email: emailA, password: PASSWORD } });
  check('logging in again works', login.status === 200);
  tokenA = login.body.accessToken;
  check('logout ends the session', (await call('POST', '/auth/logout', { token: tokenA })).status === 204 && (await call('GET', '/dashboard', { token: tokenA })).status === 401);

  // ---- account deletion (needs a live session)
  const relogin = await call('POST', '/auth/login', { body: { email: emailA, password: PASSWORD } });
  tokenA = relogin.body.accessToken;
  const wrong = await call('DELETE', '/me', { token: tokenA, body: { password: 'WrongPass999' } });
  check('a wrong password does not delete anything', wrong.status === 403 && code(wrong) === 'PASSWORD_INCORRECT');
  check('deleting account A', (await call('DELETE', '/me', { token: tokenA, body: { password: PASSWORD } })).status === 204);
  check('A can no longer sign in', code(await call('POST', '/auth/login', { body: { email: emailA, password: PASSWORD } })) === 'INVALID_CREDENTIALS');
  const bHistory = await call('GET', `/accounts/${accountsB[0].id}/transactions`, { token: tokenB });
  check("B's history now shows A as 'Deleted user'", bHistory.body.items[0].counterparty.name === 'Deleted user');
  check('deleting account B', (await call('DELETE', '/me', { token: tokenB, body: { password: PASSWORD } })).status === 204);
}

main()
  .then(() => {
    console.log(failures === 0 ? '\nAll checks passed.' : `\n${failures} check(s) FAILED.`);
    process.exit(failures === 0 ? 0 : 1);
  })
  .catch((error) => {
    console.error(`Smoke test could not run: ${error.message}`);
    process.exit(1);
  });