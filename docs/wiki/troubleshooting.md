<!-- daily-resume-monitor:start:troubleshooting:2026-06-16 -->
## 2026-06-16 Automated Review

- Problem: No troubleshooting item was promoted from this run without verified symptoms and root cause.
- Symptoms: Not recorded.
- Root cause: Not recorded.
- Fix: Not recorded.
- Validation: build/test-results/test/TEST-com.faithlog.FaithLogApplicationTests.xml: verified pass; tests=1, passed=1, failures=0, errors=0, skipped=0
- Remaining risk: Transcript source and health/latency target remain pending decisions.
<!-- daily-resume-monitor:end:troubleshooting:2026-06-16 -->

## 2026-06-17 #27 Auth JWT Test Context

- Problem: 인증 구현 후 `JwtProvider`와 MVC slice 테스트 context가 실패.
- Symptoms: `No default constructor found`, `No qualifying bean of type JwtProvider`, `No qualifying bean of type AccessTokenBlacklistChecker`.
- Root cause: `JwtProvider`에 복수 생성자가 있었지만 Spring 주입 생성자가 명시되지 않았고, `@WebMvcTest`가 filter 의존성 전체를 로드하지 않음.
- Fix: 운영 생성자에 `@Autowired`를 명시하고, `AuthControllerTest`에서 `JwtProvider`와 `AccessTokenBlacklistChecker`를 mock bean으로 분리.
- Validation: `./gradlew test` 성공.
- Remaining risk: #28에서 Redis blacklist 저장/삭제 구현 시 `AccessTokenBlacklistChecker` 실제 구현과 통합 테스트 필요.

## 2026-06-17 #27 Docker App Startup

- Problem: #27 수정 후 Docker image build는 성공했지만 compose app 컨테이너가 기동 완료 전에 종료.
- Symptoms: `faithlog-postgres`와 `faithlog-redis`는 healthy, `faithlog-backend`는 `Exited (1)`, 헬스체크 `localhost:8080` 연결 실패.
- Root cause: app 로그에서 PostgreSQL `FATAL: password authentication failed for user "faithlog"` 확인. 기존 Docker volume에 저장된 DB credential과 compose 기본 credential이 불일치하는 상태로 판단.
- Fix: 승인 없이 volume 삭제나 DB credential 변경은 하지 않음.
- Validation: `docker compose build` 성공, `docker compose ps -a`로 app 종료와 postgres/redis healthy 상태 확인.
- Remaining risk: 사용자가 Docker volume 초기화 또는 기존 DB credential 사용 방침을 결정한 뒤 앱 헬스체크 재검증 필요.

### Follow-up Resolution

- Decision: 로컬 Docker 개발 검증 중에는 `SPRING_JPA_HIBERNATE_DDL_AUTO=update`를 기본값으로 사용해 Hibernate가 개발 DB 스키마를 자동 생성 또는 갱신한다.
- Fix: `application-local.yml`과 `docker-compose.yml`에 local ddl-auto 기본값 `update`를 설정했다.
- Validation: `docker compose build app`, `docker compose up -d app`, `GET /api/v1/health` 모두 성공. Hibernate가 local Docker DB에 `users` 테이블을 생성했다.
- Remaining risk: 운영/배포 DB migration 전략은 바꾸지 않았고, 최종 Flyway migration consolidation은 별도 후속 작업으로 유지한다.

### 2026-06-18 Follow-up Resolution

- Problem: #29 Docker validation에서 app 컨테이너가 다시 `FATAL: password authentication failed for user "faithlog"`로 종료했다.
- Root cause: app 컨테이너의 `SPRING_DATASOURCE_PASSWORD`는 compose 기본값이었지만, 기존 로컬 Docker volume의 `faithlog` role 비밀번호가 compose 네트워크 접속 기준과 어긋나 있었다. 컨테이너 내부 localhost 접속은 통과했지만, 같은 compose 네트워크에서 `postgres` 호스트로 접속하면 실패했다.
- Fix: Docker volume을 삭제하지 않고 로컬 개발 DB에서 `ALTER USER faithlog WITH PASSWORD 'faithlog';`를 실행한 뒤 app 컨테이너만 `docker compose up -d --force-recreate app`으로 재생성했다.
- Validation: compose 네트워크에서 `select 1` 성공, `faithlog-backend` 기동 성공, host 기준 `GET /api/v1/health` 200 확인.

## 2026-06-17 #27 CI Test Profile Override

- Problem: PR #47 Backend CI `Spring Boot build and test`에서 Spring context 기반 테스트가 실패.
- Symptoms: `FaithLogApplicationTests`, `AuthServiceTest`, `AuthApiRestDocsTest`, `UserMeControllerTest`가 `HibernateException at DialectFactoryImpl`로 실패.
- Root cause: CI job env가 `SPRING_DATASOURCE_URL=jdbc:postgresql://...`로 `application-test.yml`의 H2 datasource를 덮어썼지만, test profile의 `driver-class-name: org.h2.Driver`는 그대로 남아 PostgreSQL URL과 H2 driver가 섞였다. 또한 CI의 `JWT_ACCESS_TOKEN_VALIDITY_SECONDS=3600`은 #27 확정값 1800과 충돌할 수 있었다.
- Fix: CI test job에서 datasource와 token validity env override를 제거해 `application-test.yml`이 H2/create-drop과 #27 확정 token TTL을 일관되게 사용하도록 했다.
- Validation: CI env 재현 실패 확인 후, 수정된 env 조합에서 `./gradlew test --tests '*AuthServiceTest'` 성공, `./gradlew test --rerun-tasks` 성공, `./gradlew build` 성공.
- Remaining risk: CI가 다시 돌기 전까지 GitHub Actions 원격 check 통과는 대기 상태다.

<!-- daily-resume-monitor:start:troubleshooting:2026-06-17 -->
## 2026-06-17 Automated Review

- Problem: `./gradlew asciidoctor` could not complete inside the sandbox.
- Symptoms: Gradle wrapper raised `FileNotFoundException` for `.gradle/wrapper/...zip.lck`.
- Root cause: the wrapper lock path under the user Gradle directory was outside the sandbox write scope.
- Fix: reran the same command with elevated permissions.
- Validation: `./gradlew asciidoctor` succeeded in 3s and `build/docs/asciidoc/index.html` was present afterward.
- Remaining risk: Gradle deprecated feature warnings are still present, and health/latency measurement scope remains a pending decision.
<!-- daily-resume-monitor:end:troubleshooting:2026-06-17 -->
