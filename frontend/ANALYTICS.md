# Эхо — внутренняя аналитика (frontend-v10.40)

> **Обновлено:** 04.11.2026 · **Основание:** `docs/04-development/analytics-tz.md` (ТЗ от 28.10.2026) · **Ответственный:** Роман Стяжкин (PM)

В v10.40 перенесена работа фронтенда по метрикам из комплекта «v10.28 + API» (`АНАЛИТИКА-СТАТУС.md`, `МЕТРИКА-ИНСТРУКЦИЯ.md`). Модули адаптированы под актуальный сайт и бэкенд. Интерфейс v10.39 не изменился: 20 экранов совпадают до пикселя.

## 1. Статус связки «метрики ↔ БД»

| Звено | Статус | Что видно сейчас |
|---|---|---|
| Фронт собирает события и отправляет пачки | **Готово** | `POST /api/events`: массив событий, каждые 7 с или по 20 штук; при закрытии вкладки отправка через `sendBeacon` |
| nginx `/api/` → бэкенд | **Готово** | Запрос приходит в Spring как `POST /events`, правки nginx не нужны |
| Приём событий на бэкенде | **Не реализовано** | В бэкенде нет контроллера `/events`. Правило `anyRequest().authenticated()` отвечает анонимам 401. Фронт после первого 401/403/404/405 выключает отправку до перезагрузки страницы, интерфейс не страдает |
| Таблица `events` в БД | **Не реализовано** | В миграциях V1–V16 её нет |
| Связка анонимного визита с аккаунтом | **Фронт готов, бэкенд — нет** | `anon_id` и `session_id` уходят в `POST /v1/auth/register` и `/v1/auth/login`. Бэкенд их пока не читает: Spring игнорирует неизвестные поля, ошибки нет |
| Связка рецензии с формой | **Фронт готов, бэкенд — нет** | `form_instance_id` уходит в `POST /v2/reviews`, бэкенд его пока игнорирует |
| События бэкенда (`user_registered`, `review_submitted` …) | **Не реализовано** | По ТЗ (п. 4.3) их пишет бэкенд после записи в БД. Фронт их не дублирует |
| Яндекс Метрика | **Готово** | Счётчик 114072239 загружается только после согласия посетителя и только на опубликованном домене |

**Итог.** Фронт готов полностью и проверен на тестовом сервере с эндпоинтом `/events`: события доходят, дублей нет, все поля заполнены. В реальную БД данные начнут попадать после задач A-1…A-4 из раздела 3. Пересобирать фронт для этого не нужно.

## 2. События фронта (п. 4.2 ТЗ)

| № | Событие | Статус в v10.40 | Где и как |
|---|---|---|---|
| 1 | `session_started` | ✅ | Новая сессия после 30 мин бездействия. Передаются UTM, referrer без параметров, тип устройства (телефон — до 767 px, как в вёрстке) |
| 2 | `page_viewed` | ✅ | Типы: home, catalog, search, profile, **media_profile** (новое: профиль медиа), author_profile. Для экранов входа, Studio и редактирования профиля типа в ТЗ нет, события не отправляются |
| 3 | `film_card_impression` | ✅ | Типы списков: `home_popular` («Популярное сейчас»), `catalog`, `search`. Карточка видна ≥50% не меньше 1 с |
| 4 | `film_card_clicked` | ✅ | Те же списки, с `position`. Клик по имени режиссёра внутри строки каталога кликом по фильму не считается. Повторный клик быстрее 2 с отбрасывается |
| 5 | `film_page_viewed` | ✅ | Поле `source` (откуда пришли): тип списка / `review` (мини-карточка в рецензии) / `profile` (профили и фильмография автора) / `direct` |
| 6 | `review_expanded` | ✅ | «Читать полностью» (`long_text`) и «Показать спойлер» (`spoiler`). Сворачивание не учитывается |
| 7 | `review_form_opened` | ✅ **новое** | В v10.39 форма переключается из «Сохранить оценку» в «Отправить рецензию», когда в тексте появляется первый символ. Событие отправляется в этот момент, один раз на `form_instance_id` |
| 8 | `review_started` | ✅ | Первый символ в заголовке или тексте, один раз на форму. `form_instance_id` уходит в `POST /v2/reviews`, после отправки рецензии создаётся новый |
| 9 | `partner_placement_impression` | ⏳ хук | Срабатывает на атрибут `data-analytics-integration-id`. Партнёрского блока в интерфейсе пока нет |
| 10 | `review_impression` | ✅ | Рецензия видна ≥50% не меньше 2 с. `review_id` — UUID рецензии из бэкенда |
| 11 | `scroll_depth_reached` | ✅ | Страница фильма: 25/50/75/100%, каждый порог один раз |
| 12 | `review_helper_used` | ⏳ хук | Срабатывает на `data-review-helper`. Кнопки помощника в интерфейсе нет |
| 13 | `author_comment_impression` | ⏳ хук | Атрибут ставится, если у комментария есть `id`. Бэкенд пока не отдаёт `/v1/author/comments` |
| 14–15 | `author_landing_*` | ⏳ | Лендинг `/authors/` — отдельная статическая страница. Фронтенд её не размечал, функции в модуле готовы |

`list_type = home_now_discussed / home_latest`: в «Сейчас обсуждают» показаны рецензии, а не карточки фильмов. Отдельного блока «Новинки» нет. Значения выдумывать не стали — тот же подход, что у фронтенда.

**Где код:** `src/analytics.js` содержит всю логику: whitelist событий и полей, идентификаторы, очередь, показы, отправку. `src/metrika.js` — Яндекс Метрика. В `app.js` только вызовы: `trackRender()` после каждой отрисовки и атрибуты `data-analytics-*`. В `echo-api.js` — передача `anon_id`, `session_id` и `form_instance_id`.

**Персональные данные:** пароли, токены, тексты рецензий и комментариев, текст поиска не передаются. Это проверено тестами и сквозной проверкой.

## 3. Что сделать бэкенду

### A-1. Таблица событий (Flyway `V17__analytics_events.sql`)

```sql
CREATE TABLE events (
    event_id      UUID PRIMARY KEY,                 -- дедупликация: повтор отбрасывается
    event_name    VARCHAR(64)  NOT NULL,
    origin        VARCHAR(8)   NOT NULL,            -- 'fe' | 'be'
    ts            TIMESTAMP WITH TIME ZONE NOT NULL, -- время действия (от клиента для fe)
    received_at   TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    user_id       UUID NULL,
    anon_id       UUID NULL,
    session_id    UUID NULL,
    page_view_id  UUID NULL,
    page_type     VARCHAR(32)  NULL,
    is_test       BOOLEAN      NOT NULL DEFAULT FALSE,
    props         JSONB        NOT NULL DEFAULT '{}'  -- в H2-профиле тестов: JSON
);
CREATE INDEX idx_events_name_ts ON events (event_name, ts);
CREATE INDEX idx_events_anon    ON events (anon_id);
CREATE INDEX idx_events_user    ON events (user_id);
CREATE INDEX idx_events_session ON events (session_id);

-- Связка анонимных визитов с аккаунтом (критерий приёмки №3)
CREATE TABLE analytics_identities (
    anon_id   UUID NOT NULL,
    user_id   UUID NOT NULL REFERENCES users(user_id),
    linked_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (anon_id, user_id)
);
```

### A-2. Эндпоинт `POST /events`

С учётом nginx браузер обращается к `/api/events`.

- **Без авторизации:** `permitAll` для `POST /events` в `SecurityConfig`. `sendBeacon` не умеет передавать заголовок `Authorization`.
- **Формат запроса:** `Content-Type: application/json`, тело — массив событий в формате п. 4.1 ТЗ. Пример тела — в ТЗ.
- **Ответ:** `204`. Повтор `event_id` — не ошибка (`INSERT … ON CONFLICT (event_id) DO NOTHING`).
- **Валидация:**
  - `event_name` — из списка п. 4.2 ТЗ;
  - `page_type` — из перечня ТЗ или `null`;
  - `event_id`, `anon_id`, `session_id`, `page_view_id` — UUID;
  - `user_id` — UUID или `null`;
  - `props` — объект не больше 2 КБ.
  - Некорректное событие отбрасывается молча, остальные из пачки сохраняются.
- **Лимиты:** до 100 событий и до 256 КБ на запрос. Ограничение частоты по IP, по аналогии с `AuthRateLimitFilter`.
- **Доверие к `user_id`:** клиентский `user_id` не проверен. Если запрос пришёл с валидным `Authorization`, его стоит заменить ID из токена. Для отчётов надёжнее связка через `analytics_identities`.
- **Коды ответа:** 401/403/404/405 фронт воспринимает как «эндпоинт выключен» и прекращает отправку до перезагрузки страницы. Отвечать 5xx нужно только на временные сбои: фронт повторит запрос один раз.

### A-3. Идентификаторы во входе и регистрации

- Добавить в `RegisterRequest` и `LoginRequest` необязательные поля `anon_id` и `session_id` (UUID, `@JsonProperty("anon_id")`).
- После успешной регистрации или входа:
  - записать событие `user_registered` или `user_logged_in` (`origin='be'`, `user_id`, `anon_id`, `session_id`);
  - добавить строку в `analytics_identities`.
- Сейчас эти поля приходят, но игнорируются. Ошибки нет, данные теряются.

### A-4. События бэкенда (п. 4.3 ТЗ) — после успешной записи в БД

| Событие | Где | props |
|---|---|---|
| `rating_submitted` | `PUT /v2/me/ratings/movie/{id}` | `film_id`, `final_score` |
| `review_submitted` | `POST /v2/reviews` (не черновик) | `film_id`, `review_id`, `form_instance_id` (новое необязательное поле `CreateRequest`) |
| `review_published` / `review_rejected` / `review_removed` | модерация, удаление | `review_id`, `film_id` |
| `review_liked` / `review_like_removed` | `PUT/DELETE /v2/reviews/{id}/like` | `review_id` |
| `film_preference_added` / `removed` | `/v1/me/preferences/…` | `type`, `tmdb_id` |
| `author_comment_published`, `role_assigned` | по мере появления | — |
| `partner_link_clicked` | редирект `/go/{integration_id}` (A-5) | `integration_id` |

`anon_id` и `session_id` у событий бэкенда можно брать из последней строки `analytics_identities` пользователя.

### A-5. Позже, вместе с партнёрским блоком

- Редирект `GET /go/{integration_id}` → 302 на ссылку партнёра с записью `partner_link_clicked`.
- Список `integration_id` даёт PM.
- Блок на главной размечается атрибутом `data-analytics-integration-id="…"`, показы фронт посчитает сам.

### Проверка совместимости

Тест `analytics-backend/AnalyticsCompatibilityTest.java` положите в `src/test/java/org/example/echo/api/`. Он проверяет, что запросы фронта v10.40 с полями аналитики принимаются, и печатает текущий статус `/events`:
- до A-2 — `401`;
- после A-2 — `204`.

## 4. Настройки сборки

| Переменная | Значение в Dockerfile | Назначение |
|---|---|---|
| `VITE_API_URL` | `/api` | База API, события уходят на `{VITE_API_URL}/events` |
| `VITE_ANALYTICS_ENV` | `production` | `development` / `staging` / `test` включают `is_test=true` и вывод событий в консоль |
| `VITE_ANALYTICS_TEST_USER_IDS` | пусто | UUID тестовых аккаунтов через запятую: их события получают `is_test=true` |
| `VITE_YANDEX_METRIKA_ID` | `114072239` | Номер счётчика. Пусто — Метрика не загружается |

Значения меняются без правки файлов, например для стенда: `docker build --build-arg VITE_ANALYTICS_ENV=staging …`. На `localhost` и доменах со словом `staging` события всегда помечаются `is_test=true`, а Метрика на `localhost` не запускается.

Если бэкенду удобнее версионированный путь (`/v1/events`), достаточно поменять одну константу `EVENTS_PATH` в `src/analytics.js`.

## 5. Яндекс Метрика

- Логика фронтенда сохранена:
  - до согласия внешний скрипт не загружается;
  - страницы SPA отправляются виртуальными адресами `/_echo/...` без текста поиска, ников и токенов;
  - Вебвизор маскирует поля ввода и пользовательский текст.
- Плашка согласия оформлена в стиле v10.39. На телефоне она стоит над нижним меню. После ответа больше не показывается.
- Перед публичным запуском нужно согласовать с PM политику cookies и обработки данных: Метрика — сторонний сервис, а ТЗ говорит о собственном сервере.
