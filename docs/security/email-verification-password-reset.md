# Email Verification And Password Reset Security

## Scope

Issue #224 adds provider-independent backend behavior for signup email verification and direct password reset, plus an approved Brevo SMTP adapter behind the sender port. It does not add `users.email_verified_at`.

Public endpoints:

- `POST /api/v1/auth/email-verifications/signup/request`
- `POST /api/v1/auth/email-verifications/signup/confirm`
- `POST /api/v1/auth/password-resets/request`
- `POST /api/v1/auth/password-resets/confirm`
- `POST /api/v1/auth/password-resets/complete`

The complete endpoint returns no Access or Refresh Token. The user signs in again with the new password.

## Runtime Configuration

- `FAITHLOG_AUTH_EMAIL_VERIFICATION_REQUIRED`: defaults to `false` for old app compatibility.
- `AUTH_VERIFICATION_HMAC_SECRET`: independent standard Base64 value decoding to at least 32 bytes. A malformed or shorter configured value fails startup. Blank is allowed only while the rollout flag is false; the verification operations still fail closed.
- `FAITHLOG_EMAIL_DISPATCH_CLOUD_TASKS_ENABLED`: defaults to `false`; enables durable task enqueue only when all queue settings below are present.
- `FAITHLOG_EMAIL_DISPATCH_WORKER_ENABLED`: defaults to `false`; exposes the private worker endpoint behind Google OIDC validation.
- `AUTH_EMAIL_DISPATCH_ENCRYPTION_KEY`: independent standard Base64 value decoding to exactly 32 bytes for AES-256-GCM payload encryption and dispatch-token HMAC fingerprints.
- `FAITHLOG_EMAIL_DISPATCH_PROJECT_ID`, `FAITHLOG_EMAIL_DISPATCH_LOCATION`, `FAITHLOG_EMAIL_DISPATCH_QUEUE_ID`: approved Cloud Tasks queue identity.
- `FAITHLOG_EMAIL_DISPATCH_WORKER_URL`: absolute HTTPS worker endpoint.
- `FAITHLOG_EMAIL_DISPATCH_OIDC_SERVICE_ACCOUNT_EMAIL`, `FAITHLOG_EMAIL_DISPATCH_OIDC_AUDIENCE`: exact Google OIDC service-account and audience binding used by both task creation and worker validation.
- `FAITHLOG_EMAIL_PROVIDER_ENABLED`: defaults to `false`; when true, startup requires the exact approved Brevo SMTP contract below.
- `SPRING_MAIL_HOST=smtp-relay.brevo.com`, `SPRING_MAIL_PORT=587`, `SPRING_MAIL_USERNAME=b3a5e2001@smtp-brevo.com`, and runtime-only `SPRING_MAIL_PASSWORD`.
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_AUTH=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_ENABLE=true`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_STARTTLS_REQUIRED=true`.
- `SPRING_MAIL_PROPERTIES_MAIL_SMTP_CONNECTIONTIMEOUT`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_TIMEOUT`, `SPRING_MAIL_PROPERTIES_MAIL_SMTP_WRITETIMEOUT`: positive millisecond values; the prepared deployment uses `5000/10000/10000`.
- `FAITHLOG_MAIL_FROM_NAME=FaithLog`, `FAITHLOG_MAIL_FROM_EMAIL=josephuk77@gmail.com`.

When the required flag is false, a legacy signup may omit `emailVerificationToken`. If a token is supplied, it is never ignored: the backend validates the normalized-email binding and consumes it once. Set the flag to true only after updated iOS and Android builds are mandatory.

With the provider flag false, the app starts with the unavailable sender and no SMTP requirements; signup email requests cancel their challenge and return safe `503`, while password-reset requests keep the generic response but cannot be confirmed because no code is delivered. With the provider flag true, a missing/wrong host, port, login, password, AUTH/STARTTLS setting, timeout, sender, or logo checksum fails startup. Do not enable mandatory signup verification until HMAC, Cloud Tasks, worker OIDC, encryption, and the Brevo sender all pass deployment smoke tests.

## Redis State

Redis keys contain HMAC fingerprints, not raw email, code, or grant token:

```text
auth:email-verification:challenge:{purpose}:{emailHmac}
auth:email-verification:cooldown:{emailHmac}
auth:email-verification:rate:{emailHmac}
auth:email-verification:grant:{purpose}:{tokenHmac}
auth:email-dispatch:payload:{dispatchTokenHmac}
auth:email-dispatch:lease:{dispatchTokenHmac}
```

TTL and limits:

| State | Contract |
|---|---|
| Code | 5 minutes |
| Resend cooldown | 60 seconds |
| Email request count | 5 per fixed Redis TTL window of 1 hour, starting with the first request |
| Wrong confirmation attempts | 5 |
| Signup/reset grant | 10 minutes, one consumption |

Lua scripts perform the following indivisible transitions:

1. Issue checks cooldown, increments the email rate counter, writes the challenge hash and TTL, and creates cooldown.
2. Confirm compares fixed-length HMAC hex without an early mismatch return, increments/blocks attempts, creates a purpose-specific grant with `SET NX`, and deletes the challenge.
3. Signup consumption compares the email-bound subject and deletes the grant in the same script.
4. Password-reset completion resolves without deletion, locks the expected user row, re-resolves, rejects the current password without consuming the grant, then compares the expected user and deletes the grant atomically before changing the password.

Email dispatch uses a separate durable boundary:

1. Both existing and missing password-reset accounts create the same encrypted Redis payload and Cloud Task. The task body contains only a 32-byte URL-safe opaque dispatch token.
2. AES-256-GCM protects recipient/code/purpose/TTL at rest with random 96-bit IV and authenticated context. Redis keys contain only HMAC fingerprints of the dispatch token.
3. The worker endpoint accepts only Google OIDC tokens with the configured issuer, exact audience, exact service-account email, and `email_verified=true`.
4. A Redis Lua transition distinguishes `ACQUIRED`, `IN_PROGRESS`, and `MISSING`. `IN_PROGRESS` returns a retryable worker failure, while only a missing/already-acknowledged payload is idempotently accepted. This prevents an overlapping delivery from acknowledging the task while the lease owner later fails.
5. The sender port receives a stable SHA-256 delivery ID derived from the opaque dispatch token, never the raw token. The Brevo SMTP adapter writes it only to the CRLF-safe `X-FaithLog-Delivery-Id` trace header. SMTP does not provide provider-side idempotency, so delivery remains at-least-once when send succeeds but Redis acknowledgement fails and the same code can rarely be delivered more than once.
6. A missing-account task follows the same queue/worker path but skips the provider call after decrypting `deliveryRequired=false`.

Code and token generation use `SecureRandom`: zero-padded six-digit codes and 32-byte URL-safe opaque tokens.

## Enumeration And Sensitive Data

- Password-reset request returns `가입된 이메일이라면 인증번호가 발송됩니다.` for both present and absent accounts.
- An absent account receives equivalent challenge/cooldown/rate state and the same durable enqueue path; only the private worker decides not to invoke the provider.
- Provider latency is outside the public request. Queue failure is suppressed for both present and absent password-reset requests, preserving the generic response and challenge state without sleep padding or in-process fire-and-forget work.
- Cloud Tasks request bodies, Redis keys/values, application logs, exception text, and reports must not contain plaintext recipient email or verification code. The encrypted Redis payload expires with the five-minute code TTL.
- Raw email, code, grant, password, JWT, Authorization header, and provider response body must not be logged or included in exception messages.
- IP rate limiting is not implemented until Cloud Run trusted-proxy behavior is approved. `X-Forwarded-For` is not trusted by this feature.

## Password And Session Consistency

Password completion resolves the reset grant, locks the user row, rechecks the grant subject, and rejects the current password before the atomic consume. A same-password `400` therefore preserves the same grant for another attempt within its original TTL. A new password consumes the grant once, stores a BCrypt hash, increments `tokenVersion`, and removes all refresh sessions inside the transaction boundary.

Refresh JWTs include `tokenVersion`. Refresh obtains the same user row lock and compares the claim with the current DB value before Redis rotation. This gives the following race behavior:

- A refresh/login that acquired the row first completes before reset; reset then revokes its resulting version/session.
- A refresh waiting behind reset sees the incremented version and returns `401 AUTH_UNAUTHORIZED`.
- A login waiting behind reset checks the new hash, so the old password fails.
- Existing Access Tokens fail the existing DB `tokenVersion` check.
- FCM tokens are not changed by password reset.

Refresh Tokens issued before Issue #224 do not contain `tokenVersion` and are rejected after this deployment. Existing Access Tokens continue only until their normal expiry when their persisted version still matches; the client must handle the subsequent refresh `401` by clearing auth state and asking the user to sign in again.

The DB and Redis are not one atomic resource. The fail-closed policy is:

- Refresh-session deletion failure occurs before DB commit and causes password/hash/version rollback.
- If session deletion succeeds but DB commit later fails, sessions remain revoked while the old password remains; this is safe and the user starts recovery again.
- Once a new-password attempt atomically consumes the grant, it is not restored after later infrastructure failure. A same-password validation error occurs before consumption and can retry within TTL.
- Signup flushes the email unique constraint before consuming its grant. A duplicate-email race therefore returns `AUTH_EMAIL_ALREADY_EXISTS` without consuming the losing request's grant.

## Email Canonicalization And V13

- User-entered email is stored after trim with its original letter case preserved.
- Authentication, signup uniqueness, verification, and password-reset lookup compare the canonical `trim + lowercase(Locale.ROOT)` value.
- V13 performs a read-only `lower(email)` duplicate preflight and raises SQLSTATE `23505` when duplicates exist. It never merges, deletes, or reassigns users.
- When the preflight is clean, `uk_users_email_lower` enforces PostgreSQL case-insensitive logical-mailbox uniqueness. Audit production duplicates before deployment; a failed V13 requires an explicit data-resolution decision before retry.

## Branded Brevo Email

The JavaMail adapter sends a responsive `multipart/alternative` message with a plaintext fallback and Korean copy specific to signup or password reset. It attaches `classpath:/mail/faithlog-logo.png` as `cid:faithlog-logo` with `alt="FaithLog"`; the approved 112x112 PNG is 14,996 bytes with SHA-256 `6059e8f38377cfe485752cdf2fa37a014e01586246de5fd4ff26a40446d1b748`. The message contains only the six-digit code and expiry. It has no remote image, tracking pixel, deep link, JWT, reset grant, recipient address in the body, or user content.

The adapter validates the sender identity, recipient syntax, code, positive TTL, lowercase 64-hex delivery ID, and logo checksum before sending. SMTP and message-construction failures become one generic exception without the recipient, code, SMTP response body, or secret. The header is a correlation value, not an idempotency guarantee.

## Pending Decisions

- Delivery monitoring/alert thresholds and an operational duplicate-delivery observation policy for the SMTP at-least-once boundary.
- Whether to add `users.email_verified_at` and how to backfill existing users.
- Cloud Run trusted proxy/header policy required for the approved IP limit of 20 requests/hour.
- Exact deployment revision that changes `FAITHLOG_AUTH_EMAIL_VERIFICATION_REQUIRED` from false to true.
