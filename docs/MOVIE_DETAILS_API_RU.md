# Карточка фильма с оценками — контракт для Frontend

Контракт подготовлен для согласования с Frontend-разработчиком. Он сохранён вместе с реализацией; после просмотра FE может предложить изменения названий или состава полей. Публичный запрос не требует Bearer-токена.

## Запрос

`GET /v2/media/movie/{tmdbId}/details`

`tmdbId` — положительное целое число (`int64`), внешний идентификатор фильма в TMDB. Это не UUID `mediaId` из БД. Параметров query и тела нет. `Content-Type` успешного ответа — `application/json`.

Карточка загружается из таблицы `media`; при отсутствии записи Backend синхронизирует её из TMDB и сохраняет. Агрегаты берутся из сохранённых структурированных оценок пользователей. Для обновления уже существующей карточки продолжает работать административный `POST /v1/media/movie/{tmdbId}/sync`.

## Ответ `200`

Пример ниже показывает форму ответа и иллюстративные значения полей.

```json
{
  "movie": {
    "mediaId": "b49939c0-6424-4f69-b9ef-6027a0834001",
    "mediaType": "MOVIE",
    "tmdbId": 603,
    "title": "Матрица",
    "originalTitle": "The Matrix",
    "overview": "Описание фильма",
    "originalOverview": "Movie overview",
    "releaseYear": 1999,
    "releaseDate": "1999-03-31",
    "runtimeMinutes": 136,
    "posterPath": "/poster.jpg",
    "posterUrl": "https://image.tmdb.org/t/p/w500/poster.jpg",
    "director": "Лана Вачовски",
    "cast": ["Киану Ривз"],
    "castDetails": [{
      "tmdbPersonId": 6384,
      "name": "Киану Ривз",
      "role": "Neo",
      "profilePath": "/actor.jpg",
      "profileUrl": "https://image.tmdb.org/t/p/w500/actor.jpg"
    }],
    "crewDetails": [{
      "tmdbPersonId": 9339,
      "name": "Лана Вачовски",
      "role": "Director",
      "profilePath": "/director.jpg",
      "profileUrl": "https://image.tmdb.org/t/p/w500/director.jpg"
    }],
    "genres": ["Фантастика"],
    "countries": ["США"],
    "lastSyncedAt": "2026-10-14T10:00:00Z",
    "createdAt": "2026-10-14T10:00:00Z",
    "updatedAt": "2026-10-14T10:00:00Z"
  },
  "ratings": {
    "mediaType": "MOVIE",
    "tmdbId": 603,
    "count": 2,
    "schemes": [{
      "schemeVersion": "echo-form-impression-v1",
      "count": 2,
      "parameterAverages": {
        "dramaturgy_structure": 9.5,
        "characters_performance": 9,
        "visual_language": 8.5,
        "sound_music": 9.5,
        "impression": 9.5
      },
      "averageFinalScore": 88.5
    }]
  }
}
```

`movie` — существующий `MediaResponse`: `mediaId` имеет тип UUID, `tmdbId` — `int64`, `releaseYear` и `runtimeMinutes` — целые числа; дата — `YYYY-MM-DD`, даты-время — ISO 8601 с часовым поясом. Название обязательно; постер, режиссёр и другие необязательные данные TMDB могут быть `null`, если поставщик их не предоставил. `cast`, `castDetails`, `crewDetails`, `genres`, `countries` — массивы, возможно пустые. Фото актёра берётся из `castDetails[].profileUrl`, а `profilePath` — относительный путь TMDB.

`ratings.count` — число структурированных пользовательских оценок фильма. `schemes` содержит отдельную группу для каждой версии схемы; версии не смешиваются. Внутри группы `count` — целое число, `parameterAverages` и `averageFinalScore` — JSON-числа, округлённые до четырёх знаков. Ключи параметров соответствуют `GET /v2/rating-schemes/current` и описаны в [контракте оценок](RATINGS_API_RU.md). Четыре параметра формы и отдельное `impression` выводятся из сохранённых исходных значений. `averageFinalScore` — среднее индивидуальных итоговых баллов 0–100 после применения множителя впечатления и округления; его **нельзя повторно рассчитывать из средних параметров**. Расчёт индивидуальных итогов выполняет существующий `RatingCalculator`.

Если оценок нет, фильм всё равно возвращается с `ratings: {"mediaType":"MOVIE","tmdbId":603,"count":0,"schemes":[]}`. FE может показать «Нет оценок». Если в будущем будет несколько схем, FE должен выбрать нужную версию явно; активная версия определяется через `GET /v2/rating-schemes/current`. При необходимости можно использовать отдельный `GET /v2/media/movie/{tmdbId}/ratings` с той же структурой агрегатов.

## Ошибки

Ошибки используют единый формат API:

```json
{"error":{"code":"MEDIA_NOT_FOUND","message":"Media not found in TMDB"}}
```

| HTTP | `error.code` | Условие |
| --- | --- | --- |
| 400 | `BAD_REQUEST` | `tmdbId` не является числом |
| 404 | `MEDIA_NOT_FOUND` | Фильм отсутствует и в БД, и в TMDB |
| 422 | `VALIDATION_ERROR` | `tmdbId` меньше 1 |
| 502 | `TMDB_UNAVAILABLE`, `TMDB_BAD_RESPONSE`, `TMDB_AUTH_FAILED`, `TMDB_CONFIGURATION_ERROR` | Ошибка TMDB при первоначальной загрузке фильма |
| 503 | `TMDB_RATE_LIMITED` | Ограничение TMDB при первоначальной загрузке |

`details` отсутствует, если дополнительных сведений нет, либо содержит массив объектов `{ "field": string, "issue": string }`. При ошибке TMDB уже синхронизированная карточка продолжает отдаваться из БД. Ошибки сервера используют общий код `INTERNAL_ERROR` (`500`).

Swagger/OpenAPI: `GET /v3/api-docs` и `/swagger-ui/index.html` (в текущей конфигурации доступны администратору). Реализацию и примеры проверяет `MovieDetailsIntegrationTest`.
