# MotorPlatforms: Dealership → Customer Inspection

A dealership admin texts a customer a link. The customer verifies their phone with a one-time code, fills in a form, uploads photos and video, and submits. An admin responds, and the customer gets the response by SMS.

This is a take-home exercise, not production software. The queue, SMS and object storage are mocked behind interfaces, and each mock's class comment names its production replacement.

**Stack:** Java 21, Spring Boot 3 (Web, Security, Data JPA), Postgres 16, Flyway, Gradle, JUnit 5 + MockMvc. Frontend: React + TypeScript, bundled by Vite, in `frontend/`.

## Running it

Requires Docker and a JDK 17+. Gradle fetches Java 21 and Node if they are missing.

```bash
cp .env.example .env              # then fill in the secrets (commands are in the file)
docker compose up -d              # Postgres 16 on localhost:55432
./gradlew seedAdmin               # first admin from SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD
./gradlew bootRun                 # builds the frontend, runs Flyway, serves http://localhost:8080
./gradlew test                    # 67 tests against real Postgres, plus a JaCoCo report
./gradlew spotlessCheck           # google-java-format + Prettier; spotlessApply fixes it
npm --prefix frontend run dev     # optional: hot reload on :5173, proxying /api to bootRun
```

To try it: log in, add a client, click **Send inspection link**. SMS is mocked, so the link (`[SMS MOCK] ... /i/<token>`) and the OTP appear in the app log. Open the link, request a code, submit the form, then respond from the dashboard (it polls every 10 seconds). Set `SERVER_PORT` and `PUBLIC_BASE_URL` if port 8080 is taken.

## Code structure

```
frontend/                 React views: login, dashboard, customer inspection
backend/src/main/java/com/motorplatforms
├── controller            Routes.java (every route), handlers, validation, error mapping
├── model                 JPA entities, statuses, request/response records
├── repository            Spring Data repositories, including conditional status updates
├── service               auth, users, clients, inspections, media, notifications
├── security              Spring Security rules, session cookies, JWT, rate limit
├── common                typed settings, ApiException
└── infra                 crypto, queue, sms, storage: each an interface plus a mock
```

Gradle builds the frontend into the backend's `static/` resources, so pages and API share one origin and cookies stay `SameSite=Strict` without CORS. Every error has the shape `{"error": {"code", "message", "fields"?}}`; request records are validated with Bean Validation and field errors return 400.

## How the requirements are met

- **Statuses** run `SENT → OPENED → SUBMITTED → RESPONDED`. Each transition is one conditional update (`... WHERE id = ? AND status = 'OPENED'`); 0 rows changed means `409`. `SubmitTest` fires 10 concurrent submits and asserts exactly one succeeds. Expiry is checked on the token, not stored as a status.
- **"Opened"** is recorded by `POST /api/public/session`, called by the page's script, not by `GET /i/{token}`, so SMS link previews don't count. A test checks a raw GET leaves the status at SENT.
- **Link token:** 32 bytes from `SecureRandom`, base64url, only its SHA-256 stored, 7-day expiry. Unknown, expired and submitted tokens all return `404 LINK_INVALID`.
- **OTP:** 6 digits, salted SHA-256, 5 minutes, 5 attempts. Attempts and use are conditional updates, so a code works once and failed checks always count. Success sets an `HttpOnly; Secure; SameSite=Strict` cookie holding a JWT (audience `customer`) for that one inspection. Admin sessions use a separate cookie and audience.
- **PII:** `name`, `phone` and `rego` are encrypted with AES-256-GCM by a JPA converter, stored as `v1:<base64(iv|ciphertext|tag)>` so the key can be rotated. `infra.crypto.PiiCipher` is the only class with the key. A test reads raw columns to confirm only ciphertext is stored. Passwords use argon2id.
- **N+1:** the dashboard uses `@EntityGraph`. `DashboardTest` asserts the list runs exactly one SQL statement for 5 clients, and fails without the graph.
- **Media:** request an upload URL, `PUT` directly to storage, then confirm (the server records the real size). Only key, type and size are stored. Allowlist is `image/jpeg`, `image/png`, `image/heic`, `video/mp4`; the cap is 600MB, and the signed URL binds the declared size and type. Originals are never modified.

## Mocks

| What | Mock | Production replacement |
| --- | --- | --- |
| SMS | `LogSmsSender` logs the message, number masked | Twilio |
| Queue | `InMemoryJobQueue`: 3 retries with backoff, ack timeout, DLQ | RabbitMQ or SQS with a DLQ redrive policy |
| Object storage | `LocalDiskStorage` behind HMAC-signed, expiring URLs | S3 presigned URLs, multipart for video |

The SMS mock prints the message body (link and OTP) because the log stands in for the customer's phone. This is the one deliberate exception to "never log tokens or OTPs"; the Twilio adapter must not log bodies.

## Failure modes

1. **Ack not returned.** A handler that throws or times out is retried 3 times (0.5s, 1s, 2s), then moved to the DLQ and logged. Delivery is at-least-once, so `IdempotentConsumer` records each job's UUID on success and skips redeliveries: a retried SMS job never texts twice. Covered by `InMemoryJobQueueTest`.
2. **Duplicate requests.** Conditional updates let only one racing request win; the rest get `409`. Submit and send-response also accept an `Idempotency-Key`: the same key gets the same success with no side effects, a different key gets `409`. The SMS is queued only by the winning request, after commit.
3. **Uploads.** Files go straight to storage via 15-minute signed URLs; the API never streams bytes. Production adds S3 multipart for video and a lifecycle rule for unconfirmed objects.
4. **Leaked API keys.** Secrets come only from env vars, validated at startup; `.env` is gitignored and `.env.example` committed. If a key leaks: rotate and revoke it, check provider logs for misuse, and rewrite git history if it was committed (treating it as compromised regardless). A leaked `PII_ENCRYPTION_KEY` means adding a `v2` key and re-encrypting.
5. **High load.** The API is stateless (JWT cookies) and scales horizontally. SMS goes through the queue, uploads bypass the API, public endpoints have a per-IP limit, and the dashboard is one indexed query.

## Assumptions

- **Stack:** Java 21 / Spring Boot 3 / Gradle; Postgres 16 with JPA and Flyway; Jakarta Bean Validation; JUnit 5 + MockMvc against real Postgres; Spotless (google-java-format + Prettier). React was chosen for TSX, with no router, state library or UI kit.
- **Auth:** argon2id passwords. Admin session is a 1-hour JWT cookie; customer session is a separate 30-minute cookie for one inspection. No CSRF tokens, since cookies are `SameSite=Strict`. A removed admin keeps access until their JWT expires.
- **Domain:** one role, admin, who can do everything including creating admins. The first admin comes from `./gradlew seedAdmin`. Admins see decrypted PII. A client with inspections cannot be deleted (`409`).
- **Client data:** rego is trimmed and uppercased with no state format check. Phones without a country code are treated as Australian and stored in E.164.
- **Form:** mileage (required, 0–2,000,000 km), condition notes (required, up to 5,000 chars), any number of optional photos and videos. "Parse the information" means schema validation with field-level errors, mapped onto the inspection row.
- **Response:** free text up to 1,000 characters, sent in full by SMS as `Hi, this is MotorPlatforms. We have reviewed your car inspection: <response>`. The OTP SMS reads `Your MotorPlatforms verification code is 123456. It expires in 5 minutes.`
- **Limits:** a new OTP replaces the old one and resets attempts. Signed URLs last 15 minutes. Public endpoints allow 60 requests per minute per IP. Admin passwords are at least 12 characters. The dashboard shows all inspections, highlighting SUBMITTED ones.

## What production would add

- A transactional outbox, so a crash between commit and publish can't lose an SMS job.
- Multi-tenancy (a dealership id on every row).
- KMS for the PII key, a secrets manager, and storage-level encryption at rest.
- A CDN, virus scanning and transcoding of uploads, stored as derivatives.
- Rate limiting at the gateway or in Redis, plus per-phone OTP limits.
- Session revocation, an audit log, CSP and HSTS.
- An HMAC blind index if rego search is needed.
- Server-side pagination, observability (metrics, tracing, DLQ alerts) and CI.
