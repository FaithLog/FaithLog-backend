# Issue #161 deployment infrastructure and supply-chain audit matrix

## 1. Audit baseline and counting rules

- Audit date: 2026-07-13
- Baseline commit: `f3e81fb9b3c2afbc4ad9342eb6cf6bf55e19c553` (`origin/develop`)
- Branch: `audit/161-deployment-supply-chain-security`
- Scope: read-only repository and Git-history analysis, safe dependency metadata checks, and documentation
- Excluded: production/test/config/database/Flyway/runtime infrastructure changes, live Cloud Run smoke or load,
  production Supabase/Upstash/Firebase access, credential validation, image modification, push, and PR
- Sensitive-data rule: count and classify matching files and commits without printing or recording matched values

Every manifest below has its own denominator. A file can legitimately appear in more than one manifest when it
crosses trust boundaries, so the section totals are not added into one artificial grand total. Supporting policy
documents and the nine predecessor audit documents under `docs/security/157-*` through `160-*` are read as
evidence but are not added to the runtime manifest counts.

## 2. Counted manifest summary

| Manifest | Count | Counting unit |
| --- | ---: | --- |
| GitHub Actions workflows | 2 | workflow files |
| GitHub Actions action invocations | 8 | `uses:` rows, 6 unique coordinates |
| Docker/container build surface | 3 | `Dockerfile`, Compose, build-context ignore file |
| Gradle supply-chain control surface | 6 | build/settings/wrapper scripts and wrapper artifacts |
| Direct Gradle dependency declarations | 20 | dependency declaration rows |
| Environment templates | 4 | tracked `.env*.example` files |
| Spring application profile templates | 6 | five main/profile files plus one test profile |
| Deployment contract documents | 1 | Cloud Run/Supabase/Upstash contract |
| Security header/CORS/actuator/SpringDoc surface | 4 | matching production config/source files |
| Firebase infrastructure adapters | 8 | files in the production/local FCM adapter package |
| Firebase/FCM failure and source-of-truth trace | 14 | adapter, worker, retry, Entity, Repository files |
| Redis infrastructure adapters/config | 7 | non-`package-info` infrastructure/config files |
| Redis fail-open/fail-closed trace | 9 | adapters plus auth/notification decision services |
| Flyway migrations | 7 | V1 through V7 migration files |

## 3. GitHub Actions manifest

### 3.1 Workflow files (2)

1. `.github/workflows/ci.yml`
2. `.github/workflows/project-docs-check.yml`

### 3.2 Action invocation rows (8, 6 unique coordinates)

| # | Workflow:line | Action coordinate |
| ---: | --- | --- |
| 1 | `.github/workflows/ci.yml:64` | `actions/checkout@v4` |
| 2 | `.github/workflows/ci.yml:78` | `actions/setup-java@v4` |
| 3 | `.github/workflows/ci.yml:86` | `gradle/actions/wrapper-validation@v4` |
| 4 | `.github/workflows/ci.yml:102` | `actions/upload-artifact@v4` |
| 5 | `.github/workflows/ci.yml:124` | `actions/checkout@v4` |
| 6 | `.github/workflows/ci.yml:137` | `docker/setup-buildx-action@v3` |
| 7 | `.github/workflows/ci.yml:141` | `docker/build-push-action@v6` |
| 8 | `.github/workflows/project-docs-check.yml:19` | `actions/checkout@v4` |

The six unique coordinates are `actions/checkout@v4`, `actions/setup-java@v4`,
`gradle/actions/wrapper-validation@v4`, `actions/upload-artifact@v4`,
`docker/setup-buildx-action@v3`, and `docker/build-push-action@v6`.

## 4. Docker/container manifest (3)

1. `Dockerfile`
2. `docker-compose.yml`
3. `.dockerignore`

`.gitignore` is audited separately as a repository secret-control file. The four env templates and deployment
document are not added to the three-file container denominator even though their interaction with build context
is traced.

## 5. Gradle supply-chain manifest

### 5.1 Build and wrapper files (6)

1. `build.gradle.kts`
2. `settings.gradle.kts`
3. `gradlew`
4. `gradlew.bat`
5. `gradle/wrapper/gradle-wrapper.jar`
6. `gradle/wrapper/gradle-wrapper.properties`

No `gradle.lockfile`, dependency lock state, `gradle/verification-metadata.xml`, or version catalog exists at
the baseline. Absence is recorded here without counting nonexistent files.

### 5.2 Direct dependency declaration rows (20)

| Scope | Count | Coordinates or managed modules |
| --- | ---: | --- |
| `implementation` | 11 | Actuator, JPA, Redis, Security, Validation, Web, Flyway core, SpringDoc, Firebase Admin, Commons Lang, JJWT API |
| `runtimeOnly` | 4 | JJWT impl, JJWT Jackson, Flyway PostgreSQL, PostgreSQL driver |
| `testImplementation` | 3 | Spring Boot Test, REST Docs MockMvc, Spring Security Test |
| `testRuntimeOnly` | 2 | H2, JUnit Platform launcher |
| **Total** | **20** |  |

The build also declares five plugin rows: the core `java` and `jacoco` plugins plus version-pinned Spring Boot,
Spring dependency-management, and Asciidoctor plugins. The plugin rows are reported separately from the 20
dependency declarations.

## 6. Configuration and deployment template manifest

### 6.1 Environment templates (4)

1. `.env.example`
2. `.env.local.example`
3. `.env.docker.example`
4. `.env.prod.example`

### 6.2 Spring application profile files (6)

1. `src/main/resources/application.yml`
2. `src/main/resources/application-dev.yml`
3. `src/main/resources/application-docker.yml`
4. `src/main/resources/application-local.yml`
5. `src/main/resources/application-prod.example.yml`
6. `src/test/resources/application-test.yml`

`application-prod.example.yml` is a deployment template, not a Spring profile file that is automatically loaded
as `application-prod.yml`. Runtime production values therefore require the documented environment-variable or
platform-secret contract.

### 6.3 Deployment contract (1)

1. `docs/deploy/cloud-run-supabase.md`

## 7. Security header, CORS, actuator, and SpringDoc manifest (4)

1. `src/main/java/com/faithlog/global/config/OpenApiConfig.java`
2. `src/main/java/com/faithlog/global/security/SecurityConfig.java`
3. `src/main/resources/application.yml`
4. `src/main/resources/application-prod.example.yml`

Production source/config matches for an explicit CORS policy: 0 files. Production source/config matches for
explicit CSP, frame, HSTS, referrer, or permissions-policy configuration: 0 files. These zero counts state the
repository evidence only; Cloud Run or an upstream gateway can add controls outside the repository.

## 8. Firebase/FCM manifest

### 8.1 Infrastructure adapter package (8)

1. `src/main/java/com/faithlog/notification/infrastructure/fcm/AsyncNotificationDispatchAdapter.java`
2. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseAdminFcmConfig.java`
3. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseAdminMessagingClient.java`
4. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmFailure.java`
5. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmFailureClassifier.java`
6. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmSendAdapter.java`
7. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseMessagingClient.java`
8. `src/main/java/com/faithlog/notification/infrastructure/fcm/NoOpFcmSendAdapterConfig.java`

### 8.2 Failure/source-of-truth trace (14)

1. `src/main/java/com/faithlog/notification/domain/entity/NotificationLog.java`
2. `src/main/java/com/faithlog/notification/domain/entity/UserFcmToken.java`
3. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseAdminFcmConfig.java`
4. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseAdminMessagingClient.java`
5. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmFailure.java`
6. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmFailureClassifier.java`
7. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseFcmSendAdapter.java`
8. `src/main/java/com/faithlog/notification/infrastructure/fcm/FirebaseMessagingClient.java`
9. `src/main/java/com/faithlog/notification/infrastructure/fcm/NoOpFcmSendAdapterConfig.java`
10. `src/main/java/com/faithlog/notification/infrastructure/repository/NotificationLogRepository.java`
11. `src/main/java/com/faithlog/notification/infrastructure/repository/UserFcmTokenRepository.java`
12. `src/main/java/com/faithlog/notification/service/NotificationDeliveryWorker.java`
13. `src/main/java/com/faithlog/notification/service/NotificationRetryBackoff.java`
14. `src/main/java/com/faithlog/notification/service/ThreadSleepingNotificationRetryBackoff.java`

## 9. Redis manifest

### 9.1 Infrastructure adapters/config (7)

1. `src/main/java/com/faithlog/global/config/RedisConfig.java`
2. `src/main/java/com/faithlog/notification/infrastructure/redis/RedisNotificationDeduplicationAdapter.java`
3. `src/main/java/com/faithlog/notification/infrastructure/redis/RedisNotificationLockAdapter.java`
4. `src/main/java/com/faithlog/user/infrastructure/redis/AuthRedisKeys.java`
5. `src/main/java/com/faithlog/user/infrastructure/redis/RedisAccessTokenBlacklistStore.java`
6. `src/main/java/com/faithlog/user/infrastructure/redis/RedisRefreshTokenStore.java`
7. `src/main/java/com/faithlog/user/infrastructure/redis/RedisSessionRevocationChecker.java`

### 9.2 Fail-open/fail-closed decision trace (9)

1. `src/main/java/com/faithlog/global/config/RedisConfig.java`
2. `src/main/java/com/faithlog/notification/infrastructure/redis/RedisNotificationDeduplicationAdapter.java`
3. `src/main/java/com/faithlog/notification/infrastructure/redis/RedisNotificationLockAdapter.java`
4. `src/main/java/com/faithlog/notification/service/NotificationDeduplicationService.java`
5. `src/main/java/com/faithlog/notification/service/NotificationLockService.java`
6. `src/main/java/com/faithlog/user/infrastructure/redis/AuthRedisKeys.java`
7. `src/main/java/com/faithlog/user/infrastructure/redis/RedisAccessTokenBlacklistStore.java`
8. `src/main/java/com/faithlog/user/infrastructure/redis/RedisRefreshTokenStore.java`
9. `src/main/java/com/faithlog/user/infrastructure/redis/RedisSessionRevocationChecker.java`

## 10. PostgreSQL/Flyway manifest

JDBC, pool, Flyway, and Hibernate production contracts are traced through the six application profile files in
section 6, the deployment contract, and these seven migrations:

1. `src/main/resources/db/migration/V1__initial_schema.sql`
2. `src/main/resources/db/migration/V2__add_poll_user_option_fields.sql`
3. `src/main/resources/db/migration/V3__split_active_coffee_payment_account_owner_scope.sql`
4. `src/main/resources/db/migration/V4__add_payment_account_soft_delete.sql`
5. `src/main/resources/db/migration/V5__fix_fcm_token_active_uniqueness.sql`
6. `src/main/resources/db/migration/V6__add_user_deleted_at.sql`
7. `src/main/resources/db/migration/V7__enforce_positive_charge_amount.sql`

No database, migration, or configuration file is modified by this audit.

## 11. Secret-scan census at baseline

| Surface | Count | Classification |
| --- | ---: | --- |
| tracked non-example sensitive-path files | 0 | no tracked `.env`, Firebase Admin JSON, PEM/key store, or secret profile |
| tracked env example files | 4 | placeholder/example contract |
| untracked files | 0 | clean worktree |
| ignored sensitive-path files in this worktree | 0 | none observed |
| current high-signal credential candidate files | 0 | prefix/format scan, values not printed |
| Git-history high-signal candidate commits | 0 | diff-pattern scan, values not printed |
| Git-history non-example sensitive-filename commits | 0 | filename-only scan |
| current private-key Firebase JSON candidate files | 0 | JSON key-name scan |
| current generic secret-reference files | 17 | variable names, placeholders, policies, and adapters; not credential values |
| history generic secret-reference commits | 25 | configuration-contract history; not counted as leaked credentials |

## 12. Reproduction commands

The audit uses filename-only or count-only secret scans. Commands that could print matched values are not used.

```text
rg --files --hidden .github/workflows
rg -n '^\s*uses:' .github/workflows
rg --files src/main/java/com/faithlog/notification/infrastructure/fcm
rg --files src/main/resources/db/migration
git ls-files '.env*'
git status --porcelain --untracked-files=all
git log --all -G '<high-signal-format-pattern>' --format='%H' --no-patch
```

The findings report will keep repository-confirmed facts separate from GitHub, GCP, Supabase, Upstash, and
Firebase console checks that cannot be proved from this manifest.
