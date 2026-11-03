// Run from the repository root after: mvn -B -DskipTests package
// Starts only its own loopback server with a NEW in-memory H2 database.
// No requests are sent to a user database, SMTP server or real TMDB account.
import { spawn } from 'node:child_process';
import { createWriteStream } from 'node:fs';
import { mkdir, writeFile } from 'node:fs/promises';
import { once } from 'node:events';
import { resolve } from 'node:path';

const root = resolve(import.meta.dirname, '../..');
const results = [];
let base;
async function request(method, path, body, token, headers = {}) {
  const response = await fetch(base + path, {
    method, headers: { ...(body === undefined ? {} : { 'Content-Type': 'application/json' }),
      ...(token ? { Authorization: `Bearer ${token}` } : {}), ...headers },
    body: body === undefined ? undefined : JSON.stringify(body),
    signal: AbortSignal.timeout(15000),
  });
  const text = await response.text();
  let data;
  try { data = JSON.parse(text); } catch { data = null; }
  return { status: response.status, data, headers: response.headers };
}
function check(id, expected, actual, ok) {
  results.push({ id, expected, actual, passed: Boolean(ok) });
  console.log(`${ok ? 'PASS' : 'FAIL'} ${id}: ${JSON.stringify(actual)}`);
}
async function status(id, method, path, body, token, expected, headers) {
  const response = await request(method, path, body, token, headers);
  check(id, { status: expected }, { status: response.status, code: response.data?.error?.code }, response.status === expected);
  return response;
}
async function start(provider, expectConfigurationFailure = false) {
  const log = createWriteStream(resolve(root, `target/audit-server-${provider}.log`));
  const child = spawn('java', ['-jar', resolve(root, 'target/echo-1.0-SNAPSHOT.jar'),
    '--spring.profiles.active=dev', '--server.address=127.0.0.1', '--server.port=0',
    `--spring.datasource.url=jdbc:h2:mem:audit_${provider};MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1`,
    '--spring.datasource.username=sa', '--spring.datasource.password=audit',
    '--spring.datasource.driver-class-name=org.h2.Driver', '--spring.jpa.hibernate.ddl-auto=validate',
    '--spring.h2.console.enabled=false', '--spring.flyway.enabled=true', '--app.scheduling.enabled=false',
    '--app.email.provider=in-memory', `--app.tmdb.provider=${provider}`, '--app.tmdb.access-token=',
    '--app.auth.bcrypt.strength=4', '--app.security.rate-limit.register.capacity=1000',
    '--app.security.rate-limit.login.capacity=1000',
  ], { cwd: root, windowsHide: true, stdio: ['ignore', 'pipe', 'pipe'] });
  child.stdout.pipe(log); child.stderr.pipe(log, { end: false });
  let output = '';
  child.stdout.on('data', chunk => { output = (output + chunk).slice(-40000); });
  let startError;
  child.on('error', error => { startError = error; });
  try {
    const deadline = Date.now() + 180000;
    while (Date.now() < deadline) {
      if (startError) throw startError;
      if (child.exitCode !== null) {
        if (expectConfigurationFailure) return { child, log, startupFailure: output.includes('app.tmdb.access-token must be configured when app.tmdb.provider=http') };
        throw new Error(`Backend exited: ${child.exitCode}; see target/audit-server-${provider}.log`);
      }
      const port = output.match(/Tomcat started on port (\d+)/)?.[1];
      if (port) { base = `http://127.0.0.1:${port}`; return { child, log }; }
      await new Promise(done => setTimeout(done, 250));
    }
    throw new Error('Backend startup timeout');
  } catch (error) { child.kill(); log.end(); throw error; }
}
async function stop({ child, log }) {
  if (child.exitCode === null) { const exited = once(child, 'exit'); child.kill(); await exited; }
  log.end();
}
await mkdir(resolve(root, 'target'), { recursive: true });
let server;
try {
  server = await start('stub');
  await status('anonymous profile rejected', 'GET', '/v1/me', undefined, null, 401);
  await status('CORS Vite origin', 'OPTIONS', '/v1/auth/register', undefined, null, 200,
    { Origin: 'http://localhost:5173', 'Access-Control-Request-Method': 'POST', 'Access-Control-Request-Headers': 'content-type' });
  await status('CORS unknown origin rejected', 'OPTIONS', '/v1/auth/register', undefined, null, 403,
    { Origin: 'https://audit.invalid', 'Access-Control-Request-Method': 'POST' });
  const owner = await status('username-only registration', 'POST', '/v1/auth/register', { username: 'AuditOwner', password: 'AuditPass123!' }, null, 201);
  const token = owner.data.tokens.accessToken;
  check('registration issues tokens without email', 'access and refresh tokens; email=null; verification=false',
    { hasAccess: Boolean(token), hasRefresh: Boolean(owner.data.tokens.refreshToken), email: owner.data.user.email, verification: owner.data.emailVerificationRequired },
    token && owner.data.tokens.refreshToken && owner.data.user.email === null && owner.data.emailVerificationRequired === false);
  await status('current profile', 'GET', '/v1/me', undefined, token, 200);
  const profile = await status('public profile', 'GET', `/v1/users/${owner.data.user.userId}`, undefined, null, 200);
  check('public profile privacy', ['userId', 'username'], Object.keys(profile.data).sort(), Object.keys(profile.data).sort().join(',') === 'userId,username');
  await status('case-insensitive username login', 'POST', '/v1/auth/login', { username: ' auditowner ', password: 'AuditPass123!' }, null, 200);
  await status('wrong password', 'POST', '/v1/auth/login', { username: 'AuditOwner', password: 'WrongPass123!' }, null, 401);
  await status('short password rejected', 'POST', '/v1/auth/register', { username: 'TooShort', password: '123' }, null, 422);
  const duplicate = await request('POST', '/v1/auth/register', { username: 'auditowner', password: 'Different123!' });
  check('BUG duplicate username must not claim account creation', '409 conflict',
    { status: duplicate.status, verification: duplicate.data?.emailVerificationRequired, hasTokens: Boolean(duplicate.data?.tokens) }, duplicate.status === 409);
  if (duplicate.data?.user?.userId) {
    const phantom = await request('GET', `/v1/users/${duplicate.data.user.userId}`);
    check('BUG registered user ID must exist', 200, phantom.status, phantom.status === 200);
  }
  await status('BUG oversized BCrypt password rejected as validation', 'POST', '/v1/auth/register', { username: 'LongPassword', password: 'a'.repeat(73) }, null, 422);
  await status('BUG multibyte BCrypt password rejected as validation', 'POST', '/v1/auth/register', { username: 'LongUnicode', password: '\u044f'.repeat(37) }, null, 422);
  await status('BUG changed password suffix must not authenticate', 'POST', '/v1/auth/login', { username: 'LongPassword', password: 'a'.repeat(72) + 'different-suffix' }, null, 401);
  await status('email registration stays pending', 'POST', '/v1/auth/register', { username: 'PendingEmail', email: 'audit@example.invalid', password: 'AuditPass123!' }, null, 201);
  await status('pending email username login blocked', 'POST', '/v1/auth/login', { username: 'PendingEmail', password: 'AuditPass123!' }, null, 403);
  await status('unknown recovery email neutral response', 'POST', '/v1/auth/password-recovery/request', { email: 'absent@example.invalid' }, null, 204);
  await status('invalid verification token', 'POST', '/v1/auth/email-verification/confirm', { token: 'invalid' }, null, 400);
  await status('invalid reset token', 'POST', '/v1/auth/password-recovery/confirm', { token: 'invalid', newPassword: 'NewPass123!' }, null, 400);
  const paths = ['/v1/media/movie/603', '/v1/media/tv/1399', '/v1/media/search/movies?query=matrix', '/v1/media/search/tv?query=dark',
    '/v1/media/discover/movies/popular', '/v1/media/discover/tv/popular', '/v1/media/discover/movies/trending?window=day',
    '/v1/media/discover/tv/trending?window=week', '/v1/media/movie/603/recommendations', '/v1/media/tv/1399/recommendations'];
  for (const path of paths) await status(`stub media ${path}`, 'GET', path, undefined, null, 200);
  await status('media cache second read', 'GET', '/v1/media/movie/603', undefined, null, 200);
  await status('invalid media ID on GET', 'GET', '/v1/media/movie/0', undefined, null, 422);
  await status('blank media query', 'GET', '/v1/media/search/movies?query=%20', undefined, null, 422);
  await status('invalid trending window', 'GET', '/v1/media/discover/movies/trending?window=month', undefined, null, 400);
  await status('admin sync forbidden to user', 'POST', '/v1/media/movie/603/sync', {}, token, 403);
  await status('OpenAPI forbidden to user', 'GET', '/v3/api-docs', undefined, token, 403);
  const reviewBody = { tmdbMovieId: 603, mediaType: 'MOVIE', rating: 8, reviewText: 'First line\nSecond line' };
  await status('anonymous review write rejected', 'POST', '/v1/reviews', reviewBody, null, 401);
  const review = await status('create multiline review', 'POST', '/v1/reviews', reviewBody, token, 201);
  const reviewId = review.data.reviewId;
  await status('read review', 'GET', `/v1/reviews/${reviewId}`, undefined, null, 200);
  const filtered = await status('filter reviews by user and media', 'GET', `/v1/reviews?userId=${owner.data.user.userId}&tmdbMovieId=603&mediaType=movie&sort=rating&order=desc`, undefined, null, 200);
  check('persisted rating and pagination', { rating: 8, total: 1 }, { rating: filtered.data.items[0]?.rating, total: filtered.data.meta.total }, filtered.data.items[0]?.rating === 8 && filtered.data.meta.total === 1);
  await status('duplicate review rejected', 'POST', '/v1/reviews', reviewBody, token, 409);
  await status('invalid high rating', 'POST', '/v1/reviews', { ...reviewBody, tmdbMovieId: 604, rating: 11 }, token, 422);
  await status('BUG negative TMDB ID rejected on create', 'POST', '/v1/reviews', { ...reviewBody, tmdbMovieId: -1 }, token, 422);
  await status('BUG zero TMDB ID rejected on create', 'POST', '/v1/reviews', { ...reviewBody, tmdbMovieId: 0 }, token, 422);
  const fractional = await request('POST', '/v1/reviews', { ...reviewBody, tmdbMovieId: 605, rating: 8.9 }, token);
  check('BUG fractional rating rejected instead of truncation', '400 or 422 without saving',
    { status: fractional.status, savedRating: fractional.data?.rating }, [400, 422].includes(fractional.status));
  await status('BUG multiline review can be edited', 'PATCH', `/v1/reviews/${reviewId}`, { reviewText: 'Updated first line\nUpdated second line' }, token, 200);
  await status('edit rating', 'PATCH', `/v1/reviews/${reviewId}`, { rating: 9 }, token, 200);
  await status('BUG lowercase mediaType accepted consistently with URL', 'POST', '/v1/reviews', { ...reviewBody, tmdbMovieId: 606, mediaType: 'tv' }, token, 201);
  const other = await status('second account', 'POST', '/v1/auth/register', { username: 'OtherUser', password: 'AuditPass123!' }, null, 201);
  const otherToken = other.data.tokens.accessToken;
  await status('ownership protects review update', 'PATCH', `/v1/reviews/${reviewId}`, { rating: 1 }, otherToken, 403);
  await status('ownership protects review deletion', 'DELETE', `/v1/reviews/${reviewId}`, undefined, otherToken, 403);
  const answer = await status('create answer', 'POST', `/v1/reviews/${reviewId}/answers`, { answerText: 'Audit answer' }, otherToken, 201);
  await status('list answers', 'GET', `/v1/reviews/${reviewId}/answers`, undefined, null, 200);
  await status('ownership protects answer update', 'PATCH', `/v1/answers/${answer.data.answerId}`, { answerText: 'Intrusion' }, token, 403);
  await status('answer owner can update', 'PATCH', `/v1/answers/${answer.data.answerId}`, { answerText: 'Updated answer' }, otherToken, 200);
  await status('delete review with answer', 'DELETE', `/v1/reviews/${reviewId}`, undefined, token, 204);
  await status('deleted review missing', 'GET', `/v1/reviews/${reviewId}`, undefined, null, 404);
  await status('answer cascade deletion', 'PATCH', `/v1/answers/${answer.data.answerId}`, { answerText: 'Deleted' }, otherToken, 404);
  await status('invalid review UUID', 'GET', '/v1/reviews/not-a-uuid', undefined, null, 400);
  await status('invalid pagination', 'GET', '/v1/reviews?limit=101', undefined, null, 422);
  await status('BUG unknown route is 404', 'GET', '/v1/does-not-exist', undefined, token, 404);
  await status('BUG unsupported method is 405', 'PUT', '/v1/reviews', {}, token, 405);
  for (let index = 0; index < 31; index++) await request('GET', '/v1/media/movie/603');
  await status('lowercase media route rate limited', 'GET', '/v1/media/movie/603', undefined, null, 429);
  await status('BUG uppercase media route must share rate limit', 'GET', '/v1/media/MOVIE/603', undefined, null, 429);
  const refreshed = await status('refresh rotates session', 'POST', '/v1/auth/refresh', { refreshToken: owner.data.tokens.refreshToken }, null, 200);
  await status('old access token invalid after refresh', 'GET', '/v1/me', undefined, token, 401);
  await status('rotated access token works', 'GET', '/v1/me', undefined, refreshed.data.accessToken, 200);
  await status('refresh replay rejected', 'POST', '/v1/auth/refresh', { refreshToken: owner.data.tokens.refreshToken }, null, 401);
  await status('replay compromises descendant session', 'GET', '/v1/me', undefined, refreshed.data.accessToken, 401);
  await status('logout', 'POST', '/v1/auth/logout', {}, otherToken, 204);
  await status('logout invalidates access', 'GET', '/v1/me', undefined, otherToken, 401);
  await stop(server); server = null;
  server = await start('http', true);
  check('HTTP provider without TMDB token fails at startup', 'configuration failure before accepting requests',
    { exitCode: server.child.exitCode, missingTokenMessage: Boolean(server.startupFailure) }, server.startupFailure);
} finally {
  if (server) await stop(server);
  const summary = { timestamp: new Date().toISOString(), environment: 'fresh packaged BE; loopback; H2 migrations + validate; stub TMDB / unconfigured HTTP; in-memory email',
    passed: results.filter(r => r.passed).length, failed: results.filter(r => !r.passed).length, results };
  await writeFile(resolve(root, 'docs/audit/http-results.json'), JSON.stringify(summary, null, 2) + '\n');
  console.log(`Audit: ${summary.passed} passed; ${summary.failed} failed`);
}
// Deliberately nonzero when acceptance checks discover defects.
process.exitCode = results.some(result => !result.passed) ? 1 : 0;
