# ALIQO Batch 2 — Production Setup

Batch 2 application code is designed to keep media attachments hidden until a secure media provider is configured, and to keep push delivery disabled until Firebase Cloud Messaging (FCM) credentials are configured. Do not commit secrets to GitHub.

## 1. Secure media storage

Recommended production shape:

Android app -> ALIQO backend -> trusted signing endpoint -> object storage upload URL -> object storage.

The current backend expects:

- `MEDIA_PROVIDER=http-presigned`
- `MEDIA_SIGNING_ENDPOINT=https://...`
- `MEDIA_BASE_URL=https://...`
- optional `MEDIA_ACCESS_KEY`
- optional `MEDIA_SECRET_KEY`
- optional `MEDIA_BUCKET`
- optional `MEDIA_REGION`

The signing endpoint must accept HTTPS POST JSON with:

```json
{
  "objectKey": "...",
  "mimeType": "...",
  "byteSize": 123,
  "sha256": "...",
  "expiresAt": "..."
}
```

It must return a short-lived HTTPS upload URL and may return safe upload headers. Never return permanent storage credentials to the app.

Suggested providers: Cloudflare R2, Amazon S3, or another S3-compatible object store. The provider itself is less important than keeping the bucket private for writes and using short-lived presigned upload URLs.

After configuration, verify `GET /api/v1/media/capabilities` returns `enabled: true`. Only then should attachment UI be exposed.

## 2. Firebase Cloud Messaging

Create a Firebase project for ALIQO and add Android package `com.aliqo.app`.

Android side:

1. Download the Firebase Android configuration file from Firebase Console.
2. Add it only to the Android project in the expected app module location; do not paste credentials into source files.
3. Enable Firebase Messaging dependencies/plugin.
4. Obtain the device FCM registration token at runtime and register it with ALIQO using `POST /api/v1/devices/register`.
5. Refresh the registered token whenever Firebase rotates it.

Backend side:

Configure deployment secrets only:

- `FCM_PROJECT_ID`
- `FCM_CLIENT_EMAIL`
- `FCM_PRIVATE_KEY`

The private key must preserve newlines correctly in the deployment environment. Do not commit a service-account JSON file.

Before calling push production-ready, perform a real-device test with the app backgrounded and confirm receipt of a notification from the backend.

## 3. Release gate

Batch 2 can be considered code-complete before external credentials are present, but these features are not production-enabled until their external services are configured and tested:

- media upload and attachment picker
- background push notifications

Realtime in-app chat and database notifications do not depend on FCM and remain functional while the app is connected.

## 4. Security rules

- Never place storage secret keys, Firebase private keys, JWT secrets, or AI keys in the APK.
- Keep deployment secrets in Render/Firebase/provider secret stores.
- Keep media write access short-lived and scoped to one object.
- Reject non-HTTPS media URLs in production.
- Revoke or rotate any credential that is accidentally exposed.
