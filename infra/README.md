# GUFTAGU Infrastructure & Local Development

This directory contains container configurations and operational tooling for the GUFTAGU platform.

---

## 1. Prerequisites

- **Docker Desktop**: Installed and running with the Linux container engine.
- **Java 21**: JDK 21 installed locally.
- **Maven**: Maven 3.9+ (or use the repository wrapper `backend/mvnw.cmd` / `backend/mvnw`).

---

## 2. Local PostgreSQL Development Service

For local development, backing services run in Docker, while the Spring Boot backend runs natively on your host machine.

### Start PostgreSQL
To launch the background PostgreSQL 16 container:
```powershell
docker compose -f infra/docker-compose.yml up -d
```

### Check Service Health
Verify that the container is running and healthy:
```powershell
docker compose -f infra/docker-compose.yml ps
```
The status should indicate `Up` and `(healthy)`.

### Stop PostgreSQL
To stop the database service without deleting data:
```powershell
docker compose -f infra/docker-compose.yml stop
```
To restart it again:
```powershell
docker compose -f infra/docker-compose.yml start
```

### Tear Down Container & Reset Data
To stop and remove the container while preserving the database volume:
```powershell
docker compose -f infra/docker-compose.yml down
```
To completely reset and wipe the local development database volume:
```powershell
docker compose -f infra/docker-compose.yml down -v
```

---

## 3. Configuration & Port Overrides

Default settings are pre-configured to match the backend defaults:
- **Host Port**: `5432`
- **Database Name**: `guftagu_dev`
- **Username**: `guftagu_user`
- **Password**: `guftagu_dev_password`
- **Named Volume**: `guftagu_dev_pgdata`

### Overriding the Host Port (If 5432 Is Occupied)
If your host already has a local PostgreSQL instance running on port `5432`, configure an alternative port:

1. **Docker Compose variable substitution**:
   The repository `.env` file is consumed by Docker Compose for variable substitution. Copy `.env.example` to `.env`:
   ```powershell
   Copy-Item .env.example .env
   ```
   Update the port in `.env`:
   ```env
   GUFTAGU_POSTGRES_PORT=5433
   ```
   Start the container with the override:
   ```powershell
   docker compose -f infra/docker-compose.yml up -d
   ```

2. **Spring Boot datasource overrides**:
   Spring Boot does not automatically load the root `.env` file. For Spring Boot datasource overrides in PowerShell, set the required environment variable in your shell session before starting the backend:
   ```powershell
   $env:GUFTAGU_DATABASE_URL = "jdbc:postgresql://localhost:5433/guftagu_dev"
   ```
   *(Note: Setting `$env:GUFTAGU_POSTGRES_PORT = "5433"` in your shell also overrides the port for Docker Compose commands in the same session without needing a `.env` file).*

---

## 4. Running the Spring Boot Backend

Once PostgreSQL is healthy, start the backend from the repository root:

```powershell
.\backend\mvnw.cmd spring-boot:run -f backend/pom.xml
```

### Automatic Flyway Migrations
On startup, Spring Boot connects to the local PostgreSQL database and automatically executes any pending Flyway SQL migrations (`V1` through `V6` in `backend/src/main/resources/db/migration/`). Hibernate then validates the entity mappings against the live schema (`ddl-auto: validate`).

---

## 5. Architectural Boundaries

- **No Redis in Phase 2**: Redis is reserved for ephemeral workloads (presence, typing indicators, rate limits in later phases). No Redis container is started in Phase 2 to prevent running unused background services.
- **Test Isolation**: Local Docker Compose is strictly for developer runtime. Automated integration tests (`*IT.java`) manage their own disposable PostgreSQL containers via **Testcontainers** independently of Compose.
