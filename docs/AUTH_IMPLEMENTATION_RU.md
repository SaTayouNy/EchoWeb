# Backend-аутентификация по логину и паролю

Проверено 16 сентября 2026. В рамках этой задачи изменён только backend. Добавленные ранее файлы управления сессией в `frontend/src/auth` и frontend-тесты удалены, а `frontend/src/App.tsx` возвращён к состоянию до этой задачи.

## Что реализовано

| Требование | Статус backend | Реализация |
|---|---|---|
| Регистрация по логину и паролю | Готово | `POST /v1/auth/register` принимает `username` и `password` без email, создаёт активного пользователя и возвращает access/refresh tokens |
| Авторизация зарегистрированного пользователя | Готово | `POST /v1/auth/login` принимает `username` и `password`; регистр логина не учитывается |
| Выход из аккаунта | Готово | `POST /v1/auth/logout` отзывает текущую серверную сессию; её access и refresh tokens перестают работать |
| Сессия при переходах между страницами | Backend готов | Bearer token действует для всех защищённых API. Хранение токенов и их передача между экранами должен реализовать FE |
| Обработка перезагрузки страницы | Backend готов | `GET /v1/me` проверяет access token, `POST /v1/auth/refresh` обновляет истёкшую пару. Когда и где хранить пару токенов, решает FE |
| Проверка введённых данных | Готово | Логин обязателен, не длиннее 80 символов и без управляющих символов; пароль от 8 символов и не длиннее 72 байт UTF-8 |
| Устойчивость к некорректным данным | Готово | Ошибки возвращаются как контролируемые 400/401/409/422, а не как падение приложения |

Дополнительно:

- email при регистрации необязателен; прежний сценарий с email и подтверждением сохранён;
- занятый логин возвращает `409 USERNAME_TAKEN`;
- уникальность логина без учёта регистра обеспечивается в БД колонкой `username_normalized` и ограничением UNIQUE;
- refresh token одноразовый: обновление создаёт новую пару и отзывает старую;
- пароли и токены сессии не хранятся в открытом виде.

## Миграции

- V10 разрешает `NULL` в `users.email` для аккаунтов по логину и паролю.
- V11 добавляет и заполняет `users.username_normalized`, затем включает `NOT NULL` и `UNIQUE`.

Перед V11 в существующей БД нужно проверить конфликты логинов:

```sql
SELECT LOWER(TRIM(username)) AS canonical_username, COUNT(*)
FROM users
GROUP BY LOWER(TRIM(username))
HAVING COUNT(*) > 1;
```

Если запрос вернул строки, конфликтующие аккаунты нужно разобрать вручную до миграции. V11 намеренно останавливается и не переименовывает пользователей автоматически.

Ранее сохранённые пароли длиннее 72 байт теперь не принимаются при login, потому что BCrypt раньше мог молча игнорировать хвост после 72 байт. Таким пользователям потребуется сброс пароля.

## Автоматическая проверка

Из корня проекта:

```powershell
mvn -B test
```

Ожидаемый результат текущей версии: `Tests run: 113, Failures: 0, Errors: 0, Skipped: 0` и `BUILD SUCCESS`.

Основные проверки задачи находятся в:

- `UsernameAuthIntegrationTest` — регистрация, login, `/v1/me`, refresh, logout, валидация, конкурирующие варианты регистра логина;
- `UsernameMigrationTest` — обновление заполненной БД с V10 до V11 и безопасная остановка при конфликте;
- `AuthControllerIntegrationTest` и `SessionIntegrationTest` — совместимость email-сценария и полный жизненный цикл серверной сессии.

## Ручная проверка API

Сначала запустить backend в dev-профиле:

```powershell
$env:SPRING_PROFILES_ACTIVE = "dev"
$env:APP_AUTH_TOKEN_SECRET = "local-test-secret-change-before-production"
mvn spring-boot:run
```

В другом PowerShell выполнить:

```powershell
$base = "http://localhost:3000"
$username = "test_$(Get-Date -Format yyyyMMddHHmmss)"
$password = "StrongPass123!"

$register = Invoke-RestMethod -Method Post -Uri "$base/v1/auth/register" `
  -ContentType "application/json" `
  -Body (@{ username = $username; password = $password } | ConvertTo-Json)

$access = $register.tokens.accessToken
$headers = @{ Authorization = "Bearer $access" }

Invoke-RestMethod -Method Get -Uri "$base/v1/me" -Headers $headers

$login = Invoke-RestMethod -Method Post -Uri "$base/v1/auth/login" `
  -ContentType "application/json" `
  -Body (@{ username = $username.ToUpperInvariant(); password = $password } | ConvertTo-Json)

$newPair = Invoke-RestMethod -Method Post -Uri "$base/v1/auth/refresh" `
  -ContentType "application/json" `
  -Body (@{ refreshToken = $login.refreshToken } | ConvertTo-Json)

$newHeaders = @{ Authorization = "Bearer $($newPair.accessToken)" }
Invoke-RestMethod -Method Post -Uri "$base/v1/auth/logout" -Headers $newHeaders
```

Ожидается: регистрация возвращает пользователя и пару токенов; `/v1/me` возвращает того же пользователя; login с другим регистром логина успешен; refresh возвращает новую пару; logout отвечает `204 No Content`. После logout запрос `/v1/me` с последним access token и повтор refresh с последним refresh token должны вернуть `401`.

## Граница ответственности FE

Backend предоставляет все операции для сохранения пользовательской сессии, но сам не может сохранить авторизованное состояние при переходе между страницами или после перезагрузки браузера. Для полной приёмки пунктов 4 и 5 frontend позже должен:

1. безопасно хранить пару токенов;
2. передавать access token как `Authorization: Bearer ...`;
3. при старте проверять `/v1/me`;
4. при истечении access token один раз вызывать `/v1/auth/refresh` и атомарно заменять оба токена;
5. при logout или отказе refresh очищать локальную сессию.

Это необходимая FE-интеграция, но исходники frontend в текущей задаче не изменяются.
