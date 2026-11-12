/**
 * Эхо — административная панель.
 * Одна страница без сборки. Контракт API — в BACKEND.md.
 * Если эндпоинт ещё не реализован на бэкенде (404/405/501), раздел показывает это,
 * остальные разделы продолжают работать.
 */
import { createDemoApi } from './demo.js';

const CFG = window.ECHO_ADMIN_CONFIG || {};
const params = new URLSearchParams(location.search);
const DEMO = params.has('demo') || !String(CFG.apiBase ?? '').trim();
const BASE = String(CFG.apiBase ?? '').replace(/\/+$/, '');
const TOKEN_KEY = 'echo-admin:tokens:v1';

const PARAMS = [
  ['dramaturgy_structure', 'Драматургия и структура'],
  ['characters_performance', 'Персонажи и исполнение'],
  ['visual_language', 'Визуальный язык'],
  ['sound_music', 'Звук и музыка'],
  ['impression', 'Впечатление'],
];
const REVIEW_MIN = 150, REVIEW_MAX = 10000;
const REJECT_REASONS = [
  ['TOO_SHORT', 'Короче 150 символов или не по существу'],
  ['INSULT', 'Оскорбления в адрес людей'],
  ['PROFANITY', 'Нецензурная лексика'],
  ['SPOILER', 'Спойлеры не отмечены'],
  ['OFF_TOPIC', 'Рецензия не о фильме'],
  ['SPAM', 'Спам или реклама'],
  ['OTHER', 'Другое (опишите в комментарии)'],
];
const REASON_LABEL = Object.fromEntries(REJECT_REASONS);
// Подсказка модератору, а не автоматическое решение: только частые корни.
const FLAG_RE = /(х[уy][йеёияю]\p{L}*|п[иi]зд\p{L}*|\p{L}*[её]б[ауаелнт]\p{L}*|бля\p{L}*|мудак\p{L}*|пид[оа]р\p{L}*|долбо\p{L}*|дебил\p{L}*|идиот\p{L}*|урод\p{L}*|тупиц\p{L}*|сук[аиу])/giu;
const ACTION_LABEL = {
  REVIEW_PUBLISHED: 'Опубликовал рецензию', REVIEW_REJECTED: 'Отклонил рецензию', REVIEW_UNPUBLISHED: 'Снял рецензию с публикации',
  USER_CHECKED: 'Проверил пользователя', USER_BLOCKED: 'Заблокировал пользователя', USER_UNBLOCKED: 'Разблокировал пользователя',
  USER_RENAMED: 'Сменил ник', USER_MEDIA_ON: 'Выдал статус «Медиа»', USER_MEDIA_OFF: 'Снял статус «Медиа»', USER_ROLE: 'Сменил роль',
  AUTHOR_VERIFIED: 'Верифицировал автора', AUTHOR_REJECTED: 'Отклонил заявку автора', AUTHOR_INFO_REQUESTED: 'Запросил подтверждение у автора',
};

/* ── API ───────────────────────────────────────────────────────────── */
class ApiError extends Error {
  constructor(status, method, path, message) { super(message || `HTTP ${status}`); Object.assign(this, { status, method, path }); }
  get notImplemented() { return [404, 405, 501].includes(this.status) || this.status === 0 && DEMO; }
}
let tokens = null;
try { tokens = JSON.parse(sessionStorage.getItem(TOKEN_KEY) || 'null'); } catch { tokens = null; }
const saveTokens = v => { tokens = v; try { v ? sessionStorage.setItem(TOKEN_KEY, JSON.stringify(v)) : sessionStorage.removeItem(TOKEN_KEY); } catch {} };
const demoApi = DEMO ? createDemoApi() : null;

async function raw(method, path, body, auth = true) {
  if (DEMO) return demoApi(method, path, body);
  let res;
  try {
    res = await fetch(BASE + path, {
      method,
      headers: { ...(body !== undefined ? { 'Content-Type': 'application/json' } : {}), ...(auth && tokens?.accessToken ? { Authorization: `Bearer ${tokens.accessToken}` } : {}) },
      body: body === undefined ? undefined : JSON.stringify(body),
    });
  } catch { throw new ApiError(0, method, path, 'Сервер недоступен'); }
  if (res.status === 204) return null;
  let data = null; const text = await res.text();
  try { data = text ? JSON.parse(text) : null; } catch { data = null; }
  if (!res.ok) throw new ApiError(res.status, method, path, data?.message || data?.error?.message || `HTTP ${res.status}`);
  return data;
}
let refreshing = null;
async function api(method, path, body) {
  try { return await raw(method, path, body); }
  catch (e) {
    if (DEMO || e.status !== 401 || !tokens?.refreshToken) throw e;
    refreshing ||= raw('POST', '/v1/auth/refresh', { refreshToken: tokens.refreshToken }, false)
      .then(saveTokens).catch(err => { saveTokens(null); state.me = null; render(); throw err; })
      .finally(() => { refreshing = null; });
    await refreshing;
    return raw(method, path, body);
  }
}
const q = o => '?' + new URLSearchParams(Object.entries(o).filter(([, v]) => v !== undefined && v !== null)).toString();

/* ── Состояние ─────────────────────────────────────────────────────── */
const state = {
  me: null, booting: true, loginError: '',
  page: params.get('page') || 'overview',
  tabs: { reviews: 'PENDING', users: 'NEW', authors: 'PENDING' },
  lists: {}, selected: {}, details: {}, stats: null, audit: null, users: {},
};
const listKey = page => `${page}:${state.tabs[page]}`;

/* ── Утилиты ───────────────────────────────────────────────────────── */
const esc = v => String(v ?? '').replace(/[&<>"']/g, c => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[c]));
const fmtDate = v => v ? new Date(v).toLocaleString('ru-RU', { day: 'numeric', month: 'short', hour: '2-digit', minute: '2-digit' }) : '—';
const fmtDay = v => v ? new Date(v).toLocaleDateString('ru-RU', { day: 'numeric', month: 'long', year: 'numeric' }) : '—';
const ago = v => {
  if (!v) return '—'; const h = Math.max(0, (Date.now() - new Date(v)) / 36e5);
  return h < 1 ? 'меньше часа' : h < 24 ? `${Math.floor(h)} ч` : `${Math.floor(h / 24)} дн`;
};
const daysSince = v => v ? (Date.now() - new Date(v)) / 864e5 : Infinity;
const initials = s => esc(String(s || '?').trim().slice(0, 2).toUpperCase());
function toast(msg) { const t = document.getElementById('toast'); t.textContent = msg; t.classList.add('show'); clearTimeout(toast.t); toast.t = setTimeout(() => t.classList.remove('show'), 2600); }
function stateBox(err, retry) {
  if (err === 'loading') return `<div class="state"><div class="spinner" aria-hidden="true"></div><span>Загрузка…</span></div>`;
  if (err instanceof ApiError && err.notImplemented) return `<div class="state"><b>Ещё не подключено на бэкенде</b><span>Нужен эндпоинт <code>${esc(err.method)} ${esc(err.path.split('?')[0])}</code> — см. BACKEND.md</span></div>`;
  return `<div class="state error"><b>Не удалось загрузить</b><span>${esc(err?.message || err)}</span>${retry ? `<button class="btn" data-retry="${esc(retry)}">Повторить</button>` : ''}</div>`;
}

async function userInfo(userId) {
  if (!userId) return null;
  if (state.users[userId]) return state.users[userId];
  try { state.users[userId] = await api('GET', `/v2/admin/users/${userId}`); }
  catch { try { state.users[userId] = await api('GET', `/v1/users/${userId}`); } catch { state.users[userId] = { userId, username: 'пользователь' }; } }
  return state.users[userId];
}

/* ── Загрузка данных ───────────────────────────────────────────────── */
const LOADERS = {
  'reviews:PENDING': () => api('GET', '/v2/reviews/moderation' + q({ limit: 100, offset: 0 })),
  'reviews:PUBLISHED': () => api('GET', '/v2/admin/reviews' + q({ status: 'PUBLISHED', limit: 100, offset: 0 })),
  'reviews:REJECTED': () => api('GET', '/v2/admin/reviews' + q({ status: 'REJECTED', limit: 100, offset: 0 })),
  'users:NEW': () => api('GET', '/v2/admin/users' + q({ state: 'NEW', limit: 100, offset: 0 })),
  'users:CHECKED': () => api('GET', '/v2/admin/users' + q({ state: 'CHECKED', limit: 100, offset: 0 })),
  'users:BLOCKED': () => api('GET', '/v2/admin/users' + q({ state: 'BLOCKED', limit: 100, offset: 0 })),
  'authors:PENDING': () => api('GET', '/v2/admin/authors' + q({ status: 'PENDING', limit: 100, offset: 0 })),
  'authors:VERIFIED': () => api('GET', '/v2/admin/authors' + q({ status: 'VERIFIED', limit: 100, offset: 0 })),
  'authors:REJECTED': () => api('GET', '/v2/admin/authors' + q({ status: 'REJECTED', limit: 100, offset: 0 })),
};
const idOf = { reviews: x => x.reviewId, users: x => x.userId, authors: x => x.applicationId };

async function loadList(key, force = false) {
  if (!force && state.lists[key] && state.lists[key] !== 'loading' && !(state.lists[key] instanceof Error)) return;
  state.lists[key] = 'loading'; render();
  try {
    const data = await LOADERS[key]();
    state.lists[key] = { items: data?.items || [], total: data?.meta?.total ?? data?.items?.length ?? 0 };
    if (key.startsWith('reviews:')) await Promise.all(state.lists[key].items.map(r => userInfo(r.userId)));
    const page = key.split(':')[0];
    const items = state.lists[key].items;
    if (!items.some(x => idOf[page](x) === state.selected[key])) state.selected[key] = items[0] ? idOf[page](items[0]) : null;
    if (page === 'users' && state.selected[key]) loadUserDetail(state.selected[key]);
  } catch (e) { state.lists[key] = e; }
  render();
}
async function loadStats() {
  try { state.stats = await api('GET', '/v2/admin/stats'); }
  catch (e) {
    // Без /v2/admin/stats считаем то, что можно, из существующей очереди.
    try {
      const qd = await api('GET', '/v2/reviews/moderation' + q({ limit: 100, offset: 0 }));
      state.stats = { pendingReviews: qd?.meta?.total ?? qd?.items?.length ?? 0, oldestPendingAt: qd?.items?.[0]?.createdAt || null, partial: true };
    } catch (e2) { state.stats = e2; }
  }
  render();
}
async function loadAudit(force) {
  if (state.audit && !force && !(state.audit instanceof Error)) return;
  state.audit = 'loading'; render();
  try { state.audit = (await api('GET', '/v2/admin/audit' + q({ limit: 100, offset: 0 })))?.items || []; }
  catch (e) { state.audit = e; }
  render();
}
async function loadUserDetail(id, force) {
  if (!id || (state.details[id] && !force)) { render(); return; }
  state.details[id] = 'loading'; render();
  try { state.details[id] = await api('GET', `/v2/admin/users/${id}`); } catch (e) { state.details[id] = e; }
  render();
}
function invalidate(...prefixes) {
  for (const k of Object.keys(state.lists)) if (prefixes.some(p => k.startsWith(p))) delete state.lists[k];
  state.audit = null; loadStats();
}

/* ── Отрисовка ─────────────────────────────────────────────────────── */
const app = document.getElementById('app');
function render() {
  if (state.booting) { app.innerHTML = `<div class="login">${stateBox('loading')}</div>`; return; }
  if (!state.me) { renderLogin(); return; }
  const s = state.stats && !(state.stats instanceof Error) ? state.stats : {};
  const nav = [['overview', 'Обзор'], ['reviews', 'Рецензии', s.pendingReviews], ['users', 'Пользователи', s.newUsers], ['authors', 'Авторы', s.pendingAuthors], ['audit', 'Журнал действий']];
  app.innerHTML = `<div class="shell">
    <aside class="side">
      <div class="brand"><span class="brand-dot" aria-hidden="true"></span>Эхо · админ</div>
      <nav class="nav" aria-label="Разделы">${nav.map(([id, label, n]) => `<button data-page="${id}" ${state.page === id ? 'aria-current="page"' : ''}><span>${label}</span>${n ? `<span class="count hot" aria-label="${n} ждут">${n}</span>` : ''}</button>`).join('')}</nav>
      <div class="side-foot">
        ${DEMO ? '<span class="demo-badge">Демо-данные</span>' : ''}
        <span class="muted">${esc(state.me.username)} · ${esc(roleName(state.me.role))}</span>
        <button class="btn ghost" data-logout>Выйти</button>
      </div>
    </aside>
    <main class="main" id="main">${renderPage()}</main>
  </div>`;
}
const roleName = r => ({ ADMIN: 'администратор', MODERATOR: 'модератор', USER: 'пользователь' }[r] || r || '—');

function renderLogin() {
  app.innerHTML = `<div class="login"><form class="login-card" id="loginForm" novalidate>
    <div class="brand"><span class="brand-dot" aria-hidden="true"></span>Эхо · администрирование</div>
    <p class="muted small">Вход для администраторов и модераторов. Используйте аккаунт сайта с ролью ADMIN или MODERATOR.</p>
    ${DEMO ? '<p class="small"><span class="demo-badge">Демо</span> Введите любые логин и пароль.</p>' : ''}
    <div class="field"><label for="lgUser">Логин</label><input class="input" id="lgUser" autocomplete="username" required></div>
    <div class="field"><label for="lgPass">Пароль</label><input class="input" id="lgPass" type="password" autocomplete="current-password" required></div>
    ${state.loginError ? `<p class="error-text" role="alert">${esc(state.loginError)}</p>` : ''}
    <button class="btn primary" type="submit">Войти</button>
  </form></div>`;
  document.getElementById('lgUser').focus();
}

function renderPage() {
  switch (state.page) {
    case 'reviews': return renderReviews();
    case 'users': return renderUsers();
    case 'authors': return renderAuthors();
    case 'audit': return renderAudit();
    default: return renderOverview();
  }
}

function renderOverview() {
  const s = state.stats;
  if (!s) return `<div class="page-head"><h1>Обзор</h1></div>${stateBox('loading')}`;
  if (s instanceof Error) return `<div class="page-head"><h1>Обзор</h1></div>${stateBox(s, 'stats')}`;
  const v = x => x ?? '—';
  const oldestH = s.oldestPendingAt ? (Date.now() - new Date(s.oldestPendingAt)) / 36e5 : 0;
  return `<div class="page-head"><h1>Обзор</h1>${s.partial ? '<span class="muted small">Часть счётчиков появится после <code>GET /v2/admin/stats</code></span>' : ''}</div>
  <div class="stats">
    <button class="stat ${s.pendingReviews ? 'warn' : ''}" data-page="reviews"><span class="label">Рецензий в очереди</span><span class="num">${v(s.pendingReviews)}</span><span class="note">${s.oldestPendingAt ? `самая старая ждёт ${ago(s.oldestPendingAt)}` : 'очередь пуста'}</span></button>
    <button class="stat ${s.newUsers ? 'warn' : ''}" data-page="users"><span class="label">Новых пользователей на проверке</span><span class="num">${v(s.newUsers)}</span><span class="note">зарегистрировались, ещё не просмотрены</span></button>
    <button class="stat ${s.pendingAuthors ? 'warn' : ''}" data-page="authors"><span class="label">Заявок авторов</span><span class="num">${v(s.pendingAuthors)}</span><span class="note">ждут верификации</span></button>
    <div class="stat"><span class="label">Опубликовано за 7 дней</span><span class="num">${v(s.published7d)}</span><span class="note">отклонено: ${v(s.rejected7d)}</span></div>
  </div>
  ${oldestH > 24 ? `<div class="state error" style="margin-bottom:16px"><b>Рецензия ждёт модерации больше суток</b><span>Автор не видит её на сайте, пока она в очереди.</span><button class="btn primary" data-page="reviews">Открыть очередь</button></div>` : ''}
  <p class="muted small">Горячие клавиши в очереди рецензий: <kbd>A</kbd> — опубликовать, <kbd>R</kbd> — отклонить, <kbd>J</kbd>/<kbd>K</kbd> — следующая и предыдущая.</p>`;
}

function tabsHtml(page, tabs) {
  return `<div class="tabs" role="tablist">${tabs.map(([id, label]) => {
    const l = state.lists[`${page}:${id}`];
    const n = l && l.items ? ` <span class="count">${l.total}</span>` : '';
    return `<button role="tab" data-tab="${page}:${id}" aria-selected="${state.tabs[page] === id}">${label}${n}</button>`;
  }).join('')}</div>`;
}
function listOr(key, renderFn, empty) {
  const l = state.lists[key];
  if (!l || l === 'loading') return stateBox('loading');
  if (l instanceof Error) return stateBox(l, key);
  if (!l.items.length) return `<div class="state"><b>${empty}</b></div>`;
  return renderFn(l.items);
}

/* Рецензии */
function textChecks(r) {
  const len = (r.content || '').length;
  const flags = [...new Set(((r.title || '') + ' ' + (r.content || '')).match(FLAG_RE) || [])];
  const author = state.users[r.userId];
  const checks = [
    len < REVIEW_MIN ? ['fail', '✕', `Короче минимума: ${len} из ${REVIEW_MIN} символов`]
      : len > REVIEW_MAX ? ['fail', '✕', `Длиннее максимума: ${len} символов`] : ['ok', '✓', `Длина в норме: ${len} символов`],
    flags.length ? ['warn', '!', `Проверьте лексику: ${flags.slice(0, 5).map(esc).join(', ')}`] : ['ok', '✓', 'Подозрительных слов не найдено'],
    /спойлер|в финале|в конце фильм|умира|погиба|оказывается/i.test(r.content || '') ? ['warn', '!', 'Возможен спойлер: проверьте, отмечен ли он'] : null,
    author?.createdAt && daysSince(author.createdAt) < 2 ? ['warn', '!', `Автор зарегистрировался ${ago(author.createdAt)} назад`] : null,
  ].filter(Boolean);
  return { checks, flags };
}
function highlight(text) {
  return esc(text).replace(FLAG_RE, m => `<mark title="Проверьте лексику">${m}</mark>`);
}
function renderReviews() {
  const key = listKey('reviews');
  const head = `<div class="page-head"><h1>Рецензии</h1><span class="muted small">Правила: ${REVIEW_MIN}–${REVIEW_MAX.toLocaleString('ru-RU')} символов, без оскорблений и мата, спойлеры отмечены</span></div>`;
  const tabs = tabsHtml('reviews', [['PENDING', 'Очередь'], ['PUBLISHED', 'Опубликованные'], ['REJECTED', 'Отклонённые']]);
  const body = listOr(key, items => {
    const sel = items.find(r => r.reviewId === state.selected[key]) || items[0];
    return `<div class="split"><div class="list" role="listbox" aria-label="Рецензии">${items.map(r => {
      const u = state.users[r.userId];
      return `<button class="item" role="option" data-select="${key}|${r.reviewId}" aria-current="${r.reviewId === sel.reviewId}">
        <span class="item-top"><span>${esc(r.title || 'Без заголовка')}</span><span>${r.rating?.finalScore ?? r.legacyRating ?? '—'}</span></span>
        <span class="item-meta"><span>${esc(r.media?.title || 'Фильм')}</span><span>@${esc(u?.username || '…')}</span><span>${state.tabs.reviews === 'PENDING' ? 'ждёт ' + ago(r.createdAt) : fmtDate(r.publishedAt || r.updatedAt)}</span></span>
      </button>`;
    }).join('')}</div>${reviewDetail(sel)}</div>`;
  }, state.tabs.reviews === 'PENDING' ? 'Очередь пуста — всё проверено' : 'Пока пусто');
  return head + tabs + body;
}
function reviewDetail(r) {
  const u = state.users[r.userId] || {};
  const { checks } = textChecks(r);
  const p = r.rating?.parameters || {};
  const tab = state.tabs.reviews;
  return `<article class="panel" aria-label="Рецензия">
    <div class="panel-head">
      ${r.media?.posterUrl ? `<img class="poster" src="${esc(r.media.posterUrl)}" alt="">` : `<div class="poster" aria-hidden="true">${initials(r.media?.title)}</div>`}
      <div style="display:grid;gap:6px;min-width:0">
        <h2>${esc(r.title || 'Без заголовка')}</h2>
        <span class="muted small">${esc(r.media?.title || 'Фильм')}${r.media?.director ? ' · ' + esc(r.media.director) : ''} · TMDB ${esc(r.media?.tmdbId ?? '—')}</span>
        <div class="tags">
          <button class="tag info" data-open-user="${esc(r.userId)}" title="Открыть пользователя">@${esc(u.username || '…')}</button>
          ${u.createdAt ? `<span class="tag">на сайте с ${fmtDay(u.createdAt)}</span>` : ''}
          ${r.status === 'PUBLISHED' ? '<span class="tag good">опубликована</span>' : r.status === 'REJECTED' ? '<span class="tag bad">отклонена</span>' : '<span class="tag warn">на модерации</span>'}
          <span class="tag">${r.likes ?? 0} лайков</span>
        </div>
      </div>
    </div>
    <div class="scores">${PARAMS.map(([code, name]) => `<div class="score"><span>${name}</span><b>${p[code] ?? '—'}</b></div>`).join('')}<div class="score total"><span>Итог</span><b>${r.rating?.finalScore ?? r.legacyRating ?? '—'}</b></div></div>
    <div class="checks" aria-label="Автопроверка">${checks.map(([cls, sym, text]) => `<div class="check ${cls}"><b aria-hidden="true">${sym}</b><span>${text}</span></div>`).join('')}</div>
    <div class="review-text">${highlight(r.content || '')}</div>
    ${r.rejectReason ? `<p class="small"><b>Причина отклонения:</b> ${esc(REASON_LABEL[r.rejectReason] || r.rejectReason)}${r.rejectComment ? ' — ' + esc(r.rejectComment) : ''}</p>` : ''}
    <div class="actions">
      ${tab === 'PENDING' ? `<button class="btn good" data-act="publish" data-id="${r.reviewId}">Опубликовать <kbd>A</kbd></button><button class="btn bad" data-act="reject" data-id="${r.reviewId}">Отклонить <kbd>R</kbd></button><span class="hint">J / K — следующая / предыдущая</span>` : ''}
      ${tab === 'PUBLISHED' ? `<button class="btn bad" data-act="unpublish" data-id="${r.reviewId}">Снять с публикации</button>` : ''}
      ${tab === 'REJECTED' ? `<button class="btn good" data-act="publish" data-id="${r.reviewId}">Всё-таки опубликовать</button>` : ''}
      <span class="muted small">Отправлена ${fmtDate(r.createdAt)}</span>
    </div>
  </article>`;
}

/* Пользователи */
function renderUsers() {
  const key = listKey('users');
  const head = `<div class="page-head"><h1>Пользователи</h1><span class="muted small">Регистрация проходит без проверки. Здесь новые аккаунты просматриваются после регистрации.</span></div>`;
  const tabs = tabsHtml('users', [['NEW', 'Новые'], ['CHECKED', 'Проверенные'], ['BLOCKED', 'Заблокированные']]);
  const body = listOr(key, items => {
    const selId = state.selected[key] || items[0].userId;
    if (!state.details[selId]) queueMicrotask(() => loadUserDetail(selId));
    return `<div class="split"><div class="list" role="listbox" aria-label="Пользователи">${items.map(u => `
      <button class="item" role="option" data-select="${key}|${u.userId}" aria-current="${u.userId === selId}">
        <span class="item-top"><span>@${esc(u.username)}</span><span class="muted small">${fmtDate(u.createdAt)}</span></span>
        <span class="item-meta"><span>оценок ${u.stats?.ratings ?? 0}</span><span>рецензий ${u.stats?.reviews ?? 0}</span>${u.media ? '<span>Медиа</span>' : ''}${u.role && u.role !== 'USER' ? `<span>${roleName(u.role)}</span>` : ''}</span>
      </button>`).join('')}</div>${userDetail(selId, items.find(u => u.userId === selId))}</div>`;
  }, state.tabs.users === 'NEW' ? 'Новых пользователей нет' : 'Пока пусто');
  return head + tabs + body;
}
function userDetail(id, brief) {
  const d = state.details[id];
  if (d === 'loading' || !d) return `<div class="panel">${stateBox('loading')}</div>`;
  if (d instanceof Error) return `<div class="panel">${stateBox(d)}</div>`;
  const u = { ...brief, ...d };
  const isAdmin = state.me.role === 'ADMIN';
  return `<article class="panel" aria-label="Пользователь">
    <div class="panel-head">
      ${u.avatarUrl ? `<img class="avatar" src="${esc(u.avatarUrl)}" alt="">` : `<div class="avatar" aria-hidden="true">${initials(u.username)}</div>`}
      <div style="display:grid;gap:6px;min-width:0">
        <h2>@${esc(u.username)}</h2>
        <div class="tags">
          ${u.isActive === false ? '<span class="tag bad">заблокирован</span>' : u.checkedAt ? '<span class="tag good">проверен</span>' : '<span class="tag warn">не проверен</span>'}
          <span class="tag">${roleName(u.role)}</span>${u.media ? '<span class="tag info">Медиа</span>' : ''}${u.author ? '<span class="tag author">Автор</span>' : ''}
        </div>
      </div>
    </div>
    <dl class="kv">
      <dt>Зарегистрирован</dt><dd>${fmtDate(u.createdAt)} (${ago(u.createdAt)} назад)</dd>
      <dt>Email</dt><dd>${u.email ? esc(u.email) : '<span class="muted">не указан</span>'}</dd>
      <dt>Активность</dt><dd>оценок ${u.stats?.ratings ?? 0} · рецензий ${u.stats?.reviews ?? 0} · получено лайков ${u.stats?.receivedLikes ?? 0}</dd>
      ${u.checkedAt ? `<dt>Проверил</dt><dd>${esc(u.checkedBy || '—')}, ${fmtDate(u.checkedAt)}</dd>` : ''}
      ${u.isActive === false ? `<dt>Причина блокировки</dt><dd>${esc(u.blockedReason || '—')}</dd>` : ''}
      <dt>О себе</dt><dd>${u.bio ? esc(u.bio) : '<span class="muted">пусто</span>'}</dd>
    </dl>
    ${(u.recentReviews || []).length ? `<div class="mini-list"><h3>Последние рецензии</h3>${u.recentReviews.map(r => `<div class="mini"><span>${esc(r.title || 'Без заголовка')} · <span class="muted">${esc(r.media?.title || '')}</span></span><span class="muted">${r.status === 'PUBLISHED' ? 'опубликована' : r.status === 'REJECTED' ? 'отклонена' : 'на модерации'}</span></div>`).join('')}</div>` : ''}
    <div class="actions">
      ${!u.checkedAt && u.isActive !== false ? `<button class="btn good" data-act="check" data-id="${u.userId}">Проверен, всё в порядке</button>` : ''}
      <button class="btn" data-act="rename" data-id="${u.userId}">Сменить ник</button>
      <button class="btn" data-act="media" data-id="${u.userId}" data-on="${u.media ? 0 : 1}">${u.media ? 'Снять «Медиа»' : 'Выдать «Медиа»'}</button>
      ${isAdmin && u.userId !== state.me.userId ? `<button class="btn" data-act="role" data-id="${u.userId}">${u.role === 'MODERATOR' ? 'Убрать роль модератора' : 'Сделать модератором'}</button>` : ''}
      ${u.isActive === false ? `<button class="btn" data-act="unblock" data-id="${u.userId}">Разблокировать</button>` : `<button class="btn bad" data-act="block" data-id="${u.userId}">Заблокировать</button>`}
    </div>
  </article>`;
}

/* Авторы */
function renderAuthors() {
  const key = listKey('authors');
  const head = `<div class="page-head"><h1>Авторы</h1><span class="muted small">Заявки создателей фильмов. Верифицированный автор получает авторский профиль и комментарии.</span></div>`;
  const tabs = tabsHtml('authors', [['PENDING', 'Заявки'], ['VERIFIED', 'Верифицированные'], ['REJECTED', 'Отклонённые']]);
  const body = listOr(key, items => {
    const sel = items.find(a => a.applicationId === state.selected[key]) || items[0];
    return `<div class="split"><div class="list" role="listbox" aria-label="Авторы">${items.map(a => `
      <button class="item" role="option" data-select="${key}|${a.applicationId}" aria-current="${a.applicationId === sel.applicationId}">
        <span class="item-top"><span>${esc(a.name)}</span><span class="muted small">${fmtDate(a.createdAt)}</span></span>
        <span class="item-meta"><span>${esc(a.specialty || '—')}</span><span>@${esc(a.username || '—')}</span></span>
      </button>`).join('')}</div>${authorDetail(sel)}</div>`;
  }, state.tabs.authors === 'PENDING' ? 'Новых заявок нет' : 'Пока пусто');
  return head + tabs + body;
}
function authorDetail(a) {
  const st = state.tabs.authors;
  return `<article class="panel" aria-label="Заявка автора">
    <div class="panel-head"><div class="avatar" aria-hidden="true" style="color:var(--amber)">${initials(a.name)}</div>
      <div style="display:grid;gap:6px"><h2>${esc(a.name)}</h2><div class="tags"><span class="tag author">${esc(a.specialty || 'специальность не указана')}</span>
      ${a.status === 'VERIFIED' ? '<span class="tag good">верифицирован</span>' : a.status === 'REJECTED' ? '<span class="tag bad">отклонён</span>' : '<span class="tag warn">ждёт проверки</span>'}</div></div></div>
    <dl class="kv">
      <dt>Логин</dt><dd>@${esc(a.username || '—')}</dd>
      <dt>Подана</dt><dd>${fmtDate(a.createdAt)}</dd>
      <dt>Подтверждение</dt><dd>${(a.links || []).length ? a.links.map(l => `<a href="${esc(l)}" target="_blank" rel="noopener noreferrer">${esc(l)}</a>`).join('<br>') : '<span class="muted">ссылок нет — запросите подтверждение</span>'}</dd>
      <dt>Фильмы</dt><dd>${(a.films || []).length ? a.films.map(esc).join(', ') : '<span class="muted">не указаны</span>'}</dd>
      ${a.note ? `<dt>Комментарий</dt><dd>${esc(a.note)}</dd>` : ''}
      ${a.decidedAt ? `<dt>Решение</dt><dd>${esc(a.decidedBy || '—')}, ${fmtDate(a.decidedAt)}${a.reason ? ' — ' + esc(a.reason) : ''}</dd>` : ''}
    </dl>
    <div class="actions">
      ${st !== 'VERIFIED' ? `<button class="btn good" data-act="verify" data-id="${a.applicationId}">Верифицировать</button>` : ''}
      ${st === 'PENDING' ? `<button class="btn" data-act="info" data-id="${a.applicationId}">Запросить подтверждение</button><button class="btn bad" data-act="areject" data-id="${a.applicationId}">Отклонить</button>` : ''}
    </div>
    <p class="muted small">Проверьте, что человек действительно работал над указанными фильмами: титры на Кинопоиске или TMDB, портфолио, связь через студию.</p>
  </article>`;
}

/* Журнал */
function renderAudit() {
  const a = state.audit;
  const head = `<div class="page-head"><h1>Журнал действий</h1><span class="muted small">Кто и какое решение принял</span></div>`;
  if (!a || a === 'loading') return head + stateBox('loading');
  if (a instanceof Error) return head + stateBox(a, 'audit');
  if (!a.length) return head + '<div class="state"><b>Действий пока нет</b></div>';
  return head + `<div class="panel" style="overflow-x:auto"><table class="table"><thead><tr><th>Когда</th><th>Кто</th><th>Действие</th><th>Над чем</th><th>Причина</th></tr></thead><tbody>${a.map(x => `
    <tr><td>${fmtDate(x.at)}</td><td>@${esc(x.actor?.username || '—')}</td><td>${esc(ACTION_LABEL[x.action] || x.action)}</td><td>${esc(x.targetLabel || x.targetId || '—')}</td><td>${esc(REASON_LABEL[x.reason] || x.reason || '')}</td></tr>`).join('')}</tbody></table></div>`;
}

/* ── Диалоги ───────────────────────────────────────────────────────── */
function dialog({ title, text, reasons, input, inputLabel, inputValue = '', confirm = 'Подтвердить', danger }) {
  return new Promise(resolve => {
    const d = document.createElement('dialog');
    d.innerHTML = `<form method="dialog" class="dialog-body">
      <h2>${esc(title)}</h2>${text ? `<p class="muted small">${esc(text)}</p>` : ''}
      ${reasons ? `<fieldset class="reasons" style="border:0;padding:0;margin:0"><legend class="small muted" style="margin-bottom:6px">Причина</legend>${reasons.map(([id, l], i) => `<label><input type="radio" name="reason" value="${id}" ${i === 0 ? 'checked' : ''}> ${esc(l)}</label>`).join('')}</fieldset>` : ''}
      ${input ? `<div class="field"><label for="dlgInput">${esc(inputLabel || 'Комментарий')}</label>${input === 'text' ? `<input class="input" id="dlgInput" value="${esc(inputValue)}">` : `<textarea class="input" id="dlgInput">${esc(inputValue)}</textarea>`}</div>` : ''}
      <p class="error-text hidden" id="dlgErr"></p>
      <div class="actions"><button class="btn ${danger ? 'bad' : 'primary'}" value="ok">${esc(confirm)}</button><button class="btn ghost" value="cancel" formnovalidate>Отмена</button></div>
    </form>`;
    document.body.append(d);
    d.addEventListener('close', () => {
      const ok = d.returnValue === 'ok';
      const out = ok ? { reason: d.querySelector('input[name=reason]:checked')?.value, comment: d.querySelector('#dlgInput')?.value?.trim() || '' } : null;
      d.remove(); resolve(out);
    });
    d.querySelector('form').addEventListener('submit', e => {
      const sub = e.submitter?.value; if (sub !== 'ok') return;
      const reason = d.querySelector('input[name=reason]:checked')?.value;
      const val = d.querySelector('#dlgInput')?.value?.trim();
      const need = input && (reason === 'OTHER' || !reasons) && !val;
      if (need) { e.preventDefault(); const er = d.querySelector('#dlgErr'); er.textContent = 'Заполните поле'; er.classList.remove('hidden'); }
    });
    d.showModal();
  });
}

/* ── Действия ──────────────────────────────────────────────────────── */
async function run(fn, okMsg) {
  try { await fn(); toast(okMsg); }
  catch (e) { toast(e instanceof ApiError && e.notImplemented ? `Не подключено на бэкенде: ${e.method} ${e.path}` : `Ошибка: ${e.message}`); }
}
function nextSelected(key, id) {
  const l = state.lists[key]; if (!l?.items) return;
  const page = key.split(':')[0];
  const i = l.items.findIndex(x => idOf[page](x) === id);
  const next = l.items[i + 1] || l.items[i - 1];
  state.selected[key] = next ? idOf[page](next) : null;
}
const ACTIONS = {
  async publish(id) {
    const key = listKey('reviews'); nextSelected(key, id);
    await run(() => api('POST', `/v2/reviews/${id}/publish`), 'Рецензия опубликована');
    invalidate('reviews:'); loadList(listKey('reviews'));
  },
  async reject(id) {
    const r = await dialog({ title: 'Отклонить рецензию', text: 'Автор увидит причину. Рецензия останется у него в черновиках.', reasons: REJECT_REASONS, input: 'area', inputLabel: 'Комментарий для автора (обязателен для «Другое»)', confirm: 'Отклонить', danger: true });
    if (!r) return;
    nextSelected(listKey('reviews'), id);
    await run(() => api('POST', `/v2/admin/reviews/${id}/reject`, { reason: r.reason, comment: r.comment }), 'Рецензия отклонена');
    invalidate('reviews:'); loadList(listKey('reviews'));
  },
  async unpublish(id) {
    const r = await dialog({ title: 'Снять рецензию с публикации', text: 'Рецензия пропадёт с сайта. Используйте, если нарушение заметили после публикации.', reasons: REJECT_REASONS, input: 'area', inputLabel: 'Комментарий для автора', confirm: 'Снять', danger: true });
    if (!r) return;
    await run(() => api('POST', `/v2/admin/reviews/${id}/unpublish`, { reason: r.reason, comment: r.comment }), 'Рецензия снята с публикации');
    invalidate('reviews:'); loadList(listKey('reviews'));
  },
  async check(id) {
    nextSelected(listKey('users'), id);
    await run(() => api('POST', `/v2/admin/users/${id}/check`), 'Отмечен как проверенный');
    delete state.details[id]; invalidate('users:'); loadList(listKey('users'));
  },
  async block(id) {
    const r = await dialog({ title: 'Заблокировать пользователя', text: 'Пользователь не сможет войти. Его опубликованные рецензии останутся, пока вы их не снимете.', input: 'area', inputLabel: 'Причина блокировки', confirm: 'Заблокировать', danger: true });
    if (!r) return;
    await run(() => api('POST', `/v2/admin/users/${id}/block`, { reason: r.comment }), 'Пользователь заблокирован');
    delete state.details[id]; invalidate('users:'); loadList(listKey('users'));
  },
  async unblock(id) {
    await run(() => api('POST', `/v2/admin/users/${id}/unblock`), 'Пользователь разблокирован');
    delete state.details[id]; invalidate('users:'); loadList(listKey('users'));
  },
  async rename(id) {
    const cur = state.details[id]?.username || '';
    const r = await dialog({ title: 'Сменить ник', text: 'Для оскорбительных или вводящих в заблуждение ников. Ник — это и логин для входа, поэтому сообщите пользователю новый.', input: 'text', inputLabel: 'Новый ник', inputValue: cur, confirm: 'Сохранить' });
    if (!r || !r.comment || r.comment === cur) return;
    await run(() => api('PUT', `/v2/admin/users/${id}/username`, { username: r.comment }), 'Ник изменён');
    delete state.details[id]; delete state.users[id]; invalidate('users:', 'reviews:'); loadList(listKey('users'));
  },
  async media(id, el) {
    const enabled = el.dataset.on === '1';
    await run(() => api('PUT', `/v2/admin/users/${id}/media`, { enabled }), enabled ? 'Статус «Медиа» выдан' : 'Статус «Медиа» снят');
    delete state.details[id]; invalidate('users:'); loadUserDetail(id);
  },
  async role(id) {
    const cur = state.details[id]?.role;
    const role = cur === 'MODERATOR' ? 'USER' : 'MODERATOR';
    const r = await dialog({ title: role === 'MODERATOR' ? 'Сделать модератором' : 'Убрать роль модератора', text: role === 'MODERATOR' ? 'Модератор получит доступ к этой панели: очередь рецензий, пользователи, авторы.' : 'Пользователь потеряет доступ к панели.', confirm: 'Подтвердить' });
    if (!r) return;
    await run(() => api('PUT', `/v2/admin/users/${id}/role`, { role }), 'Роль изменена');
    delete state.details[id]; invalidate('users:'); loadUserDetail(id);
  },
  async verify(id) {
    const r = await dialog({ title: 'Верифицировать автора', text: 'Автор получит авторский профиль, авторские лайки и комментарии к своим фильмам.', input: 'area', inputLabel: 'Как подтвердили (видно только команде)', confirm: 'Верифицировать' });
    if (!r) return;
    nextSelected(listKey('authors'), id);
    await run(() => api('POST', `/v2/admin/authors/${id}/verify`, { comment: r.comment }), 'Автор верифицирован');
    invalidate('authors:'); loadList(listKey('authors'));
  },
  async areject(id) {
    const r = await dialog({ title: 'Отклонить заявку автора', input: 'area', inputLabel: 'Причина (увидит заявитель)', confirm: 'Отклонить', danger: true });
    if (!r) return;
    nextSelected(listKey('authors'), id);
    await run(() => api('POST', `/v2/admin/authors/${id}/reject`, { reason: r.comment }), 'Заявка отклонена');
    invalidate('authors:'); loadList(listKey('authors'));
  },
  async info(id) {
    const r = await dialog({ title: 'Запросить подтверждение', text: 'Например: «Пришлите ссылку на титры фильма или портфолио».', input: 'area', inputLabel: 'Сообщение заявителю', confirm: 'Отправить' });
    if (!r) return;
    await run(() => api('POST', `/v2/admin/authors/${id}/request-info`, { comment: r.comment }), 'Запрос отправлен');
    invalidate('authors:'); loadList(listKey('authors'));
  },
};

function go(page) {
  state.page = page;
  const u = new URL(location.href); u.searchParams.set('page', page); history.replaceState(null, '', u);
  if (LOADERS[listKey(page)]) loadList(listKey(page));
  if (page === 'audit') loadAudit();
  render(); document.getElementById('main')?.focus?.();
}

/* ── События ───────────────────────────────────────────────────────── */
app.addEventListener('click', e => {
  const t = e.target.closest('button,a'); if (!t) return;
  if (t.dataset.page) return go(t.dataset.page);
  if (t.dataset.tab) { const [page, tab] = t.dataset.tab.split(':'); state.tabs[page] = tab; loadList(listKey(page)); return render(); }
  if (t.dataset.select) {
    const [key, id] = t.dataset.select.split('|'); state.selected[key] = id;
    if (key.startsWith('users:')) loadUserDetail(id); else render();
    return;
  }
  if (t.dataset.act) return ACTIONS[t.dataset.act]?.(t.dataset.id, t);
  if (t.dataset.openUser) { state.tabs.users = 'NEW'; state.details[t.dataset.openUser] = undefined; go('users'); state.selected[listKey('users')] = t.dataset.openUser; loadUserDetail(t.dataset.openUser); return; }
  if (t.dataset.retry) { const k = t.dataset.retry; if (k === 'stats') loadStats(); else if (k === 'audit') loadAudit(true); else loadList(k, true); return; }
  if ('logout' in t.dataset) { saveTokens(null); state.me = null; state.lists = {}; state.details = {}; state.users = {}; state.stats = null; state.audit = null; render(); }
});
app.addEventListener('submit', async e => {
  if (e.target.id !== 'loginForm') return;
  e.preventDefault();
  const username = document.getElementById('lgUser').value.trim(), password = document.getElementById('lgPass').value;
  if (!username || !password) { state.loginError = 'Введите логин и пароль'; return render(); }
  try {
    const res = await raw('POST', '/v1/auth/login', { username, password }, false);
    saveTokens(res?.tokens || res);
    await boot(true);
  } catch (err) {
    state.loginError = err.status === 401 || err.status === 400 ? 'Неверный логин или пароль' : err.status === 429 ? 'Слишком много попыток, подождите минуту' : `Не удалось войти: ${err.message}`;
    render();
  }
});
document.addEventListener('keydown', e => {
  if (!state.me || state.page !== 'reviews' || state.tabs.reviews !== 'PENDING') return;
  if (e.target.closest('input,textarea,select,dialog') || e.metaKey || e.ctrlKey || e.altKey) return;
  const key = listKey('reviews'); const l = state.lists[key]; if (!l?.items?.length) return;
  const id = state.selected[key] || l.items[0].reviewId;
  const i = l.items.findIndex(r => r.reviewId === id);
  const k = e.key.toLowerCase();
  if (k === 'a' || k === 'ф') { e.preventDefault(); ACTIONS.publish(id); }
  else if (k === 'r' || k === 'к') { e.preventDefault(); ACTIONS.reject(id); }
  else if (k === 'j' || k === 'о') { e.preventDefault(); const n = l.items[i + 1]; if (n) { state.selected[key] = n.reviewId; render(); } }
  else if (k === 'k' || k === 'л') { e.preventDefault(); const p = l.items[i - 1]; if (p) { state.selected[key] = p.reviewId; render(); } }
});

/* ── Старт ─────────────────────────────────────────────────────────── */
async function boot(afterLogin) {
  state.booting = true; render();
  try {
    if (!tokens && !DEMO) throw new ApiError(401, 'GET', '/v1/me');
    const me = await api('GET', '/v1/me');
    if (!['ADMIN', 'MODERATOR'].includes(me?.role)) {
      saveTokens(null); state.me = null;
      state.loginError = 'У этого аккаунта нет доступа к панели: нужна роль ADMIN или MODERATOR.';
    } else { state.me = me; state.loginError = ''; }
  } catch (e) {
    state.me = null;
    if (afterLogin) state.loginError = `Не удалось получить профиль: ${e.message}`;
  }
  state.booting = false;
  if (state.me) { loadStats(); go(state.page); } else render();
}
if (DEMO && !tokens) saveTokens({ accessToken: 'demo' });
boot();
