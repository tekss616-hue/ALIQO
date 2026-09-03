# Batch 1 — Foundation, Accounts, Profiles, Friends

## Scope delivered

This batch establishes ALIQO as a real Android + backend project, not a preview. It includes a PostgreSQL data model, secure password hashing, access/refresh token auth, registration, login, logout, token rotation, password reset foundation, current-user profile, profile editing, username search, friend requests, acceptance, removal, blocking/unblocking, soft account deletion, and the initial Android authentication screen.

## Local backend

1. Copy `backend/.env.example` to `backend/.env` and replace JWT secrets with long random values.
2. Run `docker compose up -d` from `backend/`.
3. Run `npm install` in `backend/`.
4. Run `npx prisma generate`.
5. Run `npx prisma migrate dev --name batch1`.
6. Run `npm run dev`.

The API listens on `http://localhost:3000/api/v1` by default.

## Android

Open the repository in Android Studio. The emulator reaches the local backend at `http://10.0.2.2:3000/api/v1/`.

For a physical phone, change `API_BASE_URL` in `app/build.gradle.kts` to the HTTPS address of the backend. Cleartext HTTP is enabled only for local Batch-1 testing and must be disabled before production.

Package name: `com.aliqo.app`
Version code: `1`
Version name: `0.1.0`

## Done-check

- Create two accounts with different email/username values.
- Verify duplicate email/username is rejected.
- Log in with each account.
- Search for the other username.
- Send and accept a friend request using the API.
- Verify both users appear in the friends list.
- Block one user and verify the friendship is removed and search/request interaction is denied.
- Unblock.
- Edit profile fields.
- Request a password reset and complete it in development.
- Delete an account and verify old tokens stop working.
- Register the configured primary-admin email and verify its role is `PRIMARY_ADMIN`; all other new users must be `USER`.

## Security notes

No real API keys, signing passwords, or admin passwords belong in source control. `AI_API_KEY` is intentionally empty. The primary admin is assigned by normalized email during registration; its password is never stored in code. Production must use HTTPS and a real email provider for reset links.
