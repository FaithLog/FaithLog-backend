# Email Verification And Password Reset Security

## Scope

Issue #224 adds provider-independent backend behavior for signup email verification and direct password reset. It does not select an SMTP/API provider and does not add `users.email_verified_at`.

Public endpoints:

- `POST /api/v1/auth/email-verifications/signup/request`
- `POST /api/v1/auth/email-verifications/signup/confirm`
- `POST /api/v1/auth/password-resets/request`
- `POST /api/v1/auth/password-resets/confirm`
- `POST /api/v1/auth/password-resets/complete`

The complete endpoint returns no Access or Refresh Token. The user signs in again with the new password.

## Runtime Configuration

- `FAITHLOG_AUTH_EMAIL_VERIFICATION_REQUIRED`: defaults to `false` for old app compatibility.
- `AUTH_VERIFICATION_HMAC_SECRET`: Secret Manager value used only for verification-state HMAC-SHA-256 fingerprints. Use an independently generated high-entropy secret; do not reuse the JWT secret.

When the required flag is false, a legacy signup may omit `emailVerificationToken`. If a token is supplied, it is never ignored: the backend validates the normalized-email binding and consumes it once. Set the flag to true only after updated iOS and Android builds are mandatory.

The production email adapter is intentionally unavailable until a provider is approved. Enabling the signup requirement before configuring both the HMAC secret and a production sender will fail closed.

## Redis State

Redis keys contain HMAC fingerprints, not raw email, code, or grant token:

```text
auth:email-verification:challenge:{purpose}:{emailHmac}
auth:email-verification:cooldown:{emailHmac}
auth:email-verification:rate:{emailHmac}
auth:email-verification:grant:{purpose}:{tokenHmac}
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
4. Password-reset consumption reads the user-bound subject and deletes the grant in the same script.

Code and token generation use `SecureRandom`: zero-padded six-digit codes and 32-byte URL-safe opaque tokens.

## Enumeration And Sensitive Data

- Password-reset request returns `가입된 이메일이라면 인증번호가 발송됩니다.` for both present and absent accounts.
- An absent account still receives equivalent challenge/cooldown/rate state but no email is sent.
- A password-reset provider failure is suppressed from the public request response and keeps the challenge state, preventing a later wrong-code request from distinguishing it from an absent account.
- Raw email, code, grant, password, JWT, Authorization header, and provider response body must not be logged or included in exception messages.
- IP rate limiting is not implemented until Cloud Run trusted-proxy behavior is approved. `X-Forwarded-For` is not trusted by this feature.

## Password And Session Consistency

Password completion consumes the one-time reset grant, locks the user row, rejects the current password, stores a BCrypt hash, increments `tokenVersion`, and removes all refresh sessions inside the transaction boundary.

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
- The reset grant is not restored after a complete attempt. Infrastructure failure requires a new verification request.
- Signup flushes the email unique constraint before consuming its grant. A duplicate-email race therefore returns `AUTH_EMAIL_ALREADY_EXISTS` without consuming the losing request's grant.

## Pending Decisions

- Production email provider, adapter, dependency, credentials, sender identity, and delivery monitoring.
- Whether to add `users.email_verified_at` and how to backfill existing users.
- Cloud Run trusted proxy/header policy required for the approved IP limit of 20 requests/hour.
- Exact deployment revision that changes `FAITHLOG_AUTH_EMAIL_VERIFICATION_REQUIRED` from false to true.
