# 4. Разработка

> **Цель:** точка входа для разработчиков: где код, как запустить, как устроено · **Обновлено:** 23.10.2026 · **Ответственный:** Анна Киракосян (FE), Иван Зуев (BE)

Правила веток, PR и задач — в **[CONTRIBUTING.md](../../CONTRIBUTING.md)**.

## Репозитории

| Часть | Репозиторий | Стек | Ответственный |
|---|---|---|---|
| Фронтенд + вики | [SaTayouNy/EchoWeb](https://github.com/SaTayouNy/EchoWeb) (этот) | Vite, чистый JavaScript | Анна Киракосян |
| Бэкенд | `zuevvdele/EchoTheProject` (доступ — у Ивана Зуева) | Spring Boot 3.4, Java 21, H2 / PostgreSQL | Иван Зуев |

## Быстрый старт

### Фронтенд
Нужен Node.js 20+.
```bash
git clone https://github.com/SaTayouNy/EchoWeb.git
cd EchoWeb
npm install
npm run dev            # http://localhost:5173, демо-данные
```
Чтобы работать с локальным бэкендом — создайте `.env`:
```
VITE_API_URL=/api
VITE_DEV_PROXY=http://localhost:3000
```

### Бэкенд
Нужны JDK 21 и Maven. Команды выполняются в папке, где лежит `pom.xml`.

**macOS / Linux**
```bash
SPRING_PROFILES_ACTIVE=dev APP_TMDB_PROVIDER=stub mvn spring-boot:run
```
**Windows (cmd)**
```bat
set JAVA_HOME=C:\Program Files\Java\jdk-21
set SPRING_PROFILES_ACTIVE=dev
set APP_TMDB_PROVIDER=stub
mvn spring-boot:run
```
- Сервер: `http://localhost:3000`; консоль БД: `http://localhost:3000/h2-console` (логин и пароль `sa` / `sa`).
- `APP_TMDB_PROVIDER=stub` — тестовые данные фильмов без ключа TMDB; `http` — реальный TMDB (нужен ключ).
- Частые ошибки на Windows: `JAVA_HOME=…` без `set`; запуск `mvn` не из папки с `pom.xml`; Java 25 вместо 21.

## Разделы

| Страница | Что внутри |
|---|---|
| [Архитектура](architecture.md) | Компоненты, протоколы, ограничения |
| [Интеграция FE ↔ BE](api-integration.md) | Какой запрос фронта какому эндпоинту бэкенда соответствует, что не готово |
| [Аутентификация](auth.md) | Роли, регистрация, токены, сессии |
| [Хранение данных](data-storage.md) | Сущности БД, надёжность |
| [Внешние сервисы](external-services.md) | TMDB, аналитика, что подключаем позже |
| [ТЗ: сбор продуктовой аналитики](analytics-tz.md) | События Frontend для карты метрик, формат, критерии приёмки |
| [Тестирование](testing.md) | Что и как проверяем перед релизом |
| [Выкладка](deploy.md) | Сборка, сервер, nginx, переменные |

Справочники в корне репозитория: [API.md](../../API.md) — контракт, который ожидает фронт; [BACKEND.md](../../BACKEND.md) — инструкция для бэкенда.
