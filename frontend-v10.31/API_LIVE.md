# EchoWeb ↔ Echo API: действующий контракт

Обновлено 28.10.2026. Рабочий адаптер — `src/echo-api.js`, его импортирует `src/app.js`. Тестовый адрес — `http://2.26.198.243/`. OpenAPI Backend: `/api/v3/api-docs`, Swagger UI: `/api/swagger-ui/index.html` через nginx; оба маршрута требуют токен администратора. Этот контракт размещён в проекте для передачи Frontend-разработчику. Отдельные статические страницы авторов: `/authors/` и `/authors/bez-formy/`.

## Общие правила

- Сайт и API доступны с одного адреса: фронт собирается с `VITE_API_URL=/api`, nginx убирает `/api` при передаче Spring. Для отдельного origin задайте `APP_CORS_ALLOWED_ORIGINS`.
- Постеры и фото из TMDB фронт запрашивает через `/tmdb-images/w500/{filename}` на том же адресе; nginx получает их с `image.tmdb.org`. Это позволяет открывать изображения, когда CDN TMDB недоступен в браузере пользователя. Поля `posterUrl` и `profileUrl` в ответе API остаются исходными URL TMDB.
- JSON/UTF-8; фильм имеет положительный TMDB ID (`number`), пользователь и рецензия — UUID (`string`). Даты API — ISO 8601 с часовым поясом; адаптер форматирует их для UI.
- Сессия: `Authorization: Bearer <accessToken>`. Регистрация возвращает `{user,tokens}`, вход — `{tokenType,accessToken,refreshToken}`. Адаптер сохраняет токены в `sessionStorage`, обновляет через `/v1/auth/refresh` и удаляет при выходе.
- Ошибки: HTTP 4xx/5xx и `{ "error": { "code": "...", "message": "Понятный текст на русском", "details": [{"field":"...","issue":"..."}] } }`. `details` может быть `null`. UI показывает `error.message`; при обрыве сети — «Нет связи с сервером». Примеры: неверный пароль — `401 UNAUTHORIZED`, неверное поле — `422 VALIDATION_ERROR`, занятый логин — `409 USERNAME_TAKEN`.
- Публикация рецензии происходит после модерации администратором. Публичный список содержит только `PUBLISHED`; собственные ожидающие рецензии возвращает `/v2/reviews/mine`.

## Запросы интерфейса

Все URL указаны относительно `/api`.

| Сценарий | Backend URL | Ответ / преобразование адаптера |
|---|---|---|
| Фильмы на главной | `GET /v1/media/discover/movies/popular`, `GET /v1/media/discover/movies?sort=best&page=1`, `GET /v1/media/MOVIE/{tmdbId}` | `items[]` и детали; адаптер формирует фильмы, людей, авторов из титров TMDB, включая фото актёров |
| Поиск и каталог | `GET /v1/media/search/movies?query=&page=`, `GET /v1/media/discover/movies?sort=&page=&year=&genreId=&country=` | Страницы фильмов |
| Страница фильма | `GET /v1/media/MOVIE/{tmdbId}`, `GET /v1/site/movies/{tmdbId}/scores`, `GET /v2/reviews?tmdbId={id}&mediaType=movie&limit=100` | Метаданные, агрегаты оценок, опубликованные рецензии |
| Единый объект фильма | `GET /v2/media/movie/{tmdbId}/details` | `{movie,ratings}`; готов для прямого подключения к странице |
| Рецензии | `GET /v2/reviews?limit=100`, `GET /v2/reviews/mine?limit=100` | `{items,meta}`; собственные черновики и ожидающие модерации отдельно |
| Авторы | `GET /v1/author/profiles`, `GET /v1/author/comments`, `GET /v1/author/review-likes` | Проверенные авторы, комментарии, авторские лайки |
| Профили | `GET /v1/users/{userId}/profile` | Имя, аватар, био и активность; UI собирает карточки авторов рецензий |
| Сессия | `GET /v1/me`, `GET /v1/me/profile?activityLimit=50`, `GET /v1/me/preferences?size=100` | Пользователь, профиль, сохранённые предпочтения |
| Вход, регистрация, выход | `POST /v1/auth/login`, `POST /v1/auth/register`, `POST /v1/auth/logout`, `POST /v1/auth/refresh` | В запросе `username` и `password`; токены см. выше |
| Оценка | `GET /v2/rating-schemes/current`, `PUT /v2/me/ratings/MOVIE/{tmdbId}` | `{schemeVersion,parameters:{dramaturgy_structure,characters_performance,visual_language,sound_music,impression}}`; значения целые 1–10; итог считает Backend |
| Рецензия | `POST /v2/reviews` | `{tmdbId,mediaType:"MOVIE",title,content,draft:false,spoiler}`; возвращает UUID и статус `PENDING_MODERATION` |
| Лайк | `PUT` / `DELETE /v2/reviews/{reviewId}/like` | `204 No Content` |
| Предпочтения | `GET /v1/me/preferences?size=100`, `PUT` / `DELETE /v1/me/preferences/{type}/{tmdbId}` | `type`: `MOVIE`, `DIRECTOR`, `ACTOR`; для персоны при добавлении нужно `{sourceMediaType:"MOVIE",sourceTmdbId}` |
| Редактирование профиля | `PUT /v1/me/profile`, `POST /v1/me/avatar` | `username`, `bio`, `avatarUrl`, текущие `preferences`; аватар передаётся как `dataUrl` |
| Кабинет автора | `POST /v1/author/apply`, `POST /v1/author/login`, `GET /v1/author/me` | Заявка `{login,password,name,specialty}`; вход после подтверждения |
| Действия автора | `PUT` / `DELETE /v1/author/reviews/{reviewId}/like`, `PUT /v1/author/movies/{tmdbId}/comment`, `PUT` / `DELETE /v1/author/movies/{tmdbId}/comment/like` | Авторский лайк, комментарий к своей работе, лайк комментария |

`GET /v2/reviews` возвращает `items[]` с `reviewId`, `userId`, `media {mediaType,tmdbId,posterUrl,title,director}`, `rating {parameters,formQuality,baseScore,impressionMultiplier,finalScore}`, `title`, `content`, `likes`, `publishedAt`, `status`. `GET /v2/media/movie/{id}/details` возвращает год, жанры, страны, титры с `profileUrl` и `ratings {count,schemes[]}`. Если оценок нет, `count=0` и `schemes=[]`; неизвестный фильм даёт `404`.

## Расхождения с исходным контрактом EchoWeb

| Исходный контракт | Решение / остаток | Оценка остатка |
|---|---|---|
| `/movies`, `/reviews`, `/authors`, `/people`, `/users`, `/auth/me` и cookie | Адаптер сводит Backend-маршруты к объектам UI, использует Bearer; вёрстку менять не требуется. | Закрыто |
| Ошибка `{message}` | Адаптер читает `error.message`; Backend возвращает единый формат на русском. | Закрыто |
| Мгновенная публикация рецензии | По утверждённой логике рецензия сначала проходит модерацию; UI показывает собственную ожидающую рецензию. | Закрыто |
| Полный публичный список пользователей и топ-5 | Профили запрашиваются для авторов видимых рецензий; общего каталога `/users` нет. Для отдельной страницы всех пользователей нужен paginated endpoint. | 0,5–1 день |
| Полный независимый каталог работ авторов | Профили есть, работы собираются из титров загруженных фильмов. Отдельный каталог потребует endpoint. | 1–2 дня |
| Единый запрос страницы фильма | `/v2/media/movie/{id}/details` уже есть; адаптер пока использует три запроса. | 0,5 дня |
| Продакшн-почта и восстановление пароля | Текущий UI использует логин и пароль без почты; для восстановления нужны SMTP и страницы. | 1–2 дня после выбора провайдера |

На тестовом HTTP-адресе по IP используйте только демонстрационные аккаунты. Публичный вход с реальными паролями следует включать после домена и HTTPS.
