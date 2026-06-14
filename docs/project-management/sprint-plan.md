# Sprint Plan

FaithLog는 Milestone 중심으로 1~2주 단위 스프린트를 운영합니다. 기간은 팀 상황에 따라 조정합니다.

## Sprint 0: Project Management Setup

- 목표: Git-Flow, 칸반, DoR/DoD, 이슈/PR 템플릿 정립.
- 포함 작업: ARCH-005, 프로젝트 관리 문서, GitHub 템플릿.
- 완료 기준: 모든 관리 문서와 템플릿 파일 존재, project-docs-check 통과.

## Sprint 1: Project Foundation

- 목표: Spring Boot 기반 프로젝트 실행 가능 상태.
- 포함 작업: SETUP-001~008, ARCH-001~004, DEPLOY-001~003.
- 완료 기준: 로컬/Docker 실행, Swagger 접근, 공통 응답/예외 설계.

## Sprint 2: Auth and Campus

- 목표: 인증과 캠퍼스 소속 기반 기능.
- 포함 작업: USER-001~003, AUTH-001~007, CAMPUS-001~006.
- 완료 기준: 회원가입/로그인/캠퍼스 생성/가입 시나리오 통과.

## Sprint 3: Devotion and Billing Core

- 목표: 경건생활 제출과 벌금/청구 기본 흐름.
- 포함 작업: DEVOTION-001~010, BILLING-001~011.
- 완료 기준: 경건생활 제출, 벌금 계산, 청구 생성, 상태 전이 테스트 통과.

## Sprint 4: Poll and Coffee Charge

- 목표: 투표 템플릿, 응답, 댓글, 커피 투표 청구 연결.
- 포함 작업: POLL-001~014.
- 완료 기준: 투표 생성/응답/결과/댓글/커피비 청구 연결 테스트 통과.

## Sprint 5: Notification and Automation

- 목표: 알림 발송, Redis 중복 방지, 자동 배치.
- 포함 작업: NOTI-001~010, BATCH-001~007.
- 완료 기준: 미제출/미응답/미납 알림 시나리오 통과.

## Sprint 6: Admin, QA, Docs, Release

- 목표: 관리자 조회, 운영 문서, 포트폴리오 기록, MVP 릴리즈.
- 포함 작업: ADMIN-001~006, QA-001~005, DOCS-001~005, DEPLOY-004~008.
- 완료 기준: v1.0.0 운영 가능한 MVP 기준 충족.
