# Cloud Run And Supabase Deployment

This document records the Issue #46 deployment baseline for FaithLog.

## Decisions

- Runtime target: Google Cloud Run container.
- Database target: Supabase PostgreSQL, starting from a new database.
- Migration strategy: Flyway V1 initial schema for the current stabilized MVP entity model.
- Cloud Run project, region, service name, and Artifact Registry repository are not fixed in this issue. Confirm those values with the PM before running a real deployment.
- Store real database URLs, passwords, JWT secrets, and Firebase Admin credentials only in the deployment environment or secret manager. Do not commit them to the repository.

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

JWT_SECRET=<strong-secret>
JWT_ACCESS_TOKEN_VALIDITY_SECONDS=1800
JWT_REFRESH_TOKEN_VALIDITY_SECONDS=1209600

FIREBASE_CONFIG_JSON=<firebase-admin-json-secret>
FIREBASE_CONFIG_PATH=

SPRINGDOC_API_DOCS_ENABLED=false
SPRINGDOC_SWAGGER_UI_ENABLED=false
```

`FIREBASE_CONFIG_JSON` is preferred for Cloud Run because it can be mounted from a secret value without adding a key file to the image. If file-based credentials are used later, mount the file through the platform and set `FIREBASE_CONFIG_PATH` to that mounted path.

## Supabase Connection Mode

Use two connection modes intentionally:

- Flyway migration and schema inspection: use the Supabase direct PostgreSQL connection when available. Migration is an administrative operation and should not be mixed with high-throughput application pooling behavior.
- Cloud Run application traffic: prefer the Supabase pooler connection when direct IPv4 access or connection limits make direct connections risky. Keep `SPRING_DATASOURCE_HIKARI_MAXIMUM_POOL_SIZE` conservative, starting at `5`, and adjust only with measured evidence.

The JDBC URL must include Supabase-required SSL settings when the selected connection string requires them. Use placeholders in docs and issues; never paste the real connection string.

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
  --set-env-vars "SPRING_PROFILES_ACTIVE=prod,SPRING_FLYWAY_ENABLED=true,SPRING_JPA_HIBERNATE_DDL_AUTO=validate"
```

Use Cloud Run secret injection for sensitive values rather than `--set-env-vars`.

## Verification Checklist

- `./gradlew test`
- `./gradlew build`
- `./gradlew asciidoctor`
- Docker image build succeeds.
- Docker PostgreSQL clean database runs Flyway V1 successfully.
- App starts with `prod` profile against the migrated PostgreSQL schema and `ddl-auto=validate`.
- `/api/v1/health` returns `status=UP`.
- Search confirms no real Supabase URL, DB password, JWT secret, Firebase key, `.env`, or Firebase Admin SDK key file is committed.
