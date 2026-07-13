# Issue #186 Spring Security Maintenance Upgrade Report

## Scope And Decision

- Base: `origin/develop` at `f3e81fb9b3c2afbc4ad9342eb6cf6bf55e19c553`
- Branch: `security/186-spring-security-maintenance-upgrade`
- Approved path: Spring Boot plugin/BOM `3.5.0 -> 3.5.15`
- Spring Security modules are managed only by the Spring Boot BOM. No individual Security module override was added.
- Production `SecurityConfig`, authentication/authorization behavior, API contracts, database schema, Flyway, infrastructure configuration, and explicit eager-header workarounds are outside the change set.

Spring Boot 3.5.15 is the first approved 3.5.x maintenance release that manages Spring Security 6.5.11. Spring Security's official advisory marks 6.5.0 through 6.5.10 as affected and 6.5.11 as the OSS fix.

Official sources:

- [Spring Boot 3.5.15 release](https://github.com/spring-projects/spring-boot/releases/tag/v3.5.15)
- [Spring Security CVE-2026-41003](https://spring.io/security/cve-2026-41003/)

## Resolved Version Manifest

| Component | Before | After |
| --- | --- | --- |
| Spring Boot plugin/BOM and starters | 3.5.0 | 3.5.15 |
| Spring Framework | 6.2.7 | 6.2.19 |
| Spring Security config/core/crypto/web | 6.5.0 | 6.5.11 |
| Spring Security test | 6.5.0 | 6.5.11 |
| Spring Data commons/JPA/keyvalue/Redis | 3.5.0 | 3.5.12 |
| Hibernate ORM | 6.6.15.Final | 6.6.53.Final |
| Lettuce | 6.5.5.RELEASE | 6.6.0.RELEASE |
| Jackson BOM | 2.19.0 | 2.21.4 |
| HikariCP | 6.3.0 | 6.3.3 |
| Tomcat | 10.1.41 | 10.1.55 |
| PostgreSQL JDBC | 42.7.5 | 42.7.11 |

- Runtime resolved coordinates: 208 before, 209 after.
- Version changes: 81.
- Added coordinates: 1.
- Removed coordinates: 0.
- Runtime and test `dependencyInsight` vulnerable Spring Security 6.5.0-6.5.10 occurrences: 0.
- All resolved Spring Security config/core/crypto/web/test modules: 6.5.11.

### Independent Runtime And Test Dependency Contract

The PM follow-up review found that scanning JAR manifests from the test JVM could let a
safe `testRuntimeClasspath` mask a vulnerable production `runtimeClasspath`. The final
contract now reads Gradle's resolved artifacts from both configurations independently,
filters the `org.springframework.security` group, and supplies two sorted `module=version`
manifests to the test JVM as tracked `Test` task inputs.

Actual final manifests:

```text
runtimeClasspath=spring-security-config=6.5.11,spring-security-core=6.5.11,spring-security-crypto=6.5.11,spring-security-web=6.5.11
testRuntimeClasspath=spring-security-config=6.5.11,spring-security-core=6.5.11,spring-security-crypto=6.5.11,spring-security-test=6.5.11,spring-security-web=6.5.11
```

- Runtime required modules: config/core/crypto/web.
- Test required modules: config/core/crypto/web/test.
- Every discovered module must be at least 6.5.11.
- Versions must be an exact numeric three-part release. RC, M, SNAPSHOT, `.Final`, build
  metadata, two-part, and four-part versions fail.
- A missing live Gradle manifest fails instead of falling back to the test classloader.
- The Java plugin's `check` lifecycle includes `test`; the final `build` run confirmed the
  contract stays on the normal check path without another plugin or dependency.

## Complete Runtime Resolved Dependency Diff

The table below contains every changed or added coordinate from reproducible `runtimeClasspath` reports. Unchanged coordinates are omitted; removed coordinates are zero.

| Coordinate | Before | After |
| --- | --- | --- |
| `ch.qos.logback:logback-classic` | `1.5.18` | `1.5.34` |
| `ch.qos.logback:logback-core` | `1.5.18` | `1.5.34` |
| `com.fasterxml:classmate` | `1.7.0` | `1.7.3` |
| `com.fasterxml.jackson:jackson-bom` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.core:jackson-annotations` | `2.19.0` | `2.21` |
| `com.fasterxml.jackson.core:jackson-core` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.core:jackson-databind` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-toml` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-xml` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.dataformat:jackson-dataformat-yaml` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.datatype:jackson-datatype-jdk8` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.datatype:jackson-datatype-jsr310` | `2.19.0` | `2.21.4` |
| `com.fasterxml.jackson.module:jackson-module-parameter-names` | `2.19.0` | `2.21.4` |
| `com.fasterxml.woodstox:woodstox-core` | `7.1.0` | `7.0.0` |
| `com.google.code.gson:gson` | `2.13.1` | `2.13.2` |
| `com.zaxxer:HikariCP` | `6.3.0` | `6.3.3` |
| `io.lettuce:lettuce-core` | `6.5.5.RELEASE` | `6.6.0.RELEASE` |
| `io.micrometer:micrometer-commons` | `1.15.0` | `1.15.12` |
| `io.micrometer:micrometer-core` | `1.15.0` | `1.15.12` |
| `io.micrometer:micrometer-jakarta9` | `1.15.0` | `1.15.12` |
| `io.micrometer:micrometer-observation` | `1.15.0` | `1.15.12` |
| `io.projectreactor:reactor-core` | `3.7.6` | `3.7.19` |
| `jakarta.activation:jakarta.activation-api` | `2.1.3` | `2.1.4` |
| `jakarta.xml.bind:jakarta.xml.bind-api` | `4.0.2` | `4.0.5` |
| `net.bytebuddy:byte-buddy` | `1.17.5` | `1.17.8` |
| `org.antlr:antlr4-runtime` | `4.13.0` | `4.13.2` |
| `org.apache.httpcomponents.client5:httpclient5` | `5.4.4` | `5.5.2` |
| `org.apache.httpcomponents.core5:httpcore5` | `5.3.4` | `5.3.6` |
| `org.apache.httpcomponents.core5:httpcore5-h2` | `5.3.4` | `5.3.6` |
| `org.apache.tomcat.embed:tomcat-embed-core` | `10.1.41` | `10.1.55` |
| `org.apache.tomcat.embed:tomcat-embed-el` | `10.1.41` | `10.1.55` |
| `org.apache.tomcat.embed:tomcat-embed-websocket` | `10.1.41` | `10.1.55` |
| `org.aspectj:aspectjweaver` | `1.9.24` | `1.9.25.1` |
| `org.eclipse.angus:angus-activation` | `2.0.2` | `2.0.3` |
| `org.glassfish.jaxb:jaxb-core` | `4.0.5` | `4.0.9` |
| `org.glassfish.jaxb:jaxb-runtime` | `4.0.5` | `4.0.9` |
| `org.glassfish.jaxb:txw2` | `4.0.5` | `4.0.9` |
| `org.hibernate.orm:hibernate-core` | `6.6.15.Final` | `6.6.53.Final` |
| `org.hibernate.validator:hibernate-validator` | `8.0.2.Final` | `8.0.3.Final` |
| `org.jboss.logging:jboss-logging` | `3.6.1.Final` | `3.6.3.Final` |
| `org.postgresql:postgresql` | `42.7.5` | `42.7.11` |
| `org.slf4j:jul-to-slf4j` | `2.0.17` | `2.0.18` |
| `org.slf4j:slf4j-api` | `2.0.17` | `2.0.18` |
| `org.springframework:spring-aop` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-aspects` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-beans` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-context` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-context-support` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-core` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-expression` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-jcl` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-jdbc` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-orm` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-oxm` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-tx` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-web` | `6.2.7` | `6.2.19` |
| `org.springframework:spring-webmvc` | `6.2.7` | `6.2.19` |
| `org.springframework.boot:spring-boot` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-actuator` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-actuator-autoconfigure` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-autoconfigure` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-actuator` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-data-jpa` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-data-redis` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-jdbc` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-json` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-logging` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-security` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-tomcat` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-validation` | `3.5.0` | `3.5.15` |
| `org.springframework.boot:spring-boot-starter-web` | `3.5.0` | `3.5.15` |
| `org.springframework.data:spring-data-commons` | `3.5.0` | `3.5.12` |
| `org.springframework.data:spring-data-jpa` | `3.5.0` | `3.5.12` |
| `org.springframework.data:spring-data-keyvalue` | `3.5.0` | `3.5.12` |
| `org.springframework.data:spring-data-redis` | `3.5.0` | `3.5.12` |
| `org.springframework.security:spring-security-config` | `6.5.0` | `6.5.11` |
| `org.springframework.security:spring-security-core` | `6.5.0` | `6.5.11` |
| `org.springframework.security:spring-security-crypto` | `6.5.0` | `6.5.11` |
| `org.springframework.security:spring-security-web` | `6.5.0` | `6.5.11` |
| `org.webjars:webjars-locator-lite` | `1.1.0` | `1.1.3` |
| `redis.clients.authentication:redis-authx-core` | 없음 | `0.1.1-beta2` |

## TDD Evidence

RED was committed before the build change in `cc0aa8b`.

Command:

```text
./gradlew --no-daemon --console=plain test \
  --tests 'com.faithlog.global.security.SpringSecurityDependencyVersionContractTest' \
  --tests 'com.faithlog.global.security.SecurityHeaderRegressionTest'
```

RED result:

- 5 tests executed.
- 4 header tests passed.
- Dependency contract failed because config/core/crypto/web/test resolved to 6.5.0, below 6.5.11.

GREEN result after the one-line Boot upgrade:

- Dependency contract and 200/401/403/404 MockMvc header tests all passed.
- Focused auth/session/role invalidation/FCM/REST Docs suite: 59 tests, 59 passed, 0 failed, 0 errors, 0 skipped.
- Full suite: 404 tests, 401 passed, 0 failed, 0 errors, 3 skipped.
- Real Redis Lua integration: 1 test passed.
- `./gradlew build`: success.
- `./gradlew asciidoctor`: success.
- `git diff --check`: success.

### PM Review Follow-up RED/GREEN

The two review findings were reproduced before the contract implementation in test-only
commit `d5fec90`.

```text
./gradlew --no-daemon --console=plain test \
  --tests 'com.faithlog.global.security.SpringSecurityDependencyVersionContractTest'
```

RED result:

- 3 tests executed, 2 failed.
- A vulnerable runtime manifest (`6.5.10`) was ignored while the safe test classpath made
  the old live contract pass.
- `6.5.11-RC1` was accepted after the old parser removed its qualifier.

GREEN result after `b266272`:

- Dependency contract: 10 tests, 0 failures, 0 errors, 0 skipped.
- Previous 59-test focused class range: 68 tests, 0 failures, 0 errors, 0 skipped. The
  increase is exactly nine new dependency-contract cases.
- Full suite: 413 tests, 410 passed, 0 failures, 0 errors, 3 skipped.
- `./gradlew build`: success; `check` and the full `test` task remained in its lifecycle.
- `./gradlew asciidoctor`: success.
- Final runtime/test `dependencyInsight`: all five relevant modules are release 6.5.11;
  vulnerable 6.5.0-6.5.10 occurrences are zero.
- `git diff --check`: success.
- PM independent structure review found no new blocking finding and approved both prior
  false-green findings as resolved.

## Security Header Results

`SecurityHeaderRegressionTest` is a general 200/401/403/MockMvc 404 default-header
regression test. It is not a CVE exploit reproducer. The PM follow-up changed neither this
test nor production header behavior.

The approved default header set checked by MockMvc is:

- `Cache-Control: no-cache, no-store, max-age=0, must-revalidate`
- `Pragma: no-cache`
- `Expires: 0`
- `X-Content-Type-Options: nosniff`
- `X-XSS-Protection: 0`
- `X-Frame-Options: DENY`
- Secure-request HSTS: `max-age=31536000 ; includeSubDomains`

| Scenario | MockMvc | Actual Docker HTTP |
| --- | --- | --- |
| Public health 200 | passed | status and default non-HSTS headers passed |
| Unauthenticated 401 | passed | status/code and default non-HSTS headers passed |
| Insufficient-role 403 | passed | status/code and default non-HSTS headers passed |
| Unmatched path 404 | passed | not representative of servlet ERROR dispatch; see limitation below |

Actual Docker HTTP uses plain HTTP, so HSTS is correctly absent there and is covered only by the secure MockMvc request.

### Unmatched-Path Limitation

MockMvc returns the direct handler lookup 404 and does not reproduce the complete servlet container ERROR dispatch in this test. The isolated Docker comparison showed:

- Spring Boot 3.5.0 baseline: valid token + unmatched path -> `401 AUTH_UNAUTHORIZED`.
- Spring Boot 3.5.15 change: valid token + unmatched path -> `401 AUTH_UNAUTHORIZED`.
- The same token returned 200 from `/api/v1/users/me` immediately before each unmatched-path request.

Therefore this is not a #186 regression. PM selected the no-behavior-change path: do not permit `DispatcherType.ERROR` or `/error` in this issue, do not report actual HTTP 404 success, and leave any future unmatched-path status policy as a separate user decision candidate without creating an issue.

## Auth And Session Regression

Preserved and verified:

- Access token lifetime: 1,800 seconds.
- Refresh token lifetime: 1,209,600 seconds.
- Refresh rotation replaces both tokens while retaining the session contract.
- Reusing the old refresh token returns 401 and revokes the compromised session.
- The rotated access and refresh tokens are rejected after reuse detection.
- Logout invalidates the current access token and refresh session.
- Redis Lua concurrency permits exactly one rotation winner and one rejected loser, removes the refresh key, creates the session marker, and preserves the 1,209,660-second revocation TTL boundary.
- tokenVersion service/campus role invalidation, withdrawal scope, FCM lifecycle, 401 authentication versus 403 authorization, and REST Docs remained GREEN.

## Docker QA

- Compose project: `faithlog-qa-186-20260713`.
- Isolated named volumes: PostgreSQL and Redis volumes were created under that project and preserved.
- PostgreSQL 17 and Redis 7: healthy.
- Backend: Spring Boot 3.5.15, Spring 6.2.19, Hibernate 6.6.53.Final, health 200/UP.
- Actual HTTP: signup 201, login 200, token TTLs preserved, 200/401/403 headers passed, refresh rotation passed, rotate-or-revoke passed, logout invalidation passed.
- No access/refresh token values were printed by the QA script or written to project documentation.
- The framework-generated local development credential value observed in startup diagnostics was not used or recorded in this report.
- Docker Desktop engine initially stopped responding. It was recovered with a data-preserving forced Desktop stop/start; no reset or volume deletion was performed.
- Baseline diagnostic container was stopped and removed.
- Compose shutdown used `docker compose -p faithlog-qa-186-20260713 down` without `-v`.
- Final Docker command: `docker builder prune -f`.
- Reclaimed build cache: 1.4GB.
- No Docker commands were run after the builder prune.
- Docker was not rerun for the PM follow-up because it changed only the test contract and
  build-script test inputs, with no production or runtime behavior change.

## Static Scope Verification

- Production Java diff: 0 files.
- `SecurityConfig`, `JwtAuthenticationFilter`, and `JwtProvider` production diff: 0.
- API mappings, request/response DTOs, ErrorCode/status/message diff: 0.
- Entity/database/Flyway diff: 0.
- Cloud Run/GCP/Supabase/Upstash/Firebase configuration diff: 0.
- Gradle wrapper, dependency locking/verification metadata, Docker digest, and GitHub Action SHA pinning diff: 0.
- Controller Entity return and Swagger annotation policy were not changed.
- `docs/decision-log.md` was not changed because #186 did not introduce a product/API/security behavior decision.
- PM follow-up implementation diff was limited to `build.gradle.kts` and
  `SpringSecurityDependencyVersionContractTest`; documentation was then synchronized.
- Push and PR were not performed.
