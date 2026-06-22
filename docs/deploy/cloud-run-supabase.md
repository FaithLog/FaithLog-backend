# Cloud Run, Supabase, And Upstash Deployment

This document records the Issue #46 deployment baseline for FaithLog.

## Decisions

- Runtime target: Google Cloud Run container.
- Database target: Supabase PostgreSQL, starting from a new database.
- Redis target: Upstash Redis for `prod` / Cloud Run only.
- Migration strategy: Flyway V1 initial schema for the current stabilized MVP entity model.
- Cloud Run project, region, service name, and Artifact Registry repository are not fixed in this issue. Confirm those values with the PM before running a real deployment.
- Store real database URLs, database passwords, Redis passwords, JWT secrets, and Firebase Admin credentials only in the deployment environment or secret manager. Do not commit them to the repository.

## Endpoints

- Application health endpoint: `/api/v1/health`
- Actuator health endpoint: `/actuator/health`
- Cloud Run should use the container port provided by `PORT`; the app falls back to `SERVER_PORT` and then `8080`.
- Cloud Run provides a managed HTTPS service URL. Custom domain mapping and managed certificates should use Google Cloud managed flows when they are needed.

References:

- [Cloud Run ingress and default HTTPS endpoint](https://cloud.google.com/run/docs/securing/ingress)
- [Cloud Run custom domain mapping](https://cloud.google.com/run/docs/mapping-custom-domains)
- [Google-managed certificates](https://cloud.google.com/load-balancing/docs/ssl-certificates/google-managed-certs)
- [Supabase connection management](https://supabase.com/docs/guides/database/connection-management)
- [Supabase database connection guide](https://supabase.com/docs/guides/database/connecting-to-postgres)

## Required Environment Variables

Cloud Run must inject these values for the `prod` profile:

```text
SPRING_PROFILES_ACTIVE=prod
PORT=8080

SPRING_DATASOURCE_URL=<supabase-jdbc-url>
SPRING_DATASOURCE_USERNAME=<supabase-database-user>
SPRING_DATASOURCE_PASSWORD=<supabase-database-password>
SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE=5

SPRING_FLYWAY_ENABLED=true
SPRING_JPA_HIBERNATE_DDL_AUTO=validate

SPRING_DATA_REDIS_HOST=<redis-host>
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<upstash-redis-password>
SPRING_DATA_REDIS_SSL_ENABLED=true

JWT_SECRET=<strong-secret>
JWT_ACCESS_TOKEN_VALIDITY_SECONDS=1800
JWT_REFRESH_TOKEN_VALIDITY_SECONDS=1209600

FIREBASE_CONFIG_JSON=<firebase-admin-json-secret>
FIREBASE_CONFIG_PATH=

SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

`FIREBASE_CONFIG_JSON` is preferred for Cloud Run because it can be mounted from a secret value without adding a key file to the image. If file-based credentials are used later, mount the file through the platform and set `FIREBASE_CONFIG_PATH` to that mounted path.

## Environment Split

Use one Docker image and split runtime behavior with Spring profiles and environment variables:

| Profile | Runtime | Database | Redis | Secret policy |
| --- | --- | --- | --- | --- |
| `local` | Direct local app execution | Local or Docker PostgreSQL | Local or Docker Redis | Dummy/example values only |
| `docker` | Docker Compose QA/development | Compose `postgres` service | Compose `redis` service | Dummy/example values only |
| `test` | Gradle/CI tests | H2 or test PostgreSQL only | No external Upstash dependency | No network secret required |
| `prod` | Cloud Run | Supabase PostgreSQL | Upstash Redis | Cloud Run secret injection or Secret Manager |

Environment example files:

- `.env.local.example`: direct local application execution.
- `.env.docker.example`: Docker Compose QA/development; must not point to Supabase or Upstash.
- `.env.prod.example`: Cloud Run production contract; contains only placeholder Supabase and Upstash values.

Do not commit real `.env`, `.env.local`, `.env.docker`, or `.env.prod` files.

## Supabase Connection Mode

Use two connection modes intentionally:

- Flyway migration and schema inspection: use the Supabase direct PostgreSQL connection when available. Migration is an administrative operation and should not be mixed with high-throughput application pooling behavior.
- Cloud Run application traffic: prefer the Supabase pooler connection when direct IPv4 access or connection limits make direct connections risky. Keep `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` conservative, starting at `5`, and adjust only with measured evidence.

The JDBC URL must include Supabase-required SSL settings when the selected connection string requires them. Use placeholders in docs and issues; never paste the real connection string.

## Upstash Redis Connection Mode

Use Spring Boot 3.5 Redis auto-configuration with explicit host, port, password, and SSL settings:

```text
SPRING_DATA_REDIS_HOST=<upstash-redis-host>
SPRING_DATA_REDIS_PORT=6379
SPRING_DATA_REDIS_PASSWORD=<upstash-redis-password>
SPRING_DATA_REDIS_SSL_ENABLED=true
```

This host/port/password/SSL contract is preferred over a single Redis URL because it matches the existing local/docker Redis structure and keeps the password out of connection-string-shaped values in docs and logs. Spring Boot 3.5 exposes `spring.data.redis.host`, `spring.data.redis.port`, `spring.data.redis.password`, and `spring.data.redis.ssl.enabled`; the application uses the auto-configured `RedisConnectionFactory` for all Redis-backed features.

Redis-backed features that use this connection:

- Refresh token allowlist.
- Access token blacklist.
- Notification deduplication.
- Notification execution locks.

Local and Docker profiles use Docker/local Redis host and port only. They do not use Upstash defaults. Actual Upstash connection verification requires secret injection and is not part of this repository validation.

## Flyway Migration

The repository contains the initial migration:

```text
src/main/resources/db/migration/V1__initial_schema.sql
```

The V1 strategy is a clean initial schema for a new Supabase database. Existing-data deployments require a separate PM-approved baseline plan before migration.

Local unit tests keep the `test` profile on H2 with Flyway disabled. CI and Docker verification must include a PostgreSQL Flyway migration check.

Expected migration behavior:

- `spring.flyway.enabled=true` in `prod`.
- `spring.jpa.hibernate.ddl-auto=validate` in `prod`.
- App startup fails if required datasource, Redis, JWT, or Firebase production credentials are missing.
- `charge_items.source_id` is intentionally not a foreign key because it is a polymorphic source that can point to `weekly_devotion_records.id` or `poll_responses.id` depending on `source_type`.

## Docker Image Build

Build the container image locally:

```bash
docker build -t faithlog-backend:local .
```

The `Dockerfile` is intentionally shared by local Docker QA and Cloud Run. Runtime differences come from `SPRING_PROFILES_ACTIVE` and environment variables, not from separate Dockerfiles.

Build and tag for Artifact Registry after PM confirms the real values:

```bash
GCP_PROJECT_ID=<gcp-project-id>
GCP_REGION=<gcp-region>
AR_REPOSITORY=<artifact-registry-repository>
IMAGE_NAME=faithlog-backend
IMAGE_TAG=<git-sha-or-release-tag>

docker build \
  -t "$GCP_REGION-docker.pkg.dev/$GCP_PROJECT_ID/$AR_REPOSITORY/$IMAGE_NAME:$IMAGE_TAG" \
  .
```

Push after authentication and PM approval:

```bash
docker push "$GCP_REGION-docker.pkg.dev/$GCP_PROJECT_ID/$AR_REPOSITORY/$IMAGE_NAME:$IMAGE_TAG"
```

## Cloud Run Deploy Command Template

Use this only after PM confirms the GCP project, region, service name, Artifact Registry repository, and secret injection method:

```bash
gcloud run deploy <cloud-run-service-name> \
  --image "<gcp-region>-docker.pkg.dev/<gcp-project-id>/<artifact-registry-repository>/faithlog-backend:<image-tag>" \
  --region "<gcp-region>" \
  --platform managed \
  --allow-unauthenticated \
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod,SPRING_FLYWAY_ENABLED=true,SPRING_JPA_HIBERNATE_DDL_AUTO=validate,SPRING_DATA_REDIS_SSL_ENABLED=true"
```

Use Cloud Run secret injection for sensitive values rather than `--set-env-vars`.

## Verification Checklist

- `./gradlew test`
- `./gradlew build`
- `./gradlew asciidoctor`
- Docker image build succeeds.
- Docker PostgreSQL clean database runs Flyway V1 successfully.
- Docker QA starts with Docker PostgreSQL and Docker Redis only.
- App starts with deployment-like DB/Flyway/JPA settings against the migrated PostgreSQL schema and `ddl-auto=validate`.
- `/api/v1/health` returns `status=UP`.
- Search confirms no real Supabase URL, Upstash URL/password/token, DB password, JWT secret, Firebase key, `.env`, or Firebase Admin SDK key file is committed.
