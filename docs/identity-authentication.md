# Identity and Authentication Architecture

## Purpose and boundaries

Identity and access is a module in the GUFTAGU Spring Boot modular monolith. It
owns user identity, verified phone numbers, password credentials, sessions,
refresh-token lifecycle, and authentication/authorization decisions. It exposes
narrow application interfaces to profiles, conversations, messaging, realtime,
calls, media, and notifications; those modules do not read identity tables
directly.

PostgreSQL is the durable source of truth. Redis, if introduced, is only a
short-lived optimization for rate limits or connection/session-revocation hints;
it is never authoritative for users, credentials, sessions, or refresh tokens.

## Identity model and user lifecycle

Each user has one active, verified primary phone number and one password
credential. Phone number plus password is the approved primary authentication
model. The verified phone number is also the identifier used for user discovery,
subject to future privacy settings and consent policy.

The initial lifecycle is:

1. A registration request starts phone verification and submits a password.
2. The phone verification succeeds before an active user is created, or before a
   pending user is activated. Unverified identities cannot log in, be discovered,
   join conversations, or receive a session.
3. Activation creates the user, verified phone record, password credential, and
   first device session atomically where practical.
4. An active user may create, refresh, revoke, or end sessions.
5. Suspension, deletion, phone-number change, recovery, and account restoration
   are future lifecycle operations with explicit policies; they are not inferred
   from this design.

Keeping an explicit pending-verification state prevents an unverified number
from being discoverable. Expired pending registrations and verification
challenges are retained or deleted according to an approved abuse/audit
retention policy.

## Phone numbers, normalization, and discovery

Normalize every supplied number with a maintained phone-number library, using a
required/default region only while parsing. Persist the canonical international
E.164 representation, never the user's display formatting. Reject numbers that
cannot be parsed as valid mobile-capable numbers under the selected policy.

An active verified number has a database-enforced unique normalized value. Do
not compare raw input strings or rely on a client-normalized value. Phone data
is sensitive PII: restrict it to identity/discovery operations, encrypt disks and
backups, minimize it in telemetry, and never log it unmasked. A keyed normalized
lookup hash may be stored for privacy-preserving contact discovery; it is an
additional indexed representation, not a replacement for the canonical number.

Discovery must return only users that are eligible and opted in under the future
privacy policy. Bulk contact upload, matching protocol, exposure of a matched
phone number, and discovery opt-out are product/privacy decisions that precede
implementation. Rate limits and response shaping must prevent enumeration.

## Password credentials

Store a password only as an Argon2id hash, with per-password salt and current
memory/time/parallelism parameters configured outside source control. Use a
well-reviewed Spring Security password encoder when implementation begins.
Never use reversible encryption, plaintext, unsalted fast hashes, or password
equivalents in logs, events, metrics, or error messages.

Apply a password policy at the API boundary of 10–128 characters; passwords are
fully processed and never silently truncated. Confirm-password matching belongs
to clients; the server validates the submitted password independently. A password
reset requires verified-phone recovery and revokes every active session and its
refresh-token family before the user can create a new session. Breached-password
checking is optional and must not transmit raw passwords.

## Device and session model

A session represents one authenticated client installation/browser context, not
one user. A user can have many sessions. A session records its user, opaque
session ID, created/last-authenticated/last-used/expiry/revoked timestamps,
revocation reason, client type (`WEB` or `ANDROID`), optional client-provided
installation identifier, and minimally retained user-agent/IP audit metadata.

Do not use browser fingerprinting as an authorization factor. Android may use a
stable app-installation ID held in platform storage; web may use a generated
browser-context ID. Both are labels/audit aids, not proof of possession. Session
listing must expose only safe device descriptions and must not expose token
material.

## Access tokens

Issue a short-lived signed JWT access token with a 10-minute lifetime, configured
by environment. The token contains only required claims:
issuer, audience, subject (user ID), session ID, issued/expiry times, token type,
JWT ID, and optional minimal authorization version. It must not contain a phone
number, password data, permissions copied from mutable resources, or other
sensitive profile data.

The API validates signature, issuer, audience, expiry, token type, and session
status. Session status is PostgreSQL-backed so logout/revocation can take effect
immediately; a bounded cache may later optimize reads but may not grant access
after authoritative revocation. Signing keys come from a secret manager and are
key-identified for rotation. Use asymmetric signing when independent verifiers
become necessary; a single monolith may begin with a strongly managed symmetric
key if key custody remains simple and auditable.

## Refresh tokens, rotation, and reuse detection

Refresh tokens are opaque, high-entropy random secrets—not JWTs. Return the raw
value only once to the client. Persist a keyed hash of the secret, never the raw
long-lived value. A practical token format is `refreshTokenId.secret`, where the
public opaque ID locates a record and the secret is verified with a keyed HMAC or
secure hash. Token material is transmitted only over TLS.

Each refresh token belongs to one session and one rotation family. On a refresh,
within one PostgreSQL transaction:

1. Locate and lock the token record by opaque ID.
2. Verify the secret hash, expiry, family/session activity, and unused status.
3. Mark the presented token consumed and create its replacement in the same
   family with a new random secret.
4. Update session last-used data and return a new access token and refresh token.

If a consumed token is presented again, treat it as suspected theft or replay:
revoke the affected session and its refresh-token family, terminate associated
real-time connections, and emit a safe security audit event. Concurrent refresh
requests are serialized by the database lock; clients should retry only through
the documented token-refresh behavior. A refresh-token family has a 30-day
absolute lifetime from initial issuance; rotation does not extend that deadline.
Each replacement expires no later than its family's absolute expiry.

## Registration, login, refresh, and logout flows

Illustrative REST endpoints live under `/api/v1` and are contracts to be defined
later, not implementation commitments.

- **Registration:** `POST /auth/registrations` validates a normalized phone,
  password, consent, and client metadata; it creates/reuses a non-discoverable
  pending verification flow. `POST /auth/phone-verifications/confirm` verifies
  the challenge, activates identity, and creates the first session/tokens.
- **Login:** `POST /auth/sessions` accepts normalized phone and password,
  applies anti-enumeration behavior, verifies the credential, then creates a
  session, refresh family/token, and access token.
- **Refresh:** `POST /auth/tokens/refresh` rotates the current refresh token as
  described above and returns a new short-lived access token.
- **Logout current device:** `POST /auth/sessions/current/logout` requires a
  valid session context, revokes that session and its refresh family, clears the
  browser refresh cookie where applicable, and ends associated sockets.
- **Logout all devices:** `POST /auth/sessions/logout-all` requires a current
  authenticated session and revokes every active session/family for the user,
  including the caller. The client then clears local credentials.

Registration/login responses must not reveal whether a phone number exists,
whether it is pending, or whether a password was incorrect. Password reset uses
verified-phone recovery, updates the password credential, and revokes every
existing session before a new login is allowed. Password change, phone-number
change, and account deletion require separate approved flows before endpoints
are designed.

## Client token storage

### Web

Keep access tokens in memory only and send them in the `Authorization: Bearer`
header for REST calls. Store the refresh token only in an `HttpOnly`, `Secure`,
path-scoped cookie with a deliberately chosen `SameSite` setting; never put
long-lived tokens in local/session storage. Browser refresh/logout endpoints
that rely on cookies require CSRF protection, using an approved combination of
same-origin deployment or strict origin checks plus a CSRF token/header. CORS is
an allowlist of trusted web origins with credentials only where necessary.

### Android

React Native/Expo stores refresh tokens only in platform-backed secure storage.
Keep access tokens in memory where practical and rehydrate them via refresh on
app launch. Do not put either token in AsyncStorage, logs, deep-link URLs, or
analytics. Android sends access tokens in an Authorization header and sends its
refresh token in the documented request body/header over TLS.

## Authentication filter and authorization model

The identity module supplies a single Spring Security authentication filter that
extracts a bearer access token, validates it, confirms active session status,
and installs an authenticated principal containing user ID and session ID. It
has no conversation, profile, or call rules. Public endpoints are explicitly
allowlisted; all other API routes deny anonymous access by default once identity
is introduced.

Authorization is resource-based. The identity module answers identity/session
questions; owning modules decide access to their resources through narrow policy
interfaces—for example, a conversation module confirms membership before a
message action. Initial global roles should stay minimal: ordinary user plus
operational administrator, with group owner/admin/member as conversation-local
roles. Do not put mutable group membership or broad permission lists in access
tokens.

For REST, controllers delegate authorization before executing a command or
returning protected data. Authentication alone never authorizes a resource. Use
consistent `401` for missing/invalid authentication and `403` for an
authenticated principal lacking access, while avoiding data-leaking differences
for discovery and login.

## WebSocket/STOMP authentication and authorization

WebSocket/STOMP is authenticated with the current access token at STOMP CONNECT,
not a query-string token. This supports browser clients that cannot set arbitrary
native WebSocket handshake headers. A CONNECT interceptor validates the token and
active session, binds user/session identity to the connection, and rejects
anonymous/expired/revoked tokens.

An authorization interceptor checks every SUBSCRIBE and SEND destination. A user
may receive only their own queues and resources they are currently authorized to
access. Conversation membership is checked by the conversations module; calls
checks call-participant authorization; signaling remains scoped to the call.
On session revocation, disconnect active sockets for that session or reject the
next frame immediately. Clients acquire a fresh access token via REST and
reconnect after token expiry; never use a refresh token in STOMP.

## Account and session security

Require TLS in every non-local environment. Apply secure headers, bounded JSON
and STOMP payloads, origin checks, user/session socket caps, and secret-manager
configuration for token-signing keys, phone-verification providers, and token
hash peppers. Rotate keys and preserve a limited verification window for
previous keys. Redact phone numbers, passwords, access/refresh tokens, OTPs,
authorization headers, session secrets, and WebRTC SDP/ICE data from logs.

Phone verification, password recovery, password change, phone-number changes,
and login are high-risk flows. They need attempt limits, expiry, single-use
challenges, security audit events, user notification policy, and re-authentication
where appropriate. Advanced SIM-swap and number-reassignment handling is
deferred; phone possession alone must not silently replace a password credential
or active-session history.

## Rate limiting and abuse prevention

Use Redis only for short-TTL counters/windows when available, with conservative
in-process or PostgreSQL-backed fallback behavior where necessary. Limits are
keyed by IP, normalized-phone lookup hash, account/session, device label, and
endpoint as appropriate. Apply limits to registration starts, OTP sends and
verification attempts, login, refresh, recovery, phone changes, discovery, and
WebSocket CONNECT/STOMP frames.

Use generic responses, exponential backoff, per-number daily quotas, and alerting
for suspicious replay, credential stuffing, OTP abuse, and enumeration patterns.
Do not use CAPTCHA, IP reputation services, or device fingerprinting until a
concrete product/abuse need and privacy review justify them.

## PostgreSQL model

No schema or migrations are created by this design. The required durable entities
and constraints are:

- **users:** immutable primary ID, lifecycle status, created/updated timestamps,
  and optional authorization version. Index lifecycle/admin queries.
- **phone_numbers:** user foreign key, normalized E.164 value, verification
  status/timestamps, primary flag, optional keyed lookup hash. Enforce unique
  active verified normalized value and one active primary number per user.
- **password_credentials:** one-to-one user foreign key, Argon2id hash and hash
  metadata, changed timestamp, failed-login metadata if retained. Enforce one
  active credential per user.
- **sessions:** user foreign key, client type, device label/installation ID,
  created/last-used/expiry/revoked timestamps, revocation reason, safe audit
  metadata, and token-family identifier. Index active sessions by user and
  expiry/revocation cleanup queries.
- **refresh_tokens:** opaque token ID, session and family foreign keys, keyed
  secret hash, issued/expiry/consumed/revoked timestamps, and predecessor link.
  Enforce unique token ID, index session/family and active-expiry lookups, and
  lock by token ID during rotation.
- **phone_verification_challenges:** normalized phone reference or pending
  registration, hashed challenge value, purpose, expiry, attempt count, consumed
  timestamp, and provider metadata safe for audit. Index active lookup by
  phone/purpose and expiry cleanup.
- **security_audit_events:** user/session references when known, event type,
  timestamp, correlation ID, and carefully minimized context. Index by user and
  time for account security history.

Foreign keys protect ownership relationships. Deletion and retention behavior is
defined later with legal/privacy policy. Database transactions govern session and
refresh-token state transitions; Redis loss cannot change their truth.

## Error handling and validation

Use the architecture-wide problem-details-style error envelope with status,
stable code, safe message, field violations where safe, and correlation ID.
Examples include `AUTH_INVALID_CREDENTIALS`, `AUTH_SESSION_REVOKED`,
`AUTH_TOKEN_EXPIRED`, `AUTH_REFRESH_REUSED`, `PHONE_INVALID`,
`PHONE_VERIFICATION_EXPIRED`, and `RATE_LIMITED`. Login, registration, recovery,
and discovery use generic outward responses to prevent account enumeration.

Validate content type, request size, normalized phone format, password policy,
supported client type, opaque token syntax, token expiry, and endpoint-specific
idempotency/retry behavior. Reject extra-sensitive values in URLs. Validation
errors do not echo passwords, OTPs, raw tokens, or full phone numbers.

## Compatibility with later modules

Profiles are created only for active users and may expose a privacy-filtered
identity view. Conversations and messages reference immutable user IDs, not
phone numbers, and rely on identity for active-session checks. Notifications bind
device registrations to sessions/users and remove them on revocation as policy
requires. Realtime uses authenticated user/session principals for presence and
destination authorization. Calls use the same principals for invitation and
signaling authorization; WebRTC media remains outside Spring Boot and identity
does not carry media credentials.

## Testing strategy

- **Unit:** phone normalization, password encoder configuration, token creation
  and validation, refresh state transitions, policy decisions, and error mapping.
- **Integration:** PostgreSQL constraints/index behavior, registration and
  verification transaction boundaries, concurrent refresh locking, rotation,
  reuse detection, expiry, logout, logout-all, and session revocation.
- **Security/API:** invalid/missing/expired/wrong-audience access tokens;
  CSRF/CORS behavior; generic login/discovery responses; authorization for owned,
  unowned, blocked, and revoked resources; no credential values in logs/errors.
- **WebSocket:** CONNECT authentication, subscription/send destination checks,
  revoked-session disconnect/rejection, expiry/reconnect, and isolation between
  users/conversations/calls.
- **Client lifecycle:** browser refresh-cookie and CSRF behavior; Android secure
  storage and cold-start refresh; multi-device login, current-device logout,
  logout-all, password-change/recovery invalidation policy, and offline/reconnect
  behavior.

## Decisions still requiring approval

- External OTP/SMS provider selection is deferred; OTP format, expiry, attempt
  limits, and whether verification is required before every first login remain
  to be finalized.
- Breached-password policy and password-change behavior beyond the required
  password-reset session revocation.
- Browser `SameSite`/CSRF deployment model and production web origins.
- Phone discovery consent, opt-out/default visibility, contact-matching protocol,
  retention, and anti-enumeration thresholds.
- Account suspension/deletion, phone-number-change, audit-event retention, and
  user security notifications. Advanced SIM-swap and number-reassignment
  handling is deferred.
- Token signing key type/custody/rotation procedure and operational administrator
  privileges.
