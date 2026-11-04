/**
 * Эхо — optional Yandex Metrica integration for the single-page interface.
 * - Disabled without a numeric counter ID.
 * - Does not load third-party code until the visitor opts in.
 * - Does not load on file:// or localhost, even if a counter ID is configured.
 * - Sends only sanitized virtual URLs, never free-text, tokens, or account IDs.
 * - Enables Webvisor after consent, with masking for private inputs and user content.
 * - Keeps first-party /api/events analytics completely independent.
 *
 * Important: introducing a third-party analytics service requires PM/privacy approval.
 *
 * Перенесено из комплекта фронтенда «v10.28 + API» без изменения логики.
 * В v10.40 номер счётчика приходит из VITE_YANDEX_METRIKA_ID (см. app.js), список
 * маскируемых блоков дополнен классами разметки v10.40.
 */
const CONSENT_KEY = 'echo:metrika:consent:v1';
const SCRIPT_URL = 'https://mc.yandex.ru/metrika/tag.js';
const SUPPORTED_PAGES = new Set(['home', 'catalog', 'search', 'film', 'profile', 'author_profile', 'media_profile']);
const SAFE_STUDIO = new Set(['dashboard', 'films', 'reviews', 'comments', 'settings', 'profile', 'statistics']);
const SAFE_AUTH = new Set(['login', 'register', 'forgot']);

export function counterIdFrom(value) {
  const text = String(value ?? '').trim();
  return /^\d{5,12}$/.test(text) && Number.isSafeInteger(Number(text)) ? Number(text) : null;
}

export function virtualPage(snapshot = {}) {
  const type = snapshot.pageType;
  const filmId = Number(snapshot.filmId);
  if (type === 'film') {
    return {path: Number.isSafeInteger(filmId) && filmId >= 0 ? `/film/${filmId}` : '/film', title: 'Фильм — Эхо'};
  }
  if (type === 'profile') {
    return {path: snapshot.page === 'otherProfile' ? '/profile/public' : '/profile', title: 'Профиль — Эхо'};
  }
  if (type === 'author_profile') return {path: '/author', title: 'Автор — Эхо'};
  if (type === 'media_profile') return {path: '/media', title: 'Медиа — Эхо'};
  if (SUPPORTED_PAGES.has(type)) {
    const titles = {home: 'Главная', catalog: 'Каталог', search: 'Поиск'};
    return {path: type === 'home' ? '/' : `/${type}`, title: `${titles[type] || 'Страница'} — Эхо`};
  }
  if (snapshot.view === 'userAuth' || snapshot.view === 'authorAuth') {
    const scope = snapshot.view === 'userAuth' ? 'user' : 'author';
    const requested = scope === 'user' ? snapshot.userAuth : snapshot.authorAuth;
    const action = SAFE_AUTH.has(requested) ? requested : 'login';
    return {path: `/auth/${scope}/${action}`, title: 'Авторизация — Эхо'};
  }
  if (snapshot.view === 'studio') {
    const section = SAFE_STUDIO.has(snapshot.studioPage) ? snapshot.studioPage : 'dashboard';
    return {path: `/studio/${section}`, title: 'Студия — Эхо'};
  }
  return null;
}

function safeHost(win) {
  const loc = win?.location;
  if (!loc || !/^https?:$/.test(loc.protocol || '')) return false;
  const host = String(loc.hostname || '').toLowerCase();
  return !['localhost', '127.0.0.1', '::1', '[::1]'].includes(host) && !host.endsWith('.localhost');
}

// Keep referrer attribution by domain without exposing search terms, credentials or URL tokens.
function safeReferrer(value) {
  try {
    const url = new URL(String(value || ''));
    return /^https?:$/.test(url.protocol) ? `${url.origin}/` : '';
  } catch { return ''; }
}

function readConsent(win) {
  try {
    const value = win.localStorage?.getItem(CONSENT_KEY);
    return value === 'yes' ? true : value === 'no' ? false : null;
  } catch { return null; }
}

function writeConsent(win, allowed) {
  try { win.localStorage?.setItem(CONSENT_KEY, allowed ? 'yes' : 'no'); } catch { /* private mode */ }
}

export class MetricaClient {
  constructor({win = globalThis.window, doc = globalThis.document, id, allowLocal = false} = {}) {
    this.win = win;
    this.doc = doc;
    this.id = counterIdFrom(id ?? win?.__ECHO_YANDEX_METRIKA_ID__);
    this.allowLocal = allowLocal;
    this.enabled = Boolean(this.id && (allowLocal || safeHost(win)));
    this.consent = readConsent(win);
    this.started = false;
    this.lastKey = null;
    this.lastUrl = null;
    this.current = null;
    this.banner = null;
    if (this.enabled) {
      if (this.consent === true) this.start();
      else if (this.consent === null) this.showConsent();
    }
  }

  showConsent() {
    if (!this.enabled || this.banner || !this.doc?.body) return;
    const banner = this.doc.createElement('section');
    banner.className = 'echo-metrica-consent';
    banner.setAttribute('role', 'dialog');
    banner.setAttribute('aria-label', 'Настройка аналитических cookies');
    banner.innerHTML = '<div class="echo-metrica-consent__copy"><strong>Аналитика сайта</strong><p>Помогите нам улучшать «Эхо». С вашего согласия Яндекс Метрика будет собирать статистику посещений и переходов. Вы можете отказаться — сайт продолжит работать.</p></div><div class="echo-metrica-consent__actions"><button type="button" data-metrica-consent="no">Отказаться</button><button type="button" data-metrica-consent="yes">Разрешить</button></div>';
    banner.querySelector('[data-metrica-consent="yes"]')?.addEventListener('click', () => this.setConsent(true));
    banner.querySelector('[data-metrica-consent="no"]')?.addEventListener('click', () => this.setConsent(false));
    this.doc.body.appendChild(banner);
    this.banner = banner;
  }

  setConsent(allowed) {
    this.consent = Boolean(allowed);
    writeConsent(this.win, this.consent);
    this.banner?.remove();
    this.banner = null;
    if (this.consent) {
      this.start();
      if (this.current) this.sendHit(this.current);
    }
  }

  start() {
    if (!this.enabled || !this.consent || this.started) return false;
    this.started = true;
    const win = this.win;
    // Standard asynchronous tag queue; no third-party request until consent.
    win.ym = win.ym || function () { (win.ym.a = win.ym.a || []).push(arguments); };
    win.ym.l = win.ym.l || Date.now();
    // The counter was supplied with ecommerce: "dataLayer". Ensure the data layer exists.
    if (!Array.isArray(win.dataLayer)) win.dataLayer = [];
    this.maskPrivateContent();
    const tag = this.doc.createElement('script');
    tag.async = true;
    tag.src = `${SCRIPT_URL}?id=${this.id}`;
    tag.dataset.echoMetrica = String(this.id);
    this.doc.head.appendChild(tag);
    win.ym(this.id, 'init', {
      ssr: true,
      defer: true, // SPA: count the initial page and route changes only via explicit hit().
      webvisor: true,
      clickmap: true,
      ecommerce: 'dataLayer',
      referrer: safeReferrer(this.doc.referrer),
      url: this.current?.url || new URL('/_echo/', win.location.origin).href,
      accurateTrackBounce: true,
      trackLinks: true,
    });
    return true;
  }

  // Webvisor should never record passwords, review drafts, private profile
  // fields, search terms or user-generated review content. Mask newly rendered
  // SPA elements before the hit is sent; this does not affect visual styling.
  maskPrivateContent() {
    const selectors = [
      'input', 'textarea', '[contenteditable]',
      '.review', '.review-copy', '.review-text',
      '.composer', '.auth-form', '.nav-search',
      '.profile-hero', '.profile-main', '.edit-panel',
      '.studio-block', '.search-result', '.search-results',
      '.profile-card', '.profile-bio', '.profile-header',
      '.author-profile', '.media-profile', '.comment-card',
      // v10.40: поиск на телефоне, вход, редактор Студии, профиль
      '.m-search', '.auth-fields', '.profile-review', '.author-note',
    ];
    try {
      for (const el of this.doc?.querySelectorAll?.(selectors.join(',')) || []) {
        el.classList?.add('ym-hide-content');
        if (el.matches?.('input,textarea,[contenteditable]')) el.classList?.add('ym-disable-keys');
      }
    } catch { /* analytics must never interrupt the interface */ }
  }

  pageView(snapshot) {
    if (!this.enabled) return false;
    this.maskPrivateContent();
    const page = virtualPage(snapshot);
    if (!page) return false;
    // Route key is compared locally only. It is never sent to Yandex.
    const key = [snapshot.view, snapshot.page, snapshot.routeKey, snapshot.userAuth,
      snapshot.authorAuth, snapshot.studioPage].map(x => String(x ?? '')).join('|');
    if (key === this.lastKey) return false;
    this.lastKey = key;
    const url = new URL(`/_echo${page.path}`, this.win.location.origin).href;
    this.current = {url, title: page.title};
    if (this.consent === true) this.sendHit(this.current);
    return true;
  }

  sendHit(page) {
    if (!this.started || !this.consent || !page) return false;
    const options = {title: page.title};
    if (this.lastUrl) options.referer = this.lastUrl;
    this.win.ym(this.id, 'hit', page.url, options);
    this.lastUrl = page.url;
    return true;
  }

  /** Optional, explicit frontend-only goals. Do not use for BE-confirmed writes. */
  goal(name) {
    const allowed = new Set(['film_card_clicked', 'review_started', 'review_expanded', 'review_form_opened']);
    if (!this.started || !this.consent || !allowed.has(name)) return false;
    this.win.ym(this.id, 'reachGoal', name);
    return true;
  }
}

let instance = null;
export function initializeMetrica(options = {}) {
  if (!instance) instance = new MetricaClient(options);
  return instance;
}
