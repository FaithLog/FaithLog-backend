# FaithLog Project Management

FaithLog 백엔드는 Spring Boot 기반 교회/캠퍼스 운영 앱입니다. 이 폴더는 실제 개발 진행과 포트폴리오 증거를 함께 남기기 위한 프로젝트 관리 기준 문서입니다.

## 목적

- GitHub Issues, Pull Request, GitHub Actions를 기준으로 협업 흐름을 표준화합니다.
- DDD 스타일 모듈러 모놀리스 구조를 유지하면서 도메인별 개발 순서를 관리합니다.
- 이슈, 브랜치, 커밋, PR, 리뷰, QA, 릴리즈 흐름을 일관되게 만듭니다.
- 나중에 이력서/포트폴리오에서 설명할 수 있는 근거를 남깁니다.

## 문서 구조

| File | Purpose |
| --- | --- |
| `kanban-board.md` | 칸반 컬럼, WIP 제한, 이동 규칙 |
| `backlog.md` | Epic별 백로그와 필수 이슈 목록 |
| `epic-map.md` | Epic 목적, 가치, 산출물, 완료 기준 |
| `issue-map.md` | 이슈 작성 형식과 이슈 레지스트리 |
| `dependency-map.md` | 작업 의존성 표와 Mermaid 다이어그램 |
| `action-items.md` | Epic별 실행 체크리스트 |
| `definition-of-ready.md` | Ready 진입 기준 |
| `definition-of-done.md` | Done 완료 기준 |
| `git-flow.md` | 팀 Git-Flow 운영 규칙 |
| `branch-strategy.md` | 브랜치 전략과 네이밍 규칙 |
| `commit-message-rules.md` | 커밋 메시지 규칙 |
| `pull-request-rules.md` | PR 제목/본문/리뷰 규칙 |
| `workflow-rules.md` | 실제 개발 흐름과 예외 처리 |
| `sprint-plan.md` | 마일스톤 기반 스프린트 운영 |
| `milestones.md` | 릴리즈 단위 목표와 완료 기준 |
| `release-plan.md` | 버전별 릴리즈 계획 |
| `risk-register.md` | 리스크와 대응 계획 |
| `decision-log.md` | 기술/제품 의사결정 기록 |
| `portfolio-log.md` | 포트폴리오 증거 기록 양식 |
| `resume-bullets.md` | 이력서 문장 초안 |

## 운영 원칙

- 모든 기능 개발은 이슈 생성 후 시작합니다.
- 작업 브랜치는 최신 `develop`에서 생성합니다.
- PR은 작업 브랜치에서 `develop`으로 보냅니다.
- 오류가 없는 코드만 PR로 보냅니다.
- Docker 환경에서 동작하지 않는 코드는 머지하지 않습니다.
- Secret Key와 `.env` 파일은 절대 커밋하지 않습니다.
- DDD 패키지 경계와 Request DTO -> Command -> Service -> Entity 흐름을 지킵니다.

## 첫 개발 순서 추천

1. `SETUP-001` Spring Boot 프로젝트 초기 설정
2. `SETUP-002` Gradle 의존성 구성
3. `SETUP-003` PostgreSQL Docker Compose 구성
4. `SETUP-004` Redis Docker Compose 구성
5. `ARCH-001` DDD 패키지 구조 생성
