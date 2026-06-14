# Portfolio Log

개발하면서 포트폴리오 증거를 남기기 위한 기록 양식입니다. 중요한 PR, 설계 결정, 테스트 결과, 운영 문제 해결은 여기에 기록합니다.

## Template

```text
Date:
Issue:
Branch:
Pull Request:
Problem:
Decision:
Implementation:
Result:
Evidence:
What I learned:
Resume Keyword:
```

## Example 1: Git-Flow 기반 이슈-브랜치-PR 프로세스 정립

Date: TBD  
Issue: ARCH-005  
Branch: docs/{issue-number}-git-flow-rules  
Pull Request: TBD  
Problem: 기능 단위 작업이 섞이면 진행 상황과 리뷰 책임이 불명확해질 수 있다.  
Decision: GitHub Issue, Git-Flow, PR 템플릿, DoR/DoD, 칸반 보드 기준을 먼저 정립한다.  
Implementation: 이슈 제목/브랜치/커밋/PR 규칙과 칸반 이동 조건을 문서화한다.  
Result: 작업 추적성과 코드 리뷰 흐름을 개선할 기반을 마련한다.  
Evidence: `docs/project-management`, `.github/pull_request_template.md`  
What I learned: 기능 구현 전 협업 규칙을 고정하면 리뷰와 일정 관리가 쉬워진다.  
Resume Keyword: Git-Flow, GitHub Issues, Kanban, PR Review

## Example 2: Redis Refresh Token 관리

Date: TBD  
Issue: AUTH-004  
Branch: feat/{issue-number}-refresh-token-redis  
Pull Request: TBD  
Problem: Access Token 만료 후 재로그인 없이 인증을 유지해야 한다.  
Decision: Refresh Token을 Redis에 TTL 기반으로 저장한다.  
Implementation: `user/infrastructure/redis`에 Refresh Token repository를 두고 RedisConfig는 `global/config`에 둔다.  
Result: 토큰 재발급과 로그아웃 시나리오를 명확하게 검증할 수 있다.  
Evidence: Redis key spec, Auth API 테스트  
What I learned: Redis는 만료 시간이 중요한 인증 상태 관리에 적합하다.  
Resume Keyword: Redis TTL, JWT, Refresh Token

## Example 3: Poll 응답과 Billing 청구 연결

Date: TBD  
Issue: POLL-013  
Branch: feat/{issue-number}-coffee-poll-billing  
Pull Request: TBD  
Problem: 커피 투표 결과를 바탕으로 사용자별 커피비 청구를 생성해야 한다.  
Decision: Poll이 Billing Entity를 직접 참조하지 않고 Billing command로 청구 생성을 요청한다.  
Implementation: poll 결과에서 `userId`, `campusId`, `sourceType`, `sourceId`, `amount`를 만들어 billing application으로 전달한다.  
Result: 투표와 청구의 결합도를 낮추면서 연동을 구현한다.  
Evidence: Poll-Billing integration test  
What I learned: 도메인 간 연결은 ID와 command 중심으로 설계하면 변경 전파를 줄일 수 있다.  
Resume Keyword: Modular Monolith, Domain Boundary, Billing

## Example 4: Devotion 벌금 계산 Domain Service 분리

Date: TBD  
Issue: DEVOTION-009  
Branch: feat/{issue-number}-devotion-fine-calculator  
Pull Request: TBD  
Problem: 경건생활 체크, 지각, 벌금 규칙이 섞이면 Service가 비대해진다.  
Decision: 벌금 계산을 DevotionFineCalculator Domain Service로 분리한다.  
Implementation: WeeklyDevotionRecord와 PenaltyRule을 입력받아 DevotionFineResult를 생성한다.  
Result: 계산 규칙을 테스트하기 쉬워지고 Billing 연결 전 단계가 명확해진다.  
Evidence: fine calculation unit test  
What I learned: 복잡한 도메인 규칙은 Entity와 Domain Service로 분리하면 유지보수성이 좋아진다.  
Resume Keyword: Domain Service, DDD, Business Rule

## Record: #16 프로젝트 기반 구축 및 아키텍처

Date: 2026-06-14  
Issue: #16 [Chore] 프로젝트 기반 구축 및 아키텍처  
Branch: chore/16-project-setup-architecture  
Pull Request: TBD  
Problem: 기능 개발을 시작하기 전에 Spring Boot 실행 골격, Gradle Wrapper, Docker Compose, profile 설정, DDD 패키지 경계, 공통 응답/예외, GitHub 협업 규칙이 필요했다.  
Decision: 하나의 Spring Boot 애플리케이션 안에서 DDD 스타일 모듈러 모놀리스 구조를 유지하고, GitHub Issue 기반 칸반과 Git-Flow 규칙을 함께 정리했다.  
Implementation: Java 21, Spring Boot 3.5, Gradle Wrapper, PostgreSQL/Redis Docker Compose, `application-local/dev/prod.yml`, `ApiResponse`, `BusinessException`, `ErrorCode`, `RedisConfig`, Swagger/OpenAPI, `/api/v1/health`, 도메인별 패키지 구조, GitHub Issue/PR 템플릿, 자동 Draft PR workflow를 추가했다.  
Result: `./gradlew test`와 `./gradlew build`가 통과했고, `docker compose config`로 compose 설정을 검증했다. Docker daemon이 500 오류를 반환해 실제 `docker compose up` 검증은 추후 Docker Desktop 정상화 후 진행한다.  
Evidence: commit `66fca0d`, commit `c27b9b6`, commit `f0e5989`, `./gradlew build`, `docker compose config`  
What I learned: 기반 구축 이슈는 코드, 인프라, 협업 규칙을 한 PR에 담되 커밋 단위를 분리하면 리뷰와 추적이 쉬워진다.  
Resume Keyword: Spring Boot, Modular Monolith, Docker Compose, GitHub Actions, Git-Flow
