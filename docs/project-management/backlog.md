# Backlog

## EPIC-00: Project Setup & Architecture

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| SETUP-001 | Spring Boot 프로젝트 초기 설정 | Build | P0 | M | global | - |
| SETUP-002 | Gradle 의존성 구성 | Build | P0 | S | global | SETUP-001 |
| SETUP-003 | PostgreSQL Docker Compose 구성 | Build | P0 | S | global | SETUP-001 |
| SETUP-004 | Redis Docker Compose 구성 | Build | P0 | S | global | SETUP-001 |
| SETUP-005 | application-local/dev/prod.yml 분리 | Build | P0 | M | global | SETUP-003, SETUP-004 |
| SETUP-006 | Swagger/OpenAPI 설정 | Build | P1 | S | global | SETUP-001 |
| SETUP-007 | 공통 응답 ApiResponse 설계 | Chore | P0 | S | global | ARCH-001 |
| SETUP-008 | 공통 예외 BusinessException/ErrorCode 설계 | Chore | P0 | M | global | ARCH-001 |
| ARCH-001 | DDD 패키지 구조 생성 | Chore | P0 | M | global | SETUP-001 |
| ARCH-002 | Request-Command-Result-Response 규칙 문서화 | Docs | P0 | S | global | ARCH-001 |
| ARCH-003 | Repository 인터페이스/JPA 구현체 규칙 문서화 | Docs | P0 | S | global | ARCH-001 |
| ARCH-004 | 도메인 간 ID 참조 규칙 문서화 | Docs | P0 | S | global | ARCH-001 |
| ARCH-005 | Git-Flow 및 PR 규칙 문서화 | Docs | P0 | S | global | - |

## EPIC-01: User & Auth

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| USER-001 | User Entity 설계 | Feat | P0 | M | user | ARCH-001 |
| USER-002 | 회원가입 API | Feat | P0 | M | user | USER-001, SETUP-008 |
| USER-003 | 사용자 조회 API | Feat | P1 | S | user | USER-001, AUTH-001 |
| AUTH-001 | Spring Security 기본 설정 | Feat | P0 | M | user | SETUP-002 |
| AUTH-002 | JWT 발급/검증 | Feat | P0 | M | user | AUTH-001 |
| AUTH-003 | 로그인 API | Feat | P0 | M | user | USER-001, AUTH-002 |
| AUTH-004 | Refresh Token Redis 저장 | Feat | P0 | M | user | SETUP-004, AUTH-003 |
| AUTH-005 | Access Token 로그아웃 블랙리스트 Redis 저장 | Feat | P1 | M | user | SETUP-004, AUTH-002 |
| AUTH-006 | 토큰 재발급 API | Feat | P1 | M | user | AUTH-004 |
| AUTH-007 | 인증 실패/권한 실패 예외 처리 | Feat | P1 | S | user | AUTH-001, SETUP-008 |

## EPIC-02: Campus & Member

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| CAMPUS-001 | Campus Entity 설계 | Feat | P0 | M | campus | ARCH-001 |
| CAMPUS-002 | CampusMember Entity 설계 | Feat | P0 | M | campus | CAMPUS-001, USER-001 |
| CAMPUS-003 | 캠퍼스 생성 API | Feat | P0 | M | campus | CAMPUS-001, AUTH-003 |
| CAMPUS-004 | 초대코드 기반 캠퍼스 가입 API | Feat | P0 | M | campus | CAMPUS-002, USER-001 |
| CAMPUS-005 | 캠퍼스 멤버 목록 조회 API | Feat | P1 | S | campus | CAMPUS-002 |
| CAMPUS-006 | 캠퍼스 역할 변경 API | Feat | P1 | M | campus | CAMPUS-002 |
| CAMPUS-007 | CampusDutyAssignment Entity 설계 | Feat | P2 | S | campus | CAMPUS-002 |
| CAMPUS-008 | 커피 담당자 지정/해제 API | Feat | P2 | M | campus | CAMPUS-007 |

## EPIC-03: Devotion Check

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| DEVOTION-001 | WeeklyDevotionRecord Entity 설계 | Feat | P0 | M | devotion | CAMPUS-002 |
| DEVOTION-002 | DevotionDailyCheck Entity 설계 | Feat | P0 | M | devotion | DEVOTION-001 |
| DEVOTION-003 | 경건생활 주간 제출 API | Feat | P0 | L | devotion | DEVOTION-002, USER-001 |
| DEVOTION-004 | 일별 체크 수정 API | Feat | P1 | M | devotion | DEVOTION-003 |
| DEVOTION-005 | 캠퍼스별 주간 경건생활 조회 API | Feat | P1 | M | devotion | DEVOTION-003 |
| DEVOTION-006 | 미제출자 조회 API | Feat | P1 | M | devotion | DEVOTION-003 |
| DEVOTION-007 | PenaltyRule Entity 설계 | Feat | P1 | M | devotion | CAMPUS-001 |
| DEVOTION-008 | 벌금 규칙 관리 API | Feat | P1 | M | devotion | DEVOTION-007 |
| DEVOTION-009 | 경건생활 벌금 계산 로직 | Feat | P0 | L | devotion | DEVOTION-003, DEVOTION-007 |
| DEVOTION-010 | 경건생활 기반 청구 생성 연결 | Feat | P1 | L | devotion,billing | DEVOTION-009, BILLING-002 |

## EPIC-04: Billing & Payment

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| BILLING-001 | PaymentAccount Entity 설계 | Feat | P0 | M | billing | CAMPUS-001 |
| BILLING-002 | ChargeItem Entity 설계 | Feat | P0 | M | billing | BILLING-001, USER-001 |
| BILLING-003 | 계좌 등록/비활성화 API | Feat | P1 | M | billing | BILLING-001 |
| BILLING-004 | 청구 항목 생성 API | Feat | P0 | M | billing | BILLING-002 |
| BILLING-005 | 사용자별 청구 목록 조회 API | Feat | P1 | S | billing | BILLING-002, USER-001 |
| BILLING-006 | 캠퍼스별 청구 현황 조회 API | Feat | P1 | M | billing | BILLING-002, CAMPUS-001 |
| BILLING-007 | 납부 요청 처리 API | Feat | P1 | M | billing | BILLING-002 |
| BILLING-008 | 납부 완료 확인 API | Feat | P1 | M | billing | BILLING-007 |
| BILLING-009 | 청구 면제 처리 API | Feat | P2 | S | billing | BILLING-002 |
| BILLING-010 | 청구 취소 처리 API | Feat | P2 | S | billing | BILLING-002 |
| BILLING-011 | 청구 상태 전이 규칙 테스트 | Test | P1 | M | billing | BILLING-007, BILLING-008, BILLING-009, BILLING-010 |

## EPIC-05: Poll & Poll Template

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| POLL-001 | PollTemplate Entity 설계 | Feat | P1 | M | poll | CAMPUS-001 |
| POLL-002 | PollTemplateOption Entity 설계 | Feat | P1 | S | poll | POLL-001 |
| POLL-003 | 투표 템플릿 생성 API | Feat | P1 | M | poll | POLL-002 |
| POLL-004 | 템플릿 기반 투표 생성 API | Feat | P1 | M | poll | POLL-003, POLL-005 |
| POLL-005 | Poll Entity 설계 | Feat | P1 | M | poll | CAMPUS-001 |
| POLL-006 | PollOption Entity 설계 | Feat | P1 | S | poll | POLL-005 |
| POLL-007 | PollResponse Entity 설계 | Feat | P1 | M | poll | POLL-006, USER-001 |
| POLL-008 | 투표 응답 API | Feat | P1 | M | poll | POLL-007, USER-001 |
| POLL-009 | 중복 응답 방지 | Feat | P1 | S | poll | POLL-008 |
| POLL-010 | 투표 결과 조회 API | Feat | P1 | M | poll | POLL-008 |
| POLL-011 | PollComment Entity 설계 | Feat | P2 | S | poll | POLL-005 |
| POLL-012 | 투표 댓글 작성/삭제 API | Feat | P2 | M | poll | POLL-011 |
| POLL-013 | 커피 투표 옵션 가격 기반 청구 생성 연결 | Feat | P1 | L | poll,billing | POLL-008, BILLING-002 |
| POLL-014 | 투표 상태 전이 SCHEDULED/OPEN/CLOSED 처리 | Feat | P1 | M | poll | POLL-005 |

## EPIC-06: Notification & FCM

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| NOTI-001 | Firebase Admin SDK 설정 | Build | P1 | M | notification | SETUP-002 |
| NOTI-002 | UserFcmToken Entity 설계 | Feat | P1 | M | notification | USER-001 |
| NOTI-003 | FCM 토큰 등록/비활성화 API | Feat | P1 | M | notification | NOTI-002, NOTI-001 |
| NOTI-004 | NotificationLog Entity 설계 | Feat | P1 | M | notification | USER-001 |
| NOTI-005 | 단건 알림 발송 | Feat | P1 | M | notification | NOTI-001, NOTI-002, NOTI-004 |
| NOTI-006 | 캠퍼스 멤버 대상 알림 발송 | Feat | P2 | M | notification | NOTI-005, CAMPUS-002 |
| NOTI-007 | FCM 실패 토큰 비활성화 처리 | Feat | P2 | M | notification | NOTI-003, NOTI-005 |
| NOTI-008 | Redis 알림 중복 발송 방지 키 설계 | Feat | P1 | M | notification | SETUP-004 |
| NOTI-009 | Redis 알림 락 처리 | Feat | P1 | M | notification | SETUP-004 |
| NOTI-010 | 알림 발송 로그 저장 | Feat | P1 | S | notification | NOTI-004, NOTI-005 |

## EPIC-07: Admin Dashboard API

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| ADMIN-001 | 관리자 권한 정책 정리 | Docs | P1 | S | global | AUTH-001, CAMPUS-002 |
| ADMIN-002 | 캠퍼스별 경건생활 집계 API | Feat | P2 | M | devotion | ADMIN-001, DEVOTION-005 |
| ADMIN-003 | 캠퍼스별 벌금 집계 API | Feat | P2 | M | billing | ADMIN-001, BILLING-006 |
| ADMIN-004 | 캠퍼스별 투표 현황 API | Feat | P2 | M | poll | ADMIN-001, POLL-010 |
| ADMIN-005 | 캠퍼스별 미납자 조회 API | Feat | P2 | M | billing | ADMIN-001, BILLING-006 |
| ADMIN-006 | 관리자 API 권한 검증 테스트 | Test | P1 | M | global | ADMIN-002, ADMIN-003, ADMIN-004, ADMIN-005 |

## EPIC-08: Batch / Scheduler

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| BATCH-001 | 스케줄러 기본 설정 | Build | P2 | S | global | SETUP-001 |
| BATCH-002 | 주간 경건생활 미제출자 탐색 배치 | Feat | P2 | M | devotion | BATCH-001, DEVOTION-006 |
| BATCH-003 | 경건생활 벌금 자동 생성 배치 | Feat | P2 | L | devotion,billing | BATCH-001, DEVOTION-010 |
| BATCH-004 | 투표 오픈/마감 상태 변경 배치 | Feat | P2 | M | poll | BATCH-001, POLL-014 |
| BATCH-005 | 투표 미응답자 알림 배치 | Feat | P2 | M | poll,notification | BATCH-007, POLL-010, NOTI-005 |
| BATCH-006 | 미납자 알림 배치 | Feat | P2 | M | billing,notification | BATCH-007, BILLING-006, NOTI-005 |
| BATCH-007 | 배치 중복 실행 방지 Redis Lock 적용 | Feat | P1 | M | global | SETUP-004 |

## EPIC-09: Observability & Deployment

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| DEPLOY-001 | Dockerfile 작성 | Build | P0 | S | infra/docker | SETUP-001 |
| DEPLOY-002 | docker-compose.yml 구성 | Build | P0 | S | infra/docker | SETUP-003, SETUP-004 |
| DEPLOY-003 | PostgreSQL/Redis 환경 변수 정리 | Build | P0 | S | infra/docker | DEPLOY-002 |
| DEPLOY-004 | 운영용 application-prod.yml 정리 | Build | P2 | M | global | SETUP-005 |
| DEPLOY-005 | Health Check API | Feat | P1 | S | global | SETUP-001 |
| DEPLOY-006 | 기본 로그 정책 정리 | Chore | P2 | S | global | SETUP-001 |
| DEPLOY-007 | 배포 README 작성 | Docs | P2 | S | infra/docker | DEPLOY-002 |
| DEPLOY-008 | GitHub Actions CI 기본 구성 | Build | P1 | M | infra/docker | SETUP-002 |

## EPIC-10: QA, Docs & Portfolio

| ID | Title | Type | Priority | Estimate | Domain | Depends On |
| --- | --- | --- | --- | --- | --- | --- |
| QA-001 | 핵심 API 테스트 시나리오 작성 | Test | P1 | M | global | AUTH-003, CAMPUS-003 |
| QA-002 | 인증/인가 테스트 시나리오 작성 | Test | P1 | M | user | AUTH-007 |
| QA-003 | 경건생활 벌금 계산 테스트 시나리오 작성 | Test | P1 | M | devotion | DEVOTION-009 |
| QA-004 | 투표-청구 연결 테스트 시나리오 작성 | Test | P1 | M | poll,billing | POLL-013 |
| QA-005 | 알림 중복 방지 테스트 시나리오 작성 | Test | P1 | M | notification | NOTI-008, NOTI-009 |
| DOCS-001 | API 명세 README 작성 | Docs | P2 | M | global | SETUP-006 |
| DOCS-002 | ERD 설명 문서 작성 | Docs | P2 | M | global | ARCH-001 |
| DOCS-003 | DDD 패키지 구조 설명 문서 작성 | Docs | P1 | S | global | ARCH-001 |
| DOCS-004 | Redis 사용 목적 문서 작성 | Docs | P1 | S | global | SETUP-004 |
| DOCS-005 | 포트폴리오용 프로젝트 회고 작성 | Docs | P2 | M | global | Milestone 6 |
