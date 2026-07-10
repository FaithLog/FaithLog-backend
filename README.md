# FaithLog Backend

FaithLog 교회/캠퍼스 운영 앱의 Spring Boot 백엔드입니다.

## Tech Stack

- Java 21
- Spring Boot
- Spring Security + JWT
- Spring Data JPA
- PostgreSQL
- Redis
- Firebase FCM
- Gradle
- Docker Compose
- Swagger/springdoc for lightweight API exploration
- Spring REST Docs for detailed API contract documentation

## Local Infrastructure

```bash
docker compose up -d postgres redis
```

Spring Boot 프로젝트가 추가된 뒤에는 애플리케이션 이미지까지 함께 실행할 수 있습니다.

```bash
./gradlew build
docker compose up --build
```

## Isolated Docker QA

전체 QA 또는 Docker QA는 기존 개발/PM worktree의 named volume을 피하기 위해 QA 전용 compose project name으로 실행한다.

```bash
QA_COMPOSE_PROJECT=faithlog-qa-84 ./scripts/qa_docker_compose_isolated.sh
```

`QA_COMPOSE_PROJECT`를 생략하면 스크립트가 `faithlog-qa-84-<timestamp>-<random>` 형식의 project name을 자동 생성한다. 종료 시에는 같은 project name에 대해서만 `docker compose -p <projectName> down`을 실행하며 Docker volume은 삭제하지 않는다.

## API Documentation

Swagger/springdoc은 간단한 API 탐색과 확인용으로 유지한다. 상세 request/response 계약은 Spring REST Docs 테스트가 생성하는 snippets와 Asciidoc 문서를 기준으로 관리한다.

```bash
./gradlew test
./gradlew asciidoctor
```

- REST Docs snippets: `build/generated-snippets`
- Rendered API docs: `build/docs/asciidoc/index.html`

## Deployment

Flyway migration, Supabase PostgreSQL, Upstash Redis, and Google Cloud Run deployment contracts are documented in [Cloud Run, Supabase, And Upstash Deployment](docs/deploy/cloud-run-supabase.md).

## Architecture

FaithLog는 MSA가 아닌 하나의 Spring Boot 애플리케이션 안에서 도메인 경계를 나누는 모듈러 모놀리스 구조를 사용합니다.

- `admin`, `batch`, `billing`, `campus`, `devotion`, `notification`, `poll`, `prayer`, `user`를 최상위 도메인 경계로 분리
- 각 도메인 내부는 사용하는 책임만 `controller`, `service`, `domain`, `infrastructure` 하위에 배치
- Request/Response DTO는 `controller/dto/request`, `controller/dto/response`로 분리
- Service 입력과 조회 조건, 반환 모델은 `service/command`, `service/query`, `service/result`로 분리
- 정책과 의존 역전 경계는 `service/policy`, `service/port`에 배치
- Entity와 enum/value type은 `domain/entity`, `domain/type`으로 분리
- JPA Repository는 `infrastructure/repository`, 외부 연동 구현은 `infrastructure/adapter`, `redis`, `fcm` 등 실제 책임 이름으로 배치
- 사용하지 않는 빈 하위 패키지는 만들지 않음
- Controller에서 Entity 직접 반환 금지
- PostgreSQL은 Spring Boot auto-configuration 사용
- Redis 설정은 `global/config/RedisConfig.java`, 실제 Redis 구현체는 각 도메인의 `infrastructure/redis`에 배치

```text
com.faithlog.{domain}
├── controller
│   └── dto/{request,response}
├── service
│   ├── command
│   ├── query
│   ├── result
│   ├── policy
│   └── port
├── domain/{entity,type}
└── infrastructure/{repository,adapter,redis,fcm,seed}
```

`global`은 도메인 구조로 강제하지 않고 공통 설정, 보안, 예외, 응답, 공통 Controller 책임을 유지합니다.

## Codex 개발 규칙

FaithLog 백엔드 개발은 단일 Agent 규칙과 Codex Hook 기준을 따른다.

- [FaithLog Agent Rules](AGENTS.md)
- [FaithLog Codex Hook](docs/codex/FAITHLOG_CODEX_HOOK.md)
- [Daily Resume Monitor Prompt](docs/prompts/daily-resume-monitor.md)

핵심 원칙:

- TDD 방식으로 개발한다.
- Notion 최종 기획/ERD/API 기준을 우선한다.
- 경건생활 제출 시 벌금 청구는 자동 생성된다.
- 커피 투표 응답 API는 응답 저장만 수행하고, COFFEE 청구는 CLOSED 커피 투표 정산 서비스에서 최종 응답 기준으로 생성/갱신한다.
- 기능 개발 후 Obsidian 개발 로그를 작성한다.
- 작업은 GitHub Issue와 GitHub Projects 칸반보드 기준으로 관리한다.

## Daily Resume Monitor

전날 작업을 검증 가능한 증거 기준으로 정리하려면 아래 명령을 수동 실행한다.

```bash
python3 scripts/daily_resume_monitor.py
```

이 스크립트는 실행할 때마다 `docs/prompts/daily-resume-monitor.md`를 읽고, 프로젝트 문서와 승인된 Obsidian FaithLog 경로에 Markdown 노트를 생성 또는 갱신한다. 스케줄링은 아직 설정하지 않았다.
