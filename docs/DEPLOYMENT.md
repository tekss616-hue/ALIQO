# ALIQO Backend Deployment

## Goal
Deploy the NestJS + Prisma backend behind HTTPS and connect the physical Android app to it.

## Required production environment variables

- `DATABASE_URL` — PostgreSQL connection string.
- `JWT_ACCESS_SECRET` — strong random secret, minimum 32 characters.
- `PRIMARY_ADMIN_EMAIL` — initial primary admin email.
- `ACCESS_TOKEN_TTL` — default `15m`.
- `REFRESH_TOKEN_TTL_DAYS` — default `30`.
- `CORS_ORIGIN` — allowed frontend origins if a web/admin frontend is added.
- `PORT` — normally supplied by the hosting platform.
- `AI_API_KEY` — leave empty until AI Game Master work begins.

Never commit real secrets to GitHub.

## Container

The backend is deployable from `backend/Dockerfile`.

The container:
1. installs dependencies,
2. generates Prisma Client,
3. compiles NestJS,
4. applies the current Prisma schema to PostgreSQL with `prisma db push`,
5. starts the API on `0.0.0.0:$PORT`.

For mature production releases, replace `prisma db push` with committed Prisma migrations and `prisma migrate deploy`.

## Health check

After deployment, verify:

`GET https://<backend-host>/api/v1/health`

Expected response:

```json
{"ok":true,"service":"aliqo-backend"}
```

## Android connection

The current debug app uses the emulator-only address `http://10.0.2.2:3000/api/v1/`.

After an HTTPS backend URL exists, replace `API_BASE_URL` in `app/build.gradle.kts` with:

`https://<backend-host>/api/v1/`

Then run the GitHub Actions Android debug APK workflow again and install the new APK on the physical phone.

## Batch 1 production smoke test

1. Health endpoint returns 200.
2. Register two accounts.
3. Duplicate email and username are rejected.
4. Login works for both accounts.
5. `/users/me` returns the authenticated user.
6. Search the second username.
7. Send and accept a friend request.
8. Friends list contains both users.
9. Block/unblock works.
10. Profile editing works.
11. Primary admin email receives `PRIMARY_ADMIN`; all other accounts receive `USER`.
