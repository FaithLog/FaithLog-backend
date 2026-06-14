# Epic Map

## EPIC-00: Project Setup & Architecture

- Epic 목적: Spring Boot 백엔드 기반, Docker, CI, DDD 패키지 구조, 공통 응답/예외를 준비합니다.
- 사용자 가치: 이후 기능 개발을 안정적으로 시작할 수 있습니다.
- 기술적 가치: 모든 도메인 구현의 공통 기반을 고정합니다.
- 선행 조건: GitHub 저장소, 기본 `.gitignore`, Docker 방향 합의.
- 주요 산출물: Gradle 프로젝트, Docker Compose, Swagger, ApiResponse, BusinessException, 패키지 구조.
- 완료 기준: 로컬/Docker 실행 가능, Swagger 접근 가능, CI 통과.
- 포함 이슈 목록: SETUP-001~008, ARCH-001~005.
- 후속 연결 작업: 모든 도메인 구현.
- Git 브랜치 전략: `build/*`, `chore/*`, `docs/*`.
- 포트폴리오 포인트: 모듈러 모놀리스 기반 구조와 Git-Flow 협업 프로세스 정립.

## EPIC-01: User & Auth

- Epic 목적: 회원, 로그인, JWT, Refresh Token, 로그아웃 블랙리스트를 구현합니다.
- 사용자 가치: 인증된 사용자만 캠퍼스/경건생활/투표/청구 기능을 사용할 수 있습니다.
- 기술적 가치: Spring Security + JWT + Redis 인증 기반을 만듭니다.
- 선행 조건: SETUP-004, ARCH-001, USER-001.
- 주요 산출물: User Entity, Auth API, JWT Provider, Redis token repository.
- 완료 기준: 회원가입/로그인/재발급/로그아웃 시나리오 통과.
- 포함 이슈 목록: USER-001~003, AUTH-001~007.
- 후속 연결 작업: Campus 가입, 모든 인증 API.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: Redis 기반 Refresh Token과 Access Token blacklist 설계.

## EPIC-02: Campus & Member

- Epic 목적: 캠퍼스 생성, 초대코드 가입, 멤버/역할, 담당자 배정을 구현합니다.
- 사용자 가치: 캠퍼스 단위로 운영 데이터를 분리할 수 있습니다.
- 기술적 가치: 이후 devotion, poll, billing, notification의 campusId 기준을 제공합니다.
- 선행 조건: USER-001, AUTH-003, ARCH-001.
- 주요 산출물: Campus, CampusMember, DutyAssignment, 초대코드 정책.
- 완료 기준: 캠퍼스 생성/가입/권한 변경/담당자 지정 시나리오 통과.
- 포함 이슈 목록: CAMPUS-001~008.
- 후속 연결 작업: 경건생활, 투표, 청구, 알림.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: 도메인 간 직접 Entity 참조 대신 ID 참조를 활용한 캠퍼스 경계 설계.

## EPIC-03: Devotion Check

- Epic 목적: 주간 경건생활 기록, 일별 체크, 지각, 벌금 규칙과 계산을 구현합니다.
- 사용자 가치: 캠퍼스 구성원의 경건생활 제출 상태와 벌금을 관리할 수 있습니다.
- 기술적 가치: 복잡한 규칙을 Domain Service로 분리하는 사례를 만듭니다.
- 선행 조건: CAMPUS-001, CAMPUS-002, BILLING-002.
- 주요 산출물: WeeklyDevotionRecord, DailyCheck, PenaltyRule, DevotionFineCalculator.
- 완료 기준: 제출/수정/조회/벌금 계산/청구 연결 테스트 통과.
- 포함 이슈 목록: DEVOTION-001~010.
- 후속 연결 작업: Billing, Notification, Batch.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: 경건생활 벌금 계산 Domain Service 분리.

## EPIC-04: Billing & Payment

- Epic 목적: 계좌, 청구 항목, 납부 요청, 납부 완료, 면제, 취소를 구현합니다.
- 사용자 가치: 벌금/커피비를 캠퍼스 단위로 추적하고 납부 상태를 관리할 수 있습니다.
- 기술적 가치: 청구 상태 전이와 계좌 스냅샷을 안정적으로 관리합니다.
- 선행 조건: CAMPUS-001, USER-001.
- 주요 산출물: PaymentAccount, ChargeItem, BillingStatus transition.
- 완료 기준: 생성/조회/납부/면제/취소 상태 전이 테스트 통과.
- 포함 이슈 목록: BILLING-001~011.
- 후속 연결 작업: Devotion 청구 생성, Poll 커피비 청구 생성, 미납자 알림.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: 결제 승인 흐름 없이 직접 PAID 상태를 관리하는 MVP 청구 모델.

## EPIC-05: Poll & Poll Template

- Epic 목적: 템플릿 기반 투표, 옵션, 응답, 댓글, 커피 투표 청구 연결을 구현합니다.
- 사용자 가치: 수요예배/리더모임/커피/커스텀 투표를 운영할 수 있습니다.
- 기술적 가치: 템플릿과 실제 투표 데이터 분리, Poll-Billing 연동 규칙을 설계합니다.
- 선행 조건: CAMPUS-001, BILLING-001, BILLING-002.
- 주요 산출물: PollTemplate, Poll, PollOption, PollResponse, PollComment.
- 완료 기준: 템플릿 생성, 투표 생성, 중복 응답 방지, 결과 조회, 청구 연결 테스트 통과.
- 포함 이슈 목록: POLL-001~014.
- 후속 연결 작업: Notification, Batch.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: Poll 응답과 Billing 청구 연결 설계.

## EPIC-06: Notification & FCM

- Epic 목적: FCM 토큰, 알림 발송, 알림 로그, Redis 중복 방지와 락을 구현합니다.
- 사용자 가치: 미제출자/미응답자/미납자에게 알림을 보낼 수 있습니다.
- 기술적 가치: Redis TTL과 Lock을 활용한 중복 발송 방지 구조를 만듭니다.
- 선행 조건: SETUP-004, NOTI-002, NOTI-004.
- 주요 산출물: UserFcmToken, NotificationLog, Redis dedup key, lock repository.
- 완료 기준: 단건/캠퍼스 알림, 실패 토큰 처리, 중복 방지 테스트 통과.
- 포함 이슈 목록: NOTI-001~010.
- 후속 연결 작업: Batch 자동 알림.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: Redis 기반 알림 중복 방지와 FCM 실패 토큰 처리.

## EPIC-07: Admin Dashboard API

- Epic 목적: 캠퍼스별 운영 집계와 관리자 조회 API를 구현합니다.
- 사용자 가치: 리더/관리자가 경건생활, 벌금, 투표, 미납 현황을 확인할 수 있습니다.
- 기술적 가치: 관리자 기능을 별도 admin 도메인 없이 각 도메인의 AdminController로 배치합니다.
- 선행 조건: Campus, Devotion, Billing, Poll 기본 기능.
- 주요 산출물: 도메인별 AdminController, 집계 Result/Response.
- 완료 기준: 관리자 권한 검증과 캠퍼스별 집계 테스트 통과.
- 포함 이슈 목록: ADMIN-001~006.
- 후속 연결 작업: 운영 대시보드, 포트폴리오 문서.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: 분산된 관리자 API를 도메인 경계 안에서 관리.

## EPIC-08: Batch / Scheduler

- Epic 목적: 미제출/미응답/미납 자동 탐색과 알림 배치를 구현합니다.
- 사용자 가치: 반복 운영 업무를 자동화합니다.
- 기술적 가치: Redis Lock으로 중복 배치 실행을 방지합니다.
- 선행 조건: Notification, Devotion, Poll, Billing 기본 기능.
- 주요 산출물: Scheduler, batch service, Redis lock.
- 완료 기준: 배치 중복 실행 방지와 알림 발송 시나리오 통과.
- 포함 이슈 목록: BATCH-001~007.
- 후속 연결 작업: 운영 모니터링.
- Git 브랜치 전략: `feat/*`, `test/*`.
- 포트폴리오 포인트: 스케줄러와 Redis Lock 기반 자동화.

## EPIC-09: Observability & Deployment

- Epic 목적: Docker, 환경 변수, health check, 로그, CI, 배포 문서를 정리합니다.
- 사용자 가치: 개발/운영 환경에서 재현 가능한 실행 방법을 제공합니다.
- 기술적 가치: Docker 기반 검증과 CI를 통해 PR 품질을 관리합니다.
- 선행 조건: 프로젝트 기본 구조.
- 주요 산출물: Dockerfile, docker-compose, Health API, 로그 정책, CI.
- 완료 기준: Docker 실행, CI 통과, 배포 문서 작성.
- 포함 이슈 목록: DEPLOY-001~008.
- 후속 연결 작업: 릴리즈.
- Git 브랜치 전략: `build/*`, `chore/*`, `docs/*`.
- 포트폴리오 포인트: Docker와 GitHub Actions 기반 백엔드 운영 준비.

## EPIC-10: QA, Docs & Portfolio

- Epic 목적: 테스트 시나리오, API 문서, ERD 설명, 회고와 이력서 자료를 정리합니다.
- 사용자 가치: 팀원과 리뷰어가 기능 동작과 설계를 이해할 수 있습니다.
- 기술적 가치: 개발 산출물을 포트폴리오 증거로 전환합니다.
- 선행 조건: 각 기능 MVP.
- 주요 산출물: 테스트 시나리오, README, ERD 문서, Redis 문서, 포트폴리오 회고.
- 완료 기준: 핵심 플로우 테스트와 문서 업데이트 완료.
- 포함 이슈 목록: QA-001~005, DOCS-001~005.
- 후속 연결 작업: 이력서/면접 설명.
- Git 브랜치 전략: `test/*`, `docs/*`.
- 포트폴리오 포인트: 협업 프로세스와 아키텍처 의사결정 근거.
