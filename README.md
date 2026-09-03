# ALIQO

ALIQO is a match-first social chat and challenge platform for Android.

## Delivery plan

The product is built in six testable batches. Batch 1 establishes the production foundation: Android app shell, secure backend, PostgreSQL schema, authentication, profiles, user search, friendships, blocking, account recovery, and account deletion.

## Architecture

- Android: Kotlin + Jetpack Compose + Material 3
- Backend: NestJS + Prisma + PostgreSQL
- Auth: short-lived JWT access tokens + rotating refresh tokens (hashed at rest)
- Security: Argon2 password hashing, validation, rate limiting, RBAC-ready guards
- API secrets: backend environment only; never embedded in Android
- Localization: Arabic RTL and English LTR foundation

See `docs/BATCH_1.md` for setup and test instructions.
