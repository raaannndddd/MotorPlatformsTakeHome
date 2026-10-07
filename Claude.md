# CLAUDE.md

## Project

Take-home exercise: a basic implementation of the "Dealership -> Customer Inspection" system from the system design interview.

The reviewers want to see **approach, code structure, coding standards and implementation practices**. It is not production software. Infrastructure (queue, SMS, object storage) is mocked behind interfaces, and each mock has a comment saying what replaces it in production.

Items marked **[ASSUMPTION]** are not confirmed. Each must also be listed in the README. Items marked **[OPEN]** must be answered before that part is built.

## Stack

- **Language and runtime:** Java 21 LTS.
- **Framework:** Spring Boot 3 (Spring Web, Spring Security). MVC layer packages plus small interfaces give the structure.
- Build: Gradle with the wrapper (`./gradlew`).
- Database: Postgres 16 in docker compose, accessed with Spring Data JPA (Hibernate). Flyway owns the schema.
  - `JOIN FETCH` / `@EntityGraph` makes eager loading explicit.
  - A `@Modifying` JPQL update returns the changed row count, which is what conditional status updates need.
- **[ASSUMPTION] Validation:** Jakarta Bean Validation (`@Valid` on request records) at every request boundary.
- **[ASSUMPTION] Auth:**
  - Passwords hashed with argon2 (Spring Security `Argon2PasswordEncoder`).
  - Admin session is a short-lived JWT in an HttpOnly, Secure, SameSite=Strict cookie.
  - Customer session is a separate cookie scoped to one inspection.
- **Frontend:** React with TypeScript (`.ts` / `.tsx`), bundled by Vite. **[ASSUMPTION]** React was chosen for TSX.
  - Lives in `frontend/`, one HTML entry per page: login, dashboard and the customer inspection page.
  - Gradle builds it with a Gradle-managed Node (node-gradle plugin) and copies `frontend/dist` into the backend's `static/` resources, so Spring Boot serves it from the same origin as the API. Only a JDK is needed.
  - `npm run build` type-checks with `tsc` before bundling. No router, state library or UI kit (YAGNI).
  - Keep the UI plain; it is not what is being assessed.
- **[ASSUMPTION] Tests:** JUnit 5, Spring Boot Test and MockMvc against a real Postgres in docker.
- **[ASSUMPTION] Tooling:** Spotless with google-java-format for Java, and Prettier for the frontend. `spotlessCheck` and `spotlessApply` run both.
- **Commands:** `docker compose up -d`, `./gradlew bootRun` (Flyway migrates on startup), `./gradlew seedAdmin`, `./gradlew test`, `./gradlew spotlessCheck`, `npm --prefix frontend run dev` for hot reload against `bootRun`.

## Scope

**Build:**
- Admin login and user management.
- Client CRUD.
- Send an inspection link by SMS.
- The "opened" status.
- Customer OTP, form, media upload and submit.
- Admin review and response.
- An SMS to the customer once the response is sent.

**Mock behind an interface:**

| What | Mock | Production replacement |
| --- | --- | --- |
| SMS | Logs the message | Twilio |
| Queue | In-process, with retry and DLQ | RabbitMQ or SQS |
| Object storage | Local disk, with presigned-style upload URLs | S3 |

**Do not build (README only):** multi-tenancy, CDN, virus scanning, transcoding, the transactional outbox, a secrets manager, rate limiting beyond a simple per-IP limit on public endpoints.

## Domain

- **Role:** there is one role, `admin`.
  - Admins can view all users, create other admins, manage clients, send links and respond to inspections.
  - **[ASSUMPTION]** The first admin is created by a seed script.
- **Client:** `name`, `car_model`, `phone` and `rego` are all mandatory.
  - Phone is stored in E.164 format.
  - **[ASSUMPTION]** Rego is trimmed and uppercased, with no state-specific format validation.
- **Inspection statuses:** `SENT -> OPENED -> SUBMITTED -> RESPONDED`.
  - Expiry is checked on the link token, not stored as a status (YAGNI).
- **SMS text (exact):** `Hi, this is MotorPlatforms. Please take 5 minutes to fill out this car inspection form: [link]`

## Flows

**Admin**
1. Log in.
2. Pick or create a client.
3. Send the link.
4. Watch the status on the dashboard.
5. Open a submitted inspection.
6. Write a response and send it.

**Customer**
1. Open the link.
2. Request an OTP.
3. Enter the OTP.
4. Fill in the form and upload media.
5. Submit.
6. Receive an SMS when an admin responds.

Rules for these flows:

- **"Opened"** is recorded when the page loads in a browser and calls the session endpoint, not on the raw GET. SMS link previews fetch URLs automatically and would otherwise mark every link as opened.
- **Admin notification:**
  - The dashboard shows submitted inspections, highlighted, and refreshes by polling.
  - No websockets (YAGNI).
- **Parser:**
  - "Parse the information" means validating the submission against a schema and mapping it to DB rows.
  - Invalid input is rejected with field-level errors.

## Technical rules

- **Link token:**
  - 32 random bytes from a CSPRNG, URL-safe.
  - Only its SHA-256 hash is stored.
  - Expires after 7 days.
  - Not a UUID: a UUID is an identifier, not a secret.
  - The same error is returned for unknown, expired and already-submitted tokens.
- **OTP:**
  - 6 digits, stored hashed, expires after 5 minutes, maximum 5 attempts.
  - A successful OTP check exchanges the token for an HttpOnly session cookie scoped to one inspection.
- **Concurrency:**
  - Status changes use a conditional update, for example `UPDATE ... SET status='SUBMITTED' WHERE id=? AND status='OPENED'`.
  - If 0 rows change, the request is a conflict.
  - This makes a double-tap or two tabs produce exactly one submission, without holding locks.
  - Use `SELECT ... FOR UPDATE` only where a read-then-write cannot be expressed as one update.
- **Idempotency:**
  - Submit and send-response accept an `Idempotency-Key` header (a UUID).
  - Queue jobs carry a UUID and consumers skip keys they have already processed, so an SMS is never sent twice.
- **Queue:**
  - `publish(job)` and `subscribe(type, handler)` are behind an interface.
  - A failed handler is retried up to 3 times with backoff, then the job moves to the DLQ and is logged.
  - "No ack" is treated as a failure.
- **Two-way encryption (client PII):**
  - PII is encrypted reversibly so the portal can read it back.
  - Secrets that never need reading back (passwords, link tokens, OTPs) use one-way hashing instead.
  - **Encrypt:** `name`, `phone` and `rego` are encrypted by the app with AES-256-GCM before they are written to the DB. The DB only ever holds ciphertext.
  - **Decrypt:** values are decrypted on the server when an authenticated portal user reads them. The key comes from `PII_ENCRYPTION_KEY` and never reaches the browser.
  - **Code location:** all of this lives in one module, `infra/crypto`, with `encrypt` and `decrypt`. Nothing else touches the key.
  - **Ciphertext format:** each value carries a key version prefix, so the key can be rotated later.
  - **[ASSUMPTION]** Admins see decrypted values, because they need the name to pick a client. Revealing values only on demand is not built (YAGNI).
  - **Known trade-off:** encrypted fields cannot be searched or uniquely indexed. If search by rego is needed later, add an HMAC "blind index" column. Not built now.
  - **Production:** the key moves to KMS, and storage-level encryption at rest is enabled on top.
  - Never log PII, tokens or OTPs.
- **N+1:**
  - List endpoints eager-load their relations, for example inspections with their client in a single query.
  - Add a test or a query count check for the dashboard list.
- **Media:**
  - The client requests an upload URL, then uploads directly to storage, then confirms.
  - Only the object key, type and size are stored in the DB.
  - Allowlist `image/jpeg`, `image/png`, `image/heic` and `video/mp4`.
  - 600MB Size caps.
  - Originals are never modified.

## Failure modes (answer each in the README)

1. **Ack not returned:** retry 3 times with backoff, then send to the DLQ (see Queue).
2. **Duplicate requests:** idempotency keys plus conditional status updates (see above).
3. **Uploads:** direct to S3 with presigned URLs; multipart for video in production.
4. **Leaked API keys:**
   - Prevention: secrets come only from env vars, `.env` is gitignored, and `.env.example` is committed.
   - Response: rotate the key, revoke the old one, check provider logs for misuse, and rewrite git history if a key was committed.
5. **High load:**
   - The API is stateless and scales horizontally.
   - Slow work goes through the queue.
   - Uploads bypass the API.
   - Public endpoints are rate limited.

## Coding rules

- Write minimal code. Prefer deleting code to adding it. SOLID, DRY, KISS, YAGNI.
- Ask when something is unclear; do not assume. Mark every assumption **[ASSUMPTION]** and add it to the README.
- Split the repo into `frontend/` (the views) and `backend/` (a Gradle subproject; run commands from the root).
- Structure the backend as MVC: all models in `model/`, all controllers in `controller/`, with `repository/`, `service/` and `security/` alongside. Keep infrastructure behind small interfaces in `infra/`.
- Validate all input at the boundary. Return consistent error shapes.
- Tests are required for:
  - token and OTP checks
  - submitting exactly once under concurrency
  - retry then DLQ
  - authorisation (customers and anonymous requests cannot create users)
- No secrets in the repo. Config comes from env.
- Use small, descriptive commits. The git history is part of what is reviewed.

## Build order

1. Scaffold, docker compose with Postgres, config, schema and migrations, seed admin.
2. Admin auth, then user management.
3. Clients CRUD with encrypted PII.
4. Create an inspection, generate the token, send the SMS through the mock queue.
5. Public session endpoint, "opened" tracking, OTP.
6. Form, media upload through the storage mock, submit.
7. Dashboard list (eager loaded), admin response, response SMS.
8. README: setup, assumptions, mocks, failure modes, what production would add.

## Open questions

- What fields does the inspection form contain? Default: mileage, condition notes, photos and a video.
- Media size caps.
- Is the admin response free text only, or something more structured?