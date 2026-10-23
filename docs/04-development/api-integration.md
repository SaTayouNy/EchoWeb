# Интеграция фронтенда и бэкенда

> **Цель:** сопоставить запросы фронта и эндпоинты бэкенда, чтобы закрыть интеграцию в спринте 7 · **Обновлено:** 23.10.2026 · **Ответственный:** Анна Киракосян (FE), Иван Зуев (BE)

## Как устроено

- Фронт вызывает сервер только из `src/api.js`. Пути, которые он ожидает, — в [API.md](../../API.md) (без префикса версии, cookie-сессия).
- Бэкенд использует версии `/v1`, `/v2`, Bearer-токены и формат ошибок `{ error: { code, message, details } }`.
- **Решение для интеграции:** адаптируем фронт под бэкенд — меняем пути и авторизацию в `src/api.js`, а недостающее добавляем на бэкенде. Формат данных для интерфейса приводит функция `normalize` в `src/api.js`, остальной код не трогаем.

## Сопоставление

Статусы: ✅ есть · ⚠️ есть, отличается формат или путь · ❌ нет на бэкенде · ❓ уточнить у Ивана.

| Функция `api.*` | Ожидает фронт | Бэкенд | Статус |
|---|---|---|---|
| `loadInitialData` — фильмы | `GET /movies` | `GET /v2/media/movie/{tmdbId}/details` (по одному) | ⚠️ нужен список фильмов каталога |
| `loadInitialData` — рецензии | `GET /reviews` | `GET /v2/reviews` | ⚠️ сверить формат |
| `loadInitialData` — авторы | `GET /authors` | — | ❌ |
| `loadInitialData` — кандидаты в предпочтения | `GET /people` | — | ❌ (можно брать из состава фильмов TMDB) |
| `loadInitialData` — профили | `GET /users` | — | ❌ |
| `loadInitialData` — сессия | `GET /auth/me` | `GET /v1/me` | ⚠️ |
| `userAuth('login')` | `POST /auth/login` | `POST /v1/auth/…` | ⚠️ ответ — токены, а не cookie |
| `userAuth('register')` | `POST /auth/register` | `POST /v1/auth/…` | ⚠️ `409 USERNAME_TAKEN` |
| `authorAuth` | `POST /auth/author/login`, `/auth/author/register` | — | ❌ |
| `logout` | `POST /auth/logout` | `POST /v1/auth/…` | ⚠️ |
| — (обновление токена) | — | `POST /v1/auth/…` | ⚠️ добавить на фронте |
| `updateProfile` | `PATCH /me` | `/v1/me` | ❓ метод и поля |
| `savePreferences` | `PUT /me/preferences` (весь топ-5) | `/v1/me/preferences/{type}/{tmdbId}` (по одному) | ⚠️ фронт отправляет разницу |
| `publishReview` | `POST /movies/{id}/reviews` | `POST /v2/reviews` | ⚠️ модерация есть |
| `saveRating` | `POST /movies/{id}/ratings` | `/v2/me/ratings/{mediaType}/{tmdbId}` | ⚠️ |
| `setReviewLike` | `PUT/DELETE /reviews/{id}/like` | `PUT/DELETE /v2/reviews/{id}/like` | ✅ путь с `/v2` |
| `setAuthorLike` | `PUT/DELETE /reviews/{id}/author-like` | — | ❌ |
| `publishAuthorComment` | `PUT /movies/{id}/author-comment` | — | ❌ |
| `setAuthorCommentLike` | `PUT/DELETE /movies/{id}/author-comment/like` | — | ❌ |

## Порядок работ

1. **Чтение и вход** (без них сайт пуст): список фильмов, рецензии, сессия, вход, регистрация, обновление токена. Минимум для запуска без авторов: `/authors`, `/people`, `/users` можно временно отдавать пустыми списками.
2. **Действия зрителя:** оценка, рецензия, лайк, предпочтения, профиль.
3. **Эхо Studio:** вход автора, лайк автора, авторский комментарий. До готовности бэкенда — роль автора назначается вручную, а кабинет закрыт.
4. **Ошибки:** все коды из [состояний интерфейса](../03-product/ui-states.md) показываются корректно.

Каждый пункт — отдельная задача в GitHub Issues со ссылкой на эту страницу. После закрытия задачи обновите статус в таблице.

## Как проверить
1. Запустите бэкенд (`dev`, `stub`) и фронт с `VITE_DEV_PROXY=http://localhost:3000`.
2. Откройте `http://localhost:5173/?debug`; состояние интерфейса — `window.__echo.state`.
3. Пройдите сценарии из [тестирования](testing.md).
