# Echo

Echo is a Spring Boot backend for movie reviews. It provides registration, login, session-based bearer tokens, versioned film ratings, moderated review CRUD, answers, profiles and personal favorites.

## Stack

- Java 21
- Spring Boot 3.4.3
- Spring Web
- Spring Data JPA
- Spring Security
- H2 for local development
- PostgreSQL-ready configuration for non-dev environments

## Profiles

### Default profile

The default configuration is production-oriented:

- `spring.jpa.hibernate.ddl-auto=validate`
- H2 console is disabled
- database settings are expected from environment variables
- CORS is restricted to configured origins

Required environment variables:

| Variable | Purpose |
|---|---|
| `DB_URL` | JDBC URL |
| `DB_USERNAME` | Database username |
| `DB_PASSWORD` | Database password |
| `DB_DRIVER_CLASS_NAME` | JDBC driver class, defaults to `org.postgresql.Driver` |
| `APP_AUTH_TOKEN_SECRET` | Secret for HMAC signing of access and refresh tokens |

Optional environment variables:

| Variable | Default |
|---|---|
| `SERVER_PORT` | `3000` |
| `APP_JPA_DDL_AUTO` | `validate` |
| `H2_CONSOLE_ENABLED` | `false` |
| `H2_CONSOLE_PATH` | `/h2-console` |
| `APP_AUTH_SESSION_ACCESS_TTL` | `PT30M` |
| `APP_AUTH_SESSION_REFRESH_TTL` | `P30D` |
| `APP_AUTH_EMAIL_VERIFICATION_TTL` | `P1D` |
| `APP_AUTH_PASSWORD_RECOVERY_TTL` | `PT30M` |
| `APP_EMAIL_PROVIDER` | `in-memory` |
| `APP_EMAIL_FROM` | `no-reply@echo.local` |
| `APP_EMAIL_VERIFICATION_URL_TEMPLATE` | `http://localhost:3000/verify-email?token=%s` |
| `APP_EMAIL_PASSWORD_RECOVERY_URL_TEMPLATE` | `http://localhost:3000/reset-password?token=%s` |
| `APP_SECURITY_RATE_LIMIT_LOGIN_CAPACITY` | `10` |
| `APP_SECURITY_RATE_LIMIT_LOGIN_WINDOW` | `PT1M` |
| `APP_SECURITY_RATE_LIMIT_REGISTER_CAPACITY` | `10` |
| `APP_SECURITY_RATE_LIMIT_REGISTER_WINDOW` | `PT5M` |
| `APP_SECURITY_RATE_LIMIT_EMAIL_VERIFICATION_REQUEST_CAPACITY` | `5` |
| `APP_SECURITY_RATE_LIMIT_EMAIL_VERIFICATION_REQUEST_WINDOW` | `PT15M` |
| `APP_SECURITY_RATE_LIMIT_EMAIL_VERIFICATION_CONFIRM_CAPACITY` | `10` |
| `APP_SECURITY_RATE_LIMIT_EMAIL_VERIFICATION_CONFIRM_WINDOW` | `PT15M` |
| `APP_SECURITY_RATE_LIMIT_PASSWORD_RECOVERY_REQUEST_CAPACITY` | `5` |
| `APP_SECURITY_RATE_LIMIT_PASSWORD_RECOVERY_REQUEST_WINDOW` | `PT15M` |
| `APP_SECURITY_RATE_LIMIT_PASSWORD_RECOVERY_CONFIRM_CAPACITY` | `10` |
| `APP_SECURITY_RATE_LIMIT_PASSWORD_RECOVERY_CONFIRM_WINDOW` | `PT15M` |
| `APP_CORS_ALLOWED_ORIGINS` | `http://localhost:3000,http://127.0.0.1:3000` |
| `HIBERNATE_FORMAT_SQL` | `true` |

When `APP_EMAIL_PROVIDER=smtp`, configure the SMTP integration through standard Spring Mail properties:

| Variable | Purpose |
|---|---|
| `SPRING_MAIL_HOST` | SMTP host |
| `SPRING_MAIL_PORT` | SMTP port |
| `SPRING_MAIL_USERNAME` | SMTP username |
| `SPRING_MAIL_PASSWORD` | SMTP password |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH` | SMTP auth flag |
| `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE` | STARTTLS flag |

### Dev profile

Use the `dev` profile for local development:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

The `dev` profile uses:

- file-based H2 database at `./data/echo`
- `sa` / `sa` credentials
- `spring.jpa.hibernate.ddl-auto=update`
- enabled H2 console at `/h2-console`
- local HMAC signing secret for session tokens
- in-memory email delivery provider for local testing

For an offline dev run, set `$env:APP_TMDB_PROVIDER='stub'` before starting the application. Live TMDB mode requires `APP_TMDB_PROVIDER=http`, a valid `APP_TMDB_ACCESS_TOKEN` and network access to `api.themoviedb.org`.

## Run

Default profile requires the database environment variables to be set first. Example:

```powershell
$env:DB_URL='jdbc:postgresql://localhost:5432/echo'
$env:DB_USERNAME='postgres'
$env:DB_PASSWORD='postgres'
mvn spring-boot:run
```

or with the dev profile:

```powershell
$env:SPRING_PROFILES_ACTIVE='dev'
mvn spring-boot:run
```

## Test

```powershell
mvn test
```

Tests use an isolated in-memory H2 database from `src/test/resources/application.yaml`.

## Authentication

Authenticated endpoints expect signed `Bearer` access tokens:

```text
Authorization: Bearer <access-token>
```

On successful authorization the API returns:

- `tokenType`: `Bearer`
- `accessToken`
- `refreshToken`

Tokens are signed and checked on every request. Refresh rotates both tokens and invalidates the previous refresh token. Active tokens are also tracked in the `sessions` table.

Registration with `username` and `password` (without email) creates an account and immediately returns bearer tokens. Usernames are unique without regard to case. A taken username returns `409 USERNAME_TAKEN`.

When an optional email is supplied, registration creates a pending account and sends a verification email; bearer tokens are issued after confirmation.

Email verification uses a one-time token, marks the user as verified, and returns fresh bearer tokens.

Login accepts `username` and `password`; email login remains supported for existing accounts. Login is blocked for accounts with an unverified email using `EMAIL_NOT_VERIFIED`.

New passwords must contain at least 8 characters and no more than 72 UTF-8 bytes. The byte limit is also enforced on login and password reset to prevent BCrypt from silently ignoring a suffix.

The backend session lifecycle consists of `GET /v1/me`, `POST /v1/auth/refresh`, and `POST /v1/auth/logout`. Client-side storage and restoration of these tokens are intentionally outside this backend change. See [the backend auth implementation and acceptance notes](docs/AUTH_IMPLEMENTATION_RU.md).

Migration V11 adds a unique canonical username column. Check existing accounts for case-insensitive duplicates before applying it; conflicting accounts are not renamed or deleted automatically.

Password recovery creates a one-time token, emails a recovery link, and invalidates active sessions after the password is reset. Recovery requests are ignored for unknown or unverified emails.

Protected business endpoints now use Spring Security bearer authentication. Technical routes such as `/actuator/**` and `/v3/api-docs/**` are closed for anonymous users and require an authenticated `ADMIN`.

## API

### Auth

- `POST /v1/auth/register`
- `POST /v1/auth/login`
- `POST /v1/auth/refresh`
- `POST /v1/auth/email-verification/request`
- `POST /v1/auth/email-verification/confirm`
- `POST /v1/auth/password-recovery/request`
- `POST /v1/auth/password-recovery/confirm`
- `POST /v1/auth/logout`

### Users

- `GET /v1/me`
- `GET /v1/users/{userId}`

### Reviews

- `GET /v2/reviews` — public published reviews, filters, sorting and pagination
- `POST /v2/reviews` — create a draft or submit to moderation
- `GET /v2/reviews/mine` — own drafts, pending and published reviews
- `GET /v2/reviews/{reviewId}`
- `PUT /v2/reviews/{reviewId}` — edit own review
- `DELETE /v2/reviews/{reviewId}`
- `GET /v2/reviews/moderation` — administrator queue
- `POST /v2/reviews/{reviewId}/publish` — administrator approval
- `PUT /v2/reviews/{reviewId}/like`
- `DELETE /v2/reviews/{reviewId}/like`

Legacy `/v1/reviews` reads and deletions remain available. Legacy POST/PATCH writes return `409 LEGACY_REVIEW_WRITE_DISABLED`; clients must use v2 to avoid bypassing the rating and moderation workflow.

### Structured ratings

- `GET /v2/rating-schemes/current` — parameter names, codes, descriptions and formula metadata
- `GET /v2/rating-schemes/{version}`
- `PUT /v2/me/ratings/{mediaType}/{tmdbId}` — save the five original integer scores
- `GET /v2/me/ratings/{mediaType}/{tmdbId}`
- `DELETE /v2/me/ratings/{mediaType}/{tmdbId}` — requires deleting the linked review first
- `GET /v2/users/{userId}/ratings`
- `GET /v2/media/{mediaType}/{tmdbId}/ratings` — parameter and final-score averages by scheme
- `GET /v2/media/movie/{tmdbId}/details` — complete movie card and versioned community rating averages in one public response; [Frontend contract](docs/MOVIE_DETAILS_API_RU.md)

The four form parameters are averaged and multiplied by 10. Impression is a separate multiplier, `0.4 + 0.6 * (impression - 1) / 9`. Only the final score is rounded to an integer using `HALF_UP`; `9/8/7/9/9` gives `77/100`. Raw scores and their immutable scheme version are stored independently of the cached result. Existing scalar ratings remain preserved as legacy data.

Save a structured rating before creating a v2 review. Submitted reviews are private until an administrator publishes them. Changing a published review's text or its linked rating returns it to moderation. Migrations V15 and V16 add the schema and preserve historical ratings/reviews. See the [ratings contract](docs/RATINGS_API_RU.md) and [review contract](docs/REVIEWS_API_RU.md) for request examples and migration details. Swagger is available at `/swagger-ui/index.html` and `/v3/api-docs` with an authenticated administrator account.

### Answers

- `GET /v1/reviews/{reviewId}/answers`
- `POST /v1/reviews/{reviewId}/answers`
- `PATCH /v1/answers/{answerId}`
- `DELETE /v1/answers/{answerId}`

## Request notes

- registration/login payloads are JSON
- registration without email returns `user`, `tokens`, and `emailVerificationRequired=false`; registration with email returns a pending user and `emailVerificationRequired=true`
- list endpoints support pagination through `limit` and `offset`
- review listing also supports `tmdbMovieId`, `userId`, `sort`, and `order`

## User profiles

The profile API stores avatar, bio and content preferences, and aggregates review, rating and like statistics from account-owned database records. `GET /v1/me/profile` returns the private profile, while `GET /v1/users/{userId}/profile` omits private email data. Ratings, review likes and author likes have authenticated mutation endpoints. See [the profile API contract](docs/USER_PROFILE_API_RU.md).

## TMDB media

The public `/v1/media` API provides movie and TV lists, search, recommendations and detail cards. Detail cards are cached in the database and include director, cast and crew with image URLs. Migration V13 adds structured credits to existing media records. See [the TMDB API contract and error codes](docs/TMDB_API_RU.md).

## Personal favorites

The authenticated `/v1/me/preferences` API saves movies, TV releases, actors and directors to the current account. `GET` lists favorites with pagination and an optional type filter; `GET`, `PUT` and `DELETE /v1/me/preferences/{type}/{tmdbId}` check, add and remove an individual favorite. Repeated additions are idempotent, including concurrent requests. Migration V14 adds database ownership, release foreign keys and unique constraints. Favorites survive logout and login. See [the preferences API contract and FE examples](docs/PREFERENCES_API_RU.md).

## Error format

Errors are returned as JSON:

```json
{
  "error": {
    "code": "VALIDATION_ERROR",
    "message": "Validation failed",
    "details": [
      {
        "field": "email",
        "issue": "must be a well-formed email address"
      }
    ]
  }
}
```
