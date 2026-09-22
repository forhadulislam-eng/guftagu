# GUFTAGU

GUFTAGU is a production-oriented communication platform.

Current direction:

- Web: Next.js + TypeScript
- Mobile: React Native + Expo, targeting Android first
- Backend: Java 21 + Spring Boot modular monolith
- Durable data: PostgreSQL
- Ephemeral coordination/cache: Redis
- APIs: REST for request/response and STOMP/WebSocket for real-time events and WebRTC signaling
- Calls: WebRTC transports voice/video media; Spring Boot never transports media streams
- Authentication: phone number plus password, with phone-number-based user discovery

The repository intentionally uses a simple multi-application structure. Nx, microservices, application scaffolding, dependencies, infrastructure services, and implementation code are not present yet.

See [the architecture baseline](docs/architecture.md) for the approved foundation.
