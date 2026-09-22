# GUFTAGU

GUFTAGU is a real-time communication platform built for private, reliable, and seamless conversations across web and mobile.

## Product

GUFTAGU is being developed as a complete communication platform centered on:

- Real-time messaging and persistent conversation history
- Phone-number-based user discovery, profiles, and presence
- Delivery and read receipts; replies, editing, deletion, and reactions
- Group conversations, media/file sharing, and voice messages
- Notifications, privacy controls, blocking, and reporting
- WebRTC voice/video calling, call history, and missed calls
- Multi-device sessions with reconnection and offline handling

## Technology

The planned technology stack includes:

- Next.js, React, and TypeScript for web
- React Native + Expo for mobile, targeting Android first
- Java 21 and Spring Boot for the backend
- PostgreSQL and Redis for data and ephemeral coordination
- WebSocket/STOMP and WebRTC for real-time communication and calling
- Flyway, Maven, Docker, and GitHub Actions for delivery and operations

## Architecture

GUFTAGU follows a modular-monolith approach, keeping clear domain boundaries without premature microservices. PostgreSQL is the durable source of truth, while Redis is reserved for appropriate ephemeral, cache, and coordination workloads. REST supports request/response operations; WebSocket/STOMP supports real-time events and WebRTC signaling. WebRTC transports voice and video media directly—Spring Boot never carries media streams.

See the [architecture baseline](docs/architecture.md) for the detailed design.

## Project Status

GUFTAGU is under active development. The repository foundation and architecture baseline have been established.
