# MotorPlatforms: Dealership → Customer Inspection

A basic implementation of the inspection flow from the system design interview. A dealership admin sends a customer an SMS link. The customer verifies their phone with a one-time code, fills in a short form, uploads photos and video, and submits. An admin reviews the submission and responds, and the customer gets the response by SMS.

This is a take-home exercise, not production software. The queue, SMS and object storage are mocked behind interfaces. Each mock names its production replacement in its class comment.

**Stack:** Java 21, Spring Boot 3 (Web, Security, Data JPA), Postgres 16, Flyway, Gradle, JUnit 5 and MockMvc. The frontend is React with TypeScript (TSX), bundled by Vite and kept separate from the backend in `frontend/`.

## Running it

Requirements: Docker, and a JDK 17+ to run Gradle. The Gradle toolchain fetches Java 21 if it is missing, and Gradle downloads its own Node to build the frontend.

```bash
cp .env.example .env              # then fill in the secrets (commands are in the file)
docker compose up -d              # Postgres 16 on localhost:55432 (creates motor and motor_test)
./gradlew seedAdmin               # creates the first admin from SEED_ADMIN_EMAIL / SEED_ADMIN_PASSWORD
./gradlew bootRun                 # builds the frontend; Flyway migrates on startup; app on http://localhost:8080
./gradlew test                    # 67 tests against the real Postgres (motor_test), plus a JaCoCo coverage report
./gradlew spotlessCheck           # formatting (google-java-format + Prettier); spotlessApply fixes it
npm --prefix frontend run dev      # optional: hot-reloading views on :5173, proxying /api to bootRun
```

To try the flow:

1. Log in at `http://localhost:8080`.
2. Add a client and click **Send inspection link**.
3. SMS is mocked: the link appears in the app log as `[SMS MOCK] ... /i/<token>`. Open it in a browser.
4. Click **Text me a code**. The code also appears in the log.
5. Fill in the form and submit.
6. Back on the dashboard, the inspection is highlighted within 10 seconds. Open it and send a response.

Set `SERVER_PORT` and `PUBLIC_BASE_URL` if port 8080 is already taken.

## Code structure

The repo is split into a frontend and a backend, and the backend follows MVC: all models in one package, all controllers in another. The views are the React pages in `frontend/`. Infrastructure sits behind small interfaces in `infra`.

```
frontend/                 views: React + TypeScript, bundled by Vite
├── *.html                one shell per page: login, dashboard, customer inspection
└── src                   page entry points, dashboard/ and inspection/ components, api.ts, types.ts
backend/                  Spring Boot app (Gradle subproject; run every command from the repo root)
└── src/main/java/com/motorplatforms
    ├── controller        Routes.java (every HTTP route), handler classes, request validation, exception → error mapping
    ├── model             JPA entities, statuses, request/response records, session principals
    ├── repository        Spring Data repositories, including the conditional status updates
    ├── service           business logic: auth, users, clients, inspections, media, SMS notifications
    ├── security          Spring Security rules, session cookie filter, JWT, cookies, rate limit
    ├── common            typed app settings, ApiException
    └── infra
        ├── crypto        PiiCipher (AES-256-GCM), Secrets (tokens, OTPs, SHA-256), argon2
        ├── queue         JobQueue interface, InMemoryJobQueue (retry/DLQ), IdempotentConsumer
        ├── sms           SmsSender interface, LogSmsSender
        └── storage       ObjectStorage interface, LocalDiskStorage + signed-URL controller
```

At build time Gradle runs `npm ci` and `npm run build` (`tsc` type-check, then Vite) and copies `frontend/dist` into the backend's `static/` resources, so Spring Boot serves the pages from the same origin as the API. That keeps the session cookies `SameSite=Strict` without any CORS setup.

Every error uses one JSON shape: `{"error": {"code", "message", "fields"?}}`. Request bodies are Java records validated with Bean Validation (`Requests.body`). A field error returns 400 with `fields` filled in.

### API

| Who | Endpoint | Notes |
| --- | --- | --- |
| Anyone | `POST /api/auth/login`, `POST /api/auth/logout` | Rate limited |
| Admin | `GET /api/auth/me` | |
| Admin | `GET, POST /api/users` | |
| Admin | `GET, POST /api/clients`, `GET, PUT, DELETE /api/clients/{id}` | |
| Admin | `POST /api/inspections` | Creates the inspection and texts the link |
| Admin | `GET /api/inspections?status=` | Dashboard list, polled |
| Admin | `GET /api/inspections/{id}`, `GET /api/inspections/{id}/media` | |
| Admin | `POST /api/inspections/{id}/response` | Accepts `Idempotency-Key` |
| Link holder | `POST /api/public/session`, `POST /api/public/otp`, `POST /api/public/otp/verify` | Rate limited; body carries the link token |
| Customer | `GET /api/public/inspection`, `GET, POST /api/public/media`, `POST /api/public/media/{id}/confirm` | Customer session cookie |
| Customer | `POST /api/public/submit` | Accepts `Idempotency-Key` |
| Signed URL | `PUT, GET /storage/{key}?...&sig=` | Storage mock only |

## How the requirements are met

### Inspection lifecycle

The statuses run `SENT → OPENED → SUBMITTED → RESPONDED`. Link expiry is checked against the token's expiry time; there is no EXPIRED status.

Each transition is a single conditional update in `InspectionRepository`, for example:

```sql
UPDATE inspections SET status = 'SUBMITTED', ... WHERE id = ? AND status = 'OPENED'
```

If no row changes, another request got there first and this one returns a conflict. Two taps or two tabs produce exactly one submission, and no locks are held. `SubmitTest` fires 10 concurrent submits and asserts that exactly one succeeds.

### "Opened"

`GET /i/{token}` only serves the page. SMS apps fetch links to build previews, so the page load cannot be what marks an inspection as opened. Instead, the page's script calls `POST /api/public/session`, and that call records OPENED. A test checks that a raw GET leaves the status at SENT.

### Link token and OTP

**Link token:**
- 32 bytes from `SecureRandom`, encoded as base64url. It is not a UUID, because a UUID is an identifier, not a secret.
- Only the SHA-256 of the token is stored.
- It expires after 7 days.
- Unknown, expired and already-submitted tokens all get the same `404 LINK_INVALID`.

**OTP:**
- 6 digits, stored as a SHA-256 salted with the inspection id. It expires after 5 minutes and allows 5 attempts.
- Each check is a conditional `UPDATE` that increments `otp_attempts` only while the code is live and attempts remain. Failed checks are not rolled back.
- A correct code is cleared in the same conditional way, so it works only once.

A successful OTP check sets an `HttpOnly; Secure; SameSite=Strict` cookie on path `/api/public`. It holds a JWT with audience `customer` whose subject is the inspection id, so the session covers exactly one inspection. Admin sessions use a separate cookie and the `admin` audience, so neither kind of session can be used as the other.

### PII encryption

`name`, `phone` and `rego` are encrypted with AES-256-GCM by a JPA `AttributeConverter` before they are written to the database. The database only ever holds ciphertext, and a test reads the raw columns to confirm it.

- **Format:** each value is stored as `v1:<base64(iv | ciphertext | tag)>`. The version prefix selects the key, so keys can be rotated.
- **Key handling:** `infra.crypto.PiiCipher` is the only class that holds the key.
- **Decryption:** values are decrypted on the server for authenticated portal users only.
- **Hashed instead:** passwords use argon2id. Link tokens and OTPs use SHA-256 because they are high-entropy or short-lived.

### N+1

Dashboard queries use `@EntityGraph(attributePaths = "client")`. `DashboardTest` creates 5 inspections for 5 different clients and asserts that the list endpoint runs exactly **one** SQL statement, using Hibernate statistics. I confirmed that the test fails when the entity graph is removed.

### Media

The upload works in three steps:
1. The customer asks for an upload URL.
2. The customer `PUT`s the file directly to storage.
3. The customer confirms, and the server checks that the object exists and records its real size.

The database stores only the object key, content type and size.

- **Allowed types:** `image/jpeg`, `image/png`, `image/heic` and `video/mp4`.
- **Size:** the cap is 600MB. The signed URL also binds the declared size and content type, so a client cannot declare a small file and then upload a large one.
- **Storage writes:** the mock writes to a temp file and then moves it into place, so a partial upload is never visible.
- **Originals:** files are never modified.

## Mocks

| What | Mock | Production replacement |
| --- | --- | --- |
| SMS | `LogSmsSender` logs the message with the number masked | Twilio (with delivery status callbacks) |
| Queue | `InMemoryJobQueue`: in-process, 3 retries with exponential backoff, ack timeout, DLQ list | RabbitMQ or SQS (visibility timeout, redrive policy to a DLQ, alarms on DLQ depth) |
| Object storage | `LocalDiskStorage`: local disk behind HMAC-signed, expiring URLs | S3 presigned PUT/GET, multipart upload for video |

In development the SMS log stands in for the customer's phone, so it prints the message body, including the link and code. This is the one deliberate exception to "never log tokens or OTPs". The Twilio adapter must not log bodies. Everything else logs job ids and types only.

## Failure modes

### 1. The ack is not returned

A handler that throws, or does not return within the ack timeout, counts as failed. It is retried 3 times with exponential backoff (0.5s, 1s, 2s). After that, the job moves to the DLQ and an error is logged with its id. `InMemoryJobQueueTest` covers success, retry then success, retry then DLQ, a missing ack (timeout), and a job with no handler.

Delivery is at-least-once, so consumers must be idempotent. Every job carries a UUID, and `IdempotentConsumer` records processed ids in `processed_jobs` and skips redeliveries. That is what prevents a retried SMS job from texting the customer twice. A job is only recorded once it succeeds, so a failed attempt is still retried.

### 2. Duplicate requests

There are two layers of protection:
- **Conditional status updates** (see above). However many requests race, only one transition happens, and the rest get `409`.
- **The `Idempotency-Key` header** on submit and send-response. The key is stored with the transition. A retry carrying the same key gets the same success response with no side effects, so no second SMS goes out. A different key gets `409 ALREADY_SUBMITTED` or `409 ALREADY_RESPONDED`. The pages generate one key per form, so a double tap reuses it.

Side effects are tied to the transition. The response SMS is queued only by the request whose update changed a row. It is published after the transaction commits, so a rolled-back request never sends anything.

### 3. Uploads

Files go directly to storage via presigned URLs, and the API never streams file bytes. The URL is short-lived (15 minutes) and bound to the key, content type and maximum size. The confirm step records the size actually stored.

In production:
- S3 multipart upload for video, so large files can resume after a dropped connection.
- A lifecycle rule to delete objects that were never confirmed.
- Virus scanning and transcoding triggered from S3 events (see below).

### 4. Leaked API keys

**Prevention:**
- Secrets come only from environment variables. `AppProperties` validates them at startup, and the app refuses to start if one is missing.
- `.env` is gitignored and `.env.example` is committed with empty values.
- No secrets appear in code, tests or logs. The test config uses obviously fake values.

**If a key leaks:**
1. Rotate it immediately and revoke the old one with the provider.
2. Check the provider's logs (for example Twilio usage or S3 access logs) for misuse during the exposure window.
3. If the key was committed, rewrite git history (`git filter-repo`) and force-push. Treat the key as compromised regardless, because forks and clones keep it.
4. For `PII_ENCRYPTION_KEY`, add a `v2` key and re-encrypt the rows. The version prefix exists for this.
5. Rotating `JWT_SECRET` logs everyone out, which is the desired effect.

In production, secrets move to a secrets manager, and the PII key moves to KMS.

### 5. High load

- **Scaling:** the API is stateless (JWT cookies, no server sessions), so it scales horizontally behind a load balancer.
- **Slow work:** sending SMS goes through the queue and never blocks a request.
- **Uploads:** they bypass the API entirely.
- **Rate limits:** public endpoints (login and `/api/public/**`) have a per-IP limit. Here it is an in-memory fixed window. In production it moves to the gateway, or to Redis so it holds across instances.
- **Database:** the dashboard query is a single indexed query (`status, created_at`). Further steps would be pagination, read replicas and connection pooling (PgBouncer).

## Assumptions

Each of these is unconfirmed and easy to change.

**Stack and tooling**
- Java 21 / Spring Boot 3 with Gradle (wrapper).
- Postgres 16 in docker compose, accessed through Spring Data JPA (Hibernate), with the schema owned by Flyway.
- Validation uses Jakarta Bean Validation on request records.
- Passwords are hashed with argon2id (Spring Security `Argon2PasswordEncoder`).
- The admin session is a 1-hour JWT in an `HttpOnly; Secure; SameSite=Strict` cookie. The customer session is a separate 30-minute cookie scoped to one inspection.
- CSRF tokens are not used. Session cookies are `SameSite=Strict`, so cross-site requests never carry them.
- The frontend is React with TypeScript (TSX), bundled by Vite and served by Spring Boot. React was chosen for TSX; there is no router, state library or UI kit.
- Tests use JUnit 5, Spring Boot Test and MockMvc against a real Postgres.
- Formatting is enforced by Spotless with google-java-format for Java and Prettier for the frontend; `spotlessCheck` runs both.

**Domain**
- There is one role, admin. Every portal user is an admin and can manage clients, send links, respond to inspections, and list or create other admins.
- The first admin is created by a seed script (`./gradlew seedAdmin`).
- Rego is trimmed and uppercased, with no state-specific format validation.
- Phone numbers without a country code are treated as Australian and stored in E.164.
- Admins see decrypted PII, because they need the name to pick a client.
- A client with inspections cannot be deleted (`409`).
- The dashboard shows all inspections, highlights SUBMITTED ones and refreshes by polling every 10 seconds.
- "Parse the information" means validating the submission against a schema and mapping it onto the inspection row. Invalid input is rejected with field-level errors.

**The inspection form and response**
- Form fields:
  - Mileage: required, 0–2,000,000 km.
  - Condition notes: required, up to 5,000 characters.
  - Photos and video: optional, any number.
- The admin response is free text (up to 1,000 characters) and is sent in full in the SMS.
- SMS wording for the OTP and the response:
  - OTP: `Your MotorPlatforms verification code is 123456. It expires in 5 minutes.`
  - Response: `Hi, this is MotorPlatforms. We have reviewed your car inspection: <response>`
- The link SMS uses the exact text from the brief.

**Security details**
- Requesting a new OTP replaces the old one and resets the attempt counter. The per-IP rate limit bounds how many codes can be requested.
- The session JWT is trusted until it expires, so a removed user keeps access until then, after at most 1 hour. Production would add a revocation check or shorter tokens with refresh.
- Signed storage URLs last 15 minutes.
- The per-IP limit is 60 requests per minute on public endpoints.
- Admin passwords must be at least 12 characters.

## Out of scope (what production would add)

- **Transactional outbox:** SMS jobs are published right after commit. A crash between the commit and the publish would lose the job. An outbox table, written in the same transaction and relayed to the queue, closes that gap.
- **Multi-tenancy:** a dealership id on every row, plus tenant-scoped queries and authorisation.
- **Security services:** KMS for the PII key (envelope encryption) and a secrets manager for everything else. Storage-level encryption at rest on top.
- **CDN and media processing:** a CDN for static files and media. Virus scanning and transcoding of uploads (HEIC → JPEG, video → web formats), triggered by S3 events and stored as derivatives. Originals stay untouched.
- **Rate limiting and abuse protection:** at the gateway or in Redis, and per phone number for OTP sends.
- **Session revocation:** refresh tokens and a deny-list, plus disabling users.
- **Rego search:** encrypted fields cannot be searched or uniquely indexed. If search by rego is needed, add an HMAC "blind index" column.
- **Dashboard:** pagination and filtering on the server, and possibly server-sent events instead of polling.
- **Observability:** structured logs, metrics (queue depth, DLQ size, SMS failures), tracing and alerting.
- **Audit log:** who viewed or changed PII and who responded.
- **Headers and HTTPS:** a Content-Security-Policy, and HTTPS everywhere with HSTS.
- **CI:** build, test against a Postgres service container, `spotlessCheck` and dependency scanning.
