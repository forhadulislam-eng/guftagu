# GUFTAGU Architecture Baseline

## Product architecture

GUFTAGU has independent web and Android clients using one Spring Boot backend and shared public contracts. The initial production architecture is a modular monolith: one deployable backend, PostgreSQL for durable truth, Redis for bounded ephemeral workloads, REST for request/response operations, STOMP over WebSocket for real-time events and call signaling, and WebRTC for voice/video media. This deliberately avoids premature microservices. Module boundaries are enforced first; service extraction needs measured operational or team-ownership evidence.

## Repository structure and Nx decision

**Decision: use a simple multi-application repository; do not adopt Nx initially.** Nx is most useful for multiple TypeScript applications and shared TypeScript packages needing coordinated task graphs and affected builds. Although the web and React Native/Expo clients will both use TypeScript, two applications do not yet justify an additional workspace abstraction, and Nx does not materially manage the Java/Spring Boot backend. Reassess when the TypeScript estate has demonstrated coordination or build-performance costs.

```text
guftagu/
├── docs/architecture.md
├── web/                    # Next.js + TypeScript client
├── android/                # React Native + Expo client, targeting Android first
├── backend/                # Java 21 Spring Boot modular monolith
├── contracts/              # OpenAPI and asynchronous-message schemas
├── infra/                  # Docker, deployment, operational configuration
├── scripts/                # Small repository automation when justified
├── .github/workflows/      # CI workflows
└── AGENTS.md
```

This is the recommended target layout, not a request to scaffold it. Each client and backend retains native tooling. `contracts/` holds explicit interface definitions, not shared runtime code or generated-code dumps.

## Web architecture

The web client will be Next.js + TypeScript. It uses REST for durable reads and commands and one authenticated STOMP/WebSocket connection for live updates. Keep UI state, API state, and socket state separate. Server-render public/static pages where it is useful; use authenticated client state for the interactive communication surface. Socket events speed up UI but are never proof of authorization or a replacement for reconciliation through REST after reconnect.

## Android architecture

Android is a separate React Native + Expo client, targeting Android first and using the same REST and STOMP contracts. Its shape should have presentation/UI, domain/use-case, and data layers, with managed authenticated REST and socket clients. It needs secure token storage, push support, network recovery, and a mobile-appropriate cache policy. The React Native/Expo choice and Android-first target are established; minimum OS version, offline behavior, and notification provider remain approval decisions.

## Spring Boot modular monolith

Organize the single Spring Boot application by business module, not just global controller/service/repository layers. Every module owns its handlers, application services, domain rules, persistence mappings, and internal events. Cross-module work uses narrow application interfaces; modules do not query one another's tables directly. Shared infrastructure contains security, database, cache, logging, and API error conventions, but no product rules. Transactions remain local to PostgreSQL. Publish real-time events only after durable commits; adopt an outbox only when delivery guarantees require it.

## Backend domain boundaries

- **Identity and access:** accounts, credentials, sessions, devices, roles, authorization decisions.
- **Profiles and contacts:** profiles, preferences, privacy, blocks, and contacts if included in scope.
- **Conversations:** direct conversations, groups, membership, roles, and policy.
- **Messaging:** messages, edits/deletes if approved, attachment references, receipts, validation.
- **Realtime:** connections, presence, typing, event publication, STOMP authorization; no durable message truth.
- **Calls:** call permissions, signaling routing, state transitions, and call history; never media transport.
- **Media:** upload permission, metadata, scan status, and retention.
- **Notifications:** devices, preferences, and push-delivery orchestration.

## Authentication and authorization

**Decision: phone number plus password is the primary authentication model, and phone number is used for user discovery.** This aligns sign-in and contact discovery around one identifier. It requires secure phone verification and recovery flows, mitigation for SIM-swap and number-reassignment risk, rate limits around discovery, and strict phone-number privacy controls.

Regardless of identifier choice, hash passwords with Argon2id or bcrypt when passwords are used, and never retain plaintext or reversible credentials. Issue short-lived signed access JWTs and long-lived rotating refresh tokens. Store refresh tokens as hashes, scoped to a device session with expiry and revocation metadata. Rotation detects reuse and revokes the affected family; logout revokes the current session and logout-all revokes all user sessions.

Authorize every REST resource operation and every STOMP CONNECT, subscription, and send destination. Use resource membership as the main rule, plus minimal roles such as user and group owner/admin. Browser refresh tokens should use `HttpOnly`, `Secure`, appropriately `SameSite` cookies; Android tokens use platform secure storage. Token issuance, refresh, reset, logout, and device actions need auditable events.

## PostgreSQL data model

PostgreSQL is the source of truth for durable product data. Major entities and relationships are:

- `users`, `password_credentials`, `user_sessions`, optional `devices`, profiles, blocks, and preferences;
- `conversations` (direct or group) and `conversation_members` with group roles;
- `messages`, belonging to one conversation and sender, ordered with a durable server-assigned sequence or time-sortable identifier;
- `media_objects` and `message_attachments`, which reference messages and managed objects;
- `message_receipts` or durable per-member conversation checkpoints;
- `calls` and `call_participants` for lifecycle and history; and
- notification devices and delivery records where durable audit is needed.

Direct conversations enforce a unique eligible pair; groups have many memberships. Index membership lookup, messages by conversation/order, receipt reads, session token hashes, and active devices. Exact schemas, constraints, retention, partitioning, and migrations are explicitly deferred.

## Redis responsibilities

Redis is limited to TTL-bound presence and typing state, connection routing, rate-limit counters, temporary verification/idempotency state, call-signaling coordination, and bounded caches. Redis is not canonical for accounts, permissions, conversations, messages, receipts, calls, or media metadata. Cache loss must reduce convenience rather than corrupt correctness; backend behavior must remain safe during Redis loss.

## REST API conventions

Publish versioned JSON APIs under `/api/v1`, with resource-oriented URLs, correct HTTP semantics, opaque IDs, UTC ISO-8601 timestamps, explicit request/response DTOs, cursor pagination, and idempotency keys for retryable creations. Validate at boundaries. Publish OpenAPI as the REST contract. Errors use one problem-details-style JSON envelope with HTTP status, stable machine code, safe message, field violations where relevant, and correlation ID. REST writes durable state; clients reconcile with REST after missed or ambiguous socket events.

## WebSocket/STOMP messaging architecture

Provide one authenticated STOMP endpoint over WebSocket. Authenticate during handshake/CONNECT, bind a user and device session, and authorize every subscription and send destination. Publish authorized user-specific and conversation events only to allowed recipients. STOMP carries message-created/updated hints, receipts, presence/typing, notification hints, and WebRTC signaling. It is not an alternate unvalidated durable-message write path: message creation starts as REST. Require heartbeats, destination allowlists, bounded frames, connection cleanup, per-user connection limits, and reconnect/reconciliation behavior.

## WebRTC voice/video calling

WebRTC carries all audio/video media directly between participants or via TURN. Spring Boot only authenticates, authorizes, routes signaling, records state, and stores history; it never carries media streams. A caller creates an authorized call; the server records intent and alerts the callee. After acceptance, authenticated STOMP messages relay SDP offers/answers and ICE candidates only between authorized call participants.

States are initiated, ringing, accepted/connecting, connected, ended, declined, missed, and failed. A terminal transition persists call history. Use STUN for candidate discovery and credentialed TURN for restricted networks, preferably with short-lived credentials. Signaling is scoped to call ID, user/device session, and participant permission. On socket reconnect, clients confirm call state and exchange needed new ICE candidates with a bounded fail timeout. The call/participant model deliberately permits future group calling, but no SFU, topology, or group-media implementation is selected now.

## Media and files

Use private object storage for bytes, not PostgreSQL or Redis. PostgreSQL stores metadata, ownership, content type, size, checksum, scan state, lifecycle references, and authorization context. The backend authorizes access and issues short-lived signed upload/download URLs where appropriate. Enforce allowlisted types, size limits, filename normalization, quotas, malware scanning before availability, and retention/deletion rules. Never trust client MIME type or expose raw storage keys.

## Notifications, presence, typing, and receipts

Notifications are delivery hints, never truth. Persist device tokens and preferences, respect muting and blocks, minimize sensitive payload content, remove invalid tokens, and require a client to authenticate and fetch current state. Platform/provider selection needs approval.

Presence and typing are ephemeral, Redis-backed STOMP signals with short TTLs, visible only to conversation members. Presence means recently active, not guaranteed availability; disconnect and expiry clear signals. Neither is a durable chat event.

Receipts are durable PostgreSQL state. Prefer per-member conversation delivered/read checkpoints when their semantics suffice, deriving message status efficiently; use individual receipts only if required. Updates need membership authorization, are monotonic, and emit after persistence.

## Groups

Groups are conversations with durable membership and role policy. Initial roles are owner, admin, and member, with explicit capabilities for invitations, removal, and metadata changes. Membership changes are auditable. Every group message, upload, receipt, signal, notification, and call invitation rechecks current membership. Group calling remains future scope only.

## Security and abuse prevention

Enforce TLS, security headers, secure password hashing, short access-token lifetime, rotating/revocable refresh sessions, resource authorization, strict validation, and parameterized database access. Restrict CORS to approved web origins. Cookie-based browser auth requires CSRF protection for unsafe requests; a final browser token transport choice requires approval due to CSRF/XSS tradeoffs.

WebSockets require authentication, origin checks, destination authorization, frame-size limits, and connection limits. Use a secret manager, rotate secrets, do not commit secrets, and redact passwords, tokens, SDP, ICE candidates, message bodies, and sensitive media metadata from logs. Abuse controls include blocks/reports, spam and invitation limits, rate limits, moderation hooks, and operational review. Add dependency/security scans once dependencies exist.

## Rate limiting and errors

Rate-limit by user/session, IP where appropriate, and resource/destination. Protect registration, login, reset, refresh, uploads, messages, invitations, scrape-prone reads, socket connects, STOMP sends, and call signaling. Redis counters are suitable. REST returns `429` with retry guidance; sockets throttle/reject frames and close abusive connections. Make thresholds configurable and observable.

Do not expose stack traces, credentials, authorization details, or topology. Expected domain failures are explicit; unexpected failures are logged safely and mapped to generic errors. STOMP application/protocol errors follow the same stable-code and correlation-ID model.

## Logging and observability

Use structured logs with timestamp, level, module, correlation/request ID, and minimal safe context. Provide health/readiness checks and metrics for API latency/errors, PostgreSQL, Redis, sockets, calls, uploads, notifications, and rate limits. Add tracing when deployment complexity justifies it. Alert on user-impacting symptoms, not every exception.

## Testing strategy

- Unit tests: domain rules, authorization, validation, web/mobile presentation logic.
- Spring integration tests: module wiring, transactions, security, Redis fallback, durable behavior against PostgreSQL-compatible test infrastructure.
- API/contract tests: REST schemas, auth, errors, compatibility.
- Database tests: constraints, indexes/queries, and migrations once migrations exist.
- WebSocket tests: handshake/destination authorization, routing, presence, signaling isolation, recovery assumptions.
- Web and Android tests: data layers, secure-session behavior, realtime recovery, notifications where testable.
- End-to-end tests: register/login, direct messaging, groups, attachment flow, receipts, and one-to-one signaling.

Real browser/device WebRTC and TURN interoperability need focused integration testing, not only mocks.

## Docker, local development, CI/CD, and deployment

Later, Docker Compose should run local PostgreSQL, Redis, and test object-storage/TURN dependencies. Run clients and Spring Boot with native development commands unless containers provide a concrete benefit. Use sanitized environment-file examples, reproducible commands, health checks, and documented port ownership.

CI runs format/lint checks, unit and integration/API tests where services are available, builds, contract validation, and dependency/security scans. Path-based workflows are sufficient without Nx. CD produces immutable artifacts, promotes through environments, injects secrets per environment, runs smoke tests, and supports rollback. Protect branches and require review before release.

Deploy the backend as a stateless Spring Boot service behind TLS-aware ingress/load balancing, with managed PostgreSQL/Redis where practical and private object storage. Configure WebSocket ingress timeouts. Deploy web separately as its suitable static/server runtime. TURN is separate infrastructure. Begin with one region/topology that meets real demand; add multi-region, microservices, or an SFU only with evidence.

## Backup and recovery

Use automated encrypted PostgreSQL backups with point-in-time recovery and regularly tested restores. Protect media through object versioning/lifecycle policies and validate database/object consistency in recovery planning. Redis is rebuildable and never a backup substitute. Define retention, RPO, RTO, restore-test cadence, ownership, and incident communication before production. Legal retention/deletion requirements need approval.

## Development phases and dependency order

1. Approve scope and the human decisions below.
2. Establish repository conventions, contracts workflow, local configuration, CI skeleton, and security baseline.
3. Implement identity/access, PostgreSQL persistence, profiles, REST errors, authorization tests.
4. Implement direct conversations and messages, then attachments, receipts, groups, notifications.
5. Add STOMP, message events, presence/typing, recovery/reconciliation.
6. Add one-to-one call signaling/history, then WebRTC clients, STUN/TURN, interoperability tests.
7. Complete abuse controls, observability, backup/recovery, deployment, and end-to-end hardening.
8. Reassess Nx, service extraction, group calls, SFU, and scaling from production evidence.

## Decisions requiring explicit human approval

- Android minimum OS, offline behavior, and push provider.
- Phone verification and password-recovery requirements; external identity providers, if any; and browser refresh-token transport.
- Privacy, retention, deletion, moderation, reporting, compliance, and notification-content policy.
- Cloud/hosting, region, managed services, object storage, secret manager, observability provider, RPO/RTO.
- TURN provider/operation, call-quality targets, and whether recording is ever in scope.
- Initial scope for contacts, edits/deletes, attachments, receipt semantics, and notifications.
- CI provider, branch protection, release approval, and deployment ownership.
