# Milestones

## Milestone 1: 프로젝트 기반 구축

- 목표: 개발 가능한 Spring Boot 기반과 협업 규칙 준비.
- 예상 기간: 1~2주
- 포함 Epic: EPIC-00, EPIC-09 일부, EPIC-10 일부
- 포함 Issue: SETUP-001~008, ARCH-001~005, DEPLOY-001~003, DEPLOY-008
- 산출물: Gradle, Docker Compose, Swagger, 공통 응답/예외, 패키지 구조, Git-Flow 문서
- 위험 요소: 구조 설계 과잉, Docker 검증 누락
- 완료 기준: 로컬/Docker 실행, CI 통과, PR/Issue 템플릿 준비
- 포트폴리오 기록 포인트: 협업 프로세스와 모듈러 모놀리스 기반 구축

## Milestone 2: 인증과 캠퍼스

- 목표: 인증 사용자와 캠퍼스 멤버십 구현.
- 예상 기간: 2주
- 포함 Epic: EPIC-01, EPIC-02
- 포함 Issue: USER-001~003, AUTH-001~007, CAMPUS-001~008
- 산출물: 회원가입, 로그인, Refresh Token, 로그아웃, 캠퍼스 생성/가입/권한
- 위험 요소: JWT/Redis 보안, 캠퍼스 권한 누락
- 완료 기준: 인증/캠퍼스 핵심 API 테스트 통과
- 포트폴리오 기록 포인트: Spring Security + JWT + Redis 인증 구조

## Milestone 3: 경건생활과 벌금

- 목표: 경건생활 제출과 벌금 계산/청구 연결.
- 예상 기간: 2주
- 포함 Epic: EPIC-03, EPIC-04 일부
- 포함 Issue: DEVOTION-001~010, BILLING-001~011
- 산출물: 경건생활 제출, 일별 체크, 벌금 규칙, 벌금 계산, 청구 생성
- 위험 요소: 벌금 규칙 변경과 기존 청구 충돌
- 완료 기준: 벌금 계산과 청구 상태 전이 테스트 통과
- 포트폴리오 기록 포인트: Domain Service 기반 벌금 계산 분리

## Milestone 4: 투표와 커피비

- 목표: 투표와 커피비 청구 연결.
- 예상 기간: 2주
- 포함 Epic: EPIC-05
- 포함 Issue: POLL-001~014
- 산출물: 투표 템플릿, 투표 생성, 응답, 댓글, 커피 투표, 청구 연결
- 위험 요소: Poll과 Billing 결합도 증가
- 완료 기준: 투표 응답과 청구 생성 연동 테스트 통과
- 포트폴리오 기록 포인트: Poll-Billing 경계와 ID 참조 기반 연동

## Milestone 5: 알림과 자동화

- 목표: FCM 알림과 Redis 기반 중복 방지/락.
- 예상 기간: 2주
- 포함 Epic: EPIC-06, EPIC-08
- 포함 Issue: NOTI-001~010, BATCH-001~007
- 산출물: FCM 토큰 등록, 알림 발송, 미제출/미응답/미납 알림, Redis Lock
- 위험 요소: 실패 토큰 처리 누락, Redis TTL 누락
- 완료 기준: 중복 방지와 배치 알림 시나리오 통과
- 포트폴리오 기록 포인트: Redis TTL/Lock 기반 알림 자동화

## Milestone 6: 관리자/운영/배포

- 목표: 운영 가능한 MVP와 포트폴리오 산출물 완성.
- 예상 기간: 1~2주
- 포함 Epic: EPIC-07, EPIC-09, EPIC-10
- 포함 Issue: ADMIN-001~006, DEPLOY-004~008, QA-001~005, DOCS-001~005
- 산출물: 관리자 조회 API, 캠퍼스별 집계, 납부 현황, 배포 문서, 회고
- 위험 요소: 후반 문서화 누락, QA 시나리오 부족
- 완료 기준: 운영 가능한 MVP 릴리즈 체크리스트 통과
- 포트폴리오 기록 포인트: 전체 개발 프로세스와 운영 준비 경험
