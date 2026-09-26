# CI Workflows

This directory contains the GitHub Actions workflows for continuous integration across the repository.

---

## Backend CI (`backend-ci.yml`)

The `backend-ci.yml` workflow automates build verification and automated testing for the Spring Boot backend.

### Triggers and Path Filtering
The workflow runs on:
- `push` to `main`
- `pull_request` targeting `main`

It executes only when changes touch relevant backend and contract paths:
- `backend/**`
- `contracts/**`
- `.github/workflows/backend-ci.yml`

Changes restricted to documentation or other root files do not trigger this workflow.

### Concurrency
The workflow specifies concurrency cancellation:
```yaml
concurrency:
  group: ${{ github.workflow }}-${{ github.ref }}
  cancel-in-progress: ${{ github.event_name == 'pull_request' }}
```
If new commits are pushed to an open pull request while a build is already running, the outdated run is cancelled automatically.

### Permissions
The workflow applies the principle of least privilege:
```yaml
permissions:
  contents: read
```
The GITHUB_TOKEN has read-only access to repository contents.

### Environment & Build Pipeline
The workflow runs on `ubuntu-latest` within a 15-minute timeout window:

1. **Repository Checkout**: Uses `actions/checkout@v4`.
2. **Java 21 Setup**: Configures Eclipse Temurin JDK 21 using `actions/setup-java@v4` with Maven dependency caching (`cache: 'maven'`) enabled to reuse downloaded dependencies across runs.
3. **Wrapper Permissions**: Sets executable permissions on the Maven wrapper script (`chmod +x backend/mvnw`).
4. **Unit Tests**: Runs `./backend/mvnw -B clean test -f backend/pom.xml` to compile source code and execute unit tests via the Surefire plugin.
5. **Integration Tests**: Runs `./backend/mvnw -B verify -Pintegration-tests -f backend/pom.xml` to execute PostgreSQL integration tests via the Failsafe plugin.

### Testcontainers Isolation
Integration tests execute using Testcontainers (`postgres:16-alpine`). On `ubuntu-latest`, Testcontainers connects directly to the runner's native Docker daemon. The database lifecycle (container creation, migration via Flyway, test execution, and disposal via Ryuk) is managed entirely within the test process without requiring external service containers or local Docker Compose services.
