# Админ-панель «Эхо»: что сделать бэкенду

> **Цель:** подключить админ-панель к БД и открыть к ней доступ только команде · **Обновлено:** 12.11.2026 · **Ответственный:** Роман Стяжкин (PM) · **Исполнитель:** BE

Панель — статическая страница (`admin-panel/`), она ходит в наш API под `/api`. Чего не хватает, она показывает прямо в разделе: «Ещё не подключено на бэкенде: `METHOD /path`». Остальные разделы при этом работают.

## 0. Что работает уже сейчас

| Функция | Эндпоинт | Статус |
|---|---|---|
| Вход | `POST /v1/auth/login`, `POST /v1/auth/refresh`, `GET /v1/me` | ✅ есть |
| Очередь рецензий | `GET /v2/reviews/moderation` | ✅ есть (только ADMIN) |
| Опубликовать рецензию | `POST /v2/reviews/{id}/publish` | ✅ есть (только ADMIN) |
| Ник автора рецензии | `GET /v1/users/{id}` | ✅ есть |
| Всё остальное | `/v2/admin/**` | ❌ нужно сделать (ниже) |

То есть после выкладки модерировать очередь и публиковать рецензии можно сразу, достаточно аккаунта с ролью `ADMIN`.

## 1. Раздать панель и закрыть к ней доступ

1. **Выложить папку** на сервер: `/var/www/echo-admin/` — содержимое `admin-panel/` как есть, без сборки. В `config.js` оставить `apiBase: '/api'`.
2. **В хостовом nginx, в блоке `server` для echomovies.ru (443):**

```nginx
# Админ-панель: отдельный пароль только для команды (второй слой защиты)
location /admin/ {
    alias /var/www/echo-admin/;
    index index.html;
    auth_basic "Echo admin";
    auth_basic_user_file /etc/nginx/.htpasswd-admin;
    add_header X-Robots-Tag "noindex, nofollow" always;
    add_header Cache-Control "no-store" always;
}

# API НЕ закрываем Basic Auth: сайт и панель передают JWT в заголовке Authorization,
# а Basic Auth использует тот же заголовок — nginx отвечал бы 401 на каждый запрос авторизованного пользователя.
# API защищает сам бэкенд (роли и токены).
location /api/ {
    auth_basic off;
    # … существующий proxy_pass на бэкенд без изменений
}
```

```bash
htpasswd -c /etc/nginx/.htpasswd-admin roman      # и по одному логину на каждого модератора
nginx -t && systemctl reload nginx
```

3. **Первый администратор** (один раз, в БД):

```sql
UPDATE users SET role = 'ADMIN' WHERE username_normalized = lower('<ник PM на сайте>');
```

Роль `MODERATOR` дальше выдаётся из самой панели (раздел «Пользователи»).

**Итого три слоя защиты:**
1. Пароль nginx на страницу `/admin/`.
2. JWT аккаунта сайта.
3. Проверка роли `ADMIN`/`MODERATOR` на каждом эндпоинте.

Главный из них — третий: всё, что меняет данные, проверяется на бэкенде.

## 2. Роли

- `/v2/admin/**`, `/v2/reviews/moderation` и `/v2/reviews/*/publish` открыть для `hasAnyRole('ADMIN','MODERATOR')`. Сейчас там только `ADMIN`.
- Только `ADMIN`: `PUT /v2/admin/users/{id}/role`. Нельзя менять роль самому себе и нельзя назначать `ADMIN` через API.
- В `SecurityConfig` правило для `/v2/admin/users/*/role` поставить **перед** общим `/v2/admin/**`.

## 3. Миграция `V18__admin_panel.sql`

Номер — следующий после ваших. Если `V17` занят аналитикой, это `V18`.

```sql
-- Рецензии: отклонение, снятие, спойлер
ALTER TABLE reviews DROP CONSTRAINT ck_reviews_status;
ALTER TABLE reviews ADD CONSTRAINT ck_reviews_status
  CHECK (status IN ('NOT_WRITTEN', 'PENDING_MODERATION', 'PUBLISHED', 'REJECTED'));
ALTER TABLE reviews ADD COLUMN reject_reason VARCHAR(32);
ALTER TABLE reviews ADD COLUMN reject_comment TEXT;
ALTER TABLE reviews ADD COLUMN moderated_by UUID REFERENCES users(user_id);
ALTER TABLE reviews ADD COLUMN moderated_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE reviews ADD COLUMN spoiler BOOLEAN NOT NULL DEFAULT FALSE; -- фронт уже присылает "spoiler"

-- Пользователи: проверка после регистрации, блокировка, «Медиа», автор
ALTER TABLE users ADD COLUMN checked_at TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN checked_by UUID REFERENCES users(user_id);
ALTER TABLE users ADD COLUMN blocked_reason TEXT;
ALTER TABLE users ADD COLUMN is_media BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE users ADD COLUMN is_author BOOLEAN NOT NULL DEFAULT FALSE;
UPDATE users SET checked_at = created_at; -- все существующие считаем проверенными
CREATE INDEX idx_users_checked ON users (checked_at) WHERE checked_at IS NULL;

-- Заявки авторов (создатели фильмов)
CREATE TABLE author_applications (
  application_id UUID PRIMARY KEY,
  user_id        UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
  name           VARCHAR(120) NOT NULL,
  specialty      VARCHAR(80),
  links          TEXT NOT NULL DEFAULT '[]',   -- JSON-массив строк
  films          TEXT NOT NULL DEFAULT '[]',   -- JSON-массив строк
  note           TEXT,
  status         VARCHAR(16) NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED')),
  reason         TEXT,
  created_at     TIMESTAMP WITH TIME ZONE NOT NULL,
  decided_at     TIMESTAMP WITH TIME ZONE,
  decided_by     UUID REFERENCES users(user_id)
);
CREATE INDEX idx_author_applications_status ON author_applications (status, created_at);

-- Журнал действий команды
CREATE TABLE admin_actions (
  action_id    UUID PRIMARY KEY,
  at           TIMESTAMP WITH TIME ZONE NOT NULL,
  actor_id     UUID NOT NULL REFERENCES users(user_id),
  action       VARCHAR(40) NOT NULL,
  target_type  VARCHAR(16) NOT NULL,        -- REVIEW | USER | AUTHOR
  target_id    UUID NOT NULL,
  target_label VARCHAR(300),                -- «Заголовок» — @ник, на момент действия
  reason       TEXT,
  comment      TEXT
);
CREATE INDEX idx_admin_actions_at ON admin_actions (at DESC);
```

## 4. Эндпоинты

Все под `/v2/admin` (кроме уже существующих). Ответы списков — `{items, meta:{limit, offset, total}}`, как в остальном API. Каждое действие пишет строку в `admin_actions` в той же транзакции.

### Рецензии

| Метод и путь | Тело | Что делает |
|---|---|---|
| `GET /v2/admin/reviews?status=PUBLISHED\|REJECTED&limit&offset` | — | История, новые сверху (`updated_at DESC`) |
| `POST /v2/admin/reviews/{id}/reject` | `{reason, comment}` | `PENDING_MODERATION` → `REJECTED`. Автор видит причину в «Моих рецензиях», может исправить и отправить заново (→ `PENDING_MODERATION`) |
| `POST /v2/admin/reviews/{id}/unpublish` | `{reason, comment}` | `PUBLISHED` → `REJECTED`, `published_at = NULL` |

`reason` — одно из: `TOO_SHORT`, `INSULT`, `PROFANITY`, `SPOILER`, `OFF_TOPIC`, `SPAM`, `OTHER`. Для `OTHER` комментарий обязателен (400 без него).

**В `ReviewResponse` добавить:**
- `author: {userId, username}` — иначе панель делает отдельный запрос ника на каждую рецензию;
- `spoiler`;
- `rejectReason`, `rejectComment` — их видят только автор и команда.

Публичные списки отдают только `PUBLISHED`, как сейчас.

### Пользователи

| Метод и путь | Тело | Что делает |
|---|---|---|
| `GET /v2/admin/users?state=NEW\|CHECKED\|BLOCKED&limit&offset` | — | `NEW` = `checked_at IS NULL AND is_active`, новые сверху; `BLOCKED` = `NOT is_active` |
| `GET /v2/admin/users/{id}` | — | Карточка, формат ниже |
| `POST /v2/admin/users/{id}/check` | — | `checked_at = now()`, `checked_by = actor` |
| `POST /v2/admin/users/{id}/block` | `{reason}` | `is_active = false`, `blocked_reason`. **Отозвать все сессии** (`sessions`), чтобы refresh-токен перестал работать. Вход заблокированного — 403 с понятным текстом |
| `POST /v2/admin/users/{id}/unblock` | — | `is_active = true`, `blocked_reason = NULL` |
| `PUT /v2/admin/users/{id}/username` | `{username}` | Смена ника по тем же правилам, что при регистрации, с уникальностью `username_normalized` (409, если занят) |
| `PUT /v2/admin/users/{id}/media` | `{enabled}` | Бейдж «Медиа» (`is_media`) |
| `PUT /v2/admin/users/{id}/role` | `{role: USER\|MODERATOR}` | Только ADMIN, не себе |

Карточка пользователя (`AdminUserResponse`):

```json
{
  "userId": "…", "username": "kinoman_ekb", "email": null, "role": "USER", "isActive": true,
  "createdAt": "2026-11-12T10:00:00Z", "checkedAt": null, "checkedBy": null, "blockedReason": null,
  "media": false, "author": false, "bio": "…", "avatarUrl": null,
  "stats": { "ratings": 4, "reviews": 1, "receivedLikes": 0 },
  "recentReviews": [ /* до 5 ReviewResponse любого статуса */ ]
}
```

В списке достаточно тех же полей без `bio` и `recentReviews`. `checkedBy` — ник, а не UUID.

### Авторы

Фронт уже отправляет заявку: `POST /v1/author/apply {login, password, name, specialty}`. Нужно реализовать этот эндпоинт:
- создать пользователя, как при обычной регистрации;
- создать строку в `author_applications` со статусом `PENDING`.

Позже форму можно расширить ссылками и фильмами.

| Метод и путь | Тело | Что делает |
|---|---|---|
| `GET /v2/admin/authors?status=PENDING\|VERIFIED\|REJECTED&limit&offset` | — | `{applicationId, userId, username, name, specialty, links[], films[], note, status, createdAt, decidedAt, decidedBy, reason}` |
| `POST /v2/admin/authors/{id}/verify` | `{comment}` | `status = VERIFIED`, `users.is_author = true` |
| `POST /v2/admin/authors/{id}/reject` | `{reason}` | `status = REJECTED` |
| `POST /v2/admin/authors/{id}/request-info` | `{comment}` | Сохраняет запрос в `note`. Связь с заявителем пока вручную |

### Обзор и журнал

| Метод и путь | Ответ |
|---|---|
| `GET /v2/admin/stats` | `{pendingReviews, oldestPendingAt, newUsers, pendingAuthors, published7d, rejected7d}` |
| `GET /v2/admin/audit?limit&offset` | `{items:[{id, at, actor:{userId, username}, action, targetType, targetId, targetLabel, reason, comment}], meta}` |

Значения `action`: `REVIEW_PUBLISHED`, `REVIEW_REJECTED`, `REVIEW_UNPUBLISHED`, `USER_CHECKED`, `USER_BLOCKED`, `USER_UNBLOCKED`, `USER_RENAMED`, `USER_MEDIA_ON`, `USER_MEDIA_OFF`, `USER_ROLE`, `AUTHOR_VERIFIED`, `AUTHOR_REJECTED`, `AUTHOR_INFO_REQUESTED`. Публикацию через существующий `POST /v2/reviews/{id}/publish` тоже записать в журнал.

## 5. Аналитика

По ТЗ аналитики (`frontend-v10.40/ANALYTICS.md`, раздел A-4) эти действия пишут события бэкенда в таблицу `events`:

| Действие | Событие |
|---|---|
| Публикация рецензии | `review_published` |
| Отклонение рецензии | `review_rejected` |
| Снятие с публикации | `review_removed` |
| Смена роли, «Медиа», верификация автора | `role_assigned` |

Это та же запись, что в `admin_actions`, только в формате событий.

## 6. Проверка

1. Открыть `https://echomovies.ru/admin/`. Браузер спрашивает пароль nginx, после него — экран входа панели.
2. Войти обычным аккаунтом: «нет доступа». Войти аккаунтом с `ADMIN`: открывается «Обзор».
3. Очередь, публикация и отклонение рецензии. Отклонённая видна автору с причиной и пропадает из публичных списков.
4. Заблокировать тестового пользователя: он не может войти, его refresh-токен не работает.
5. В «Журнале действий» видно все шаги выше.
6. Сайт и бета-доступ работают как раньше: `/api` без Basic Auth, вход на сайт, оценка, рецензия.

Демо панели без бэкенда: `https://echomovies.ru/admin/?demo` (данные вымышленные, в браузере).
