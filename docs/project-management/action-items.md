# Action Items

## EPIC-00: Project Setup & Architecture

- [ ] 이슈 생성
- [ ] 최신 `develop` pull
- [ ] 이슈 번호 기반 브랜치 생성
- [ ] Spring Boot 프로젝트 생성
- [ ] Gradle 의존성 작성
- [ ] PostgreSQL Docker Compose 연결 확인
- [ ] Redis Docker Compose 연결 확인
- [ ] Swagger 설정
- [ ] ApiResponse 설계
- [ ] BusinessException/ErrorCode 설계
- [ ] DDD 패키지 구조 생성
- [ ] 작업 단위 커밋
- [ ] 커밋 메시지에 이슈 번호 연결
- [ ] Docker 실행 확인
- [ ] `.env`/Secret 노출 확인
- [ ] `develop`으로 PR 생성
- [ ] 코드 리뷰 반영
- [ ] 문서 업데이트
- [ ] 포트폴리오 증거 기록

## EPIC-01: User & Auth

- [ ] 이슈 생성
- [ ] 최신 `develop` pull
- [ ] 이슈 번호 기반 브랜치 생성
- [ ] User Entity 작성
- [ ] PasswordEncoder 적용
- [ ] JWT Provider 작성
- [ ] Refresh Token Redis Key 설계
- [ ] Logout Blacklist Redis Key 설계
- [ ] 인증 실패 예외 정의
- [ ] 회원가입 API Swagger 확인
- [ ] 로그인 API Swagger 확인
- [ ] 토큰 재발급 시나리오 테스트
- [ ] 작업 단위 커밋
- [ ] 커밋 메시지에 이슈 번호 연결
- [ ] 로컬 실행 확인
- [ ] Docker 실행 확인
- [ ] `.env`/Secret 노출 확인
- [ ] `develop`으로 PR 생성
- [ ] 코드 리뷰 반영
- [ ] 문서 업데이트
- [ ] 포트폴리오 증거 기록

## EPIC-02: Campus & Member

- [ ] Campus Entity 작성
- [ ] CampusMember Entity 작성
- [ ] 초대코드 중복 방지
- [ ] 캠퍼스 가입 중복 방지
- [ ] 캠퍼스 역할 변경 권한 검증
- [ ] 커피 담당자 지정/해제 테스트
- [ ] 캠퍼스 단위 API 권한 확인
- [ ] Docker 실행 확인
- [ ] 문서 업데이트
- [ ] 포트폴리오 증거 기록

## EPIC-03: Devotion Check

- [ ] WeeklyDevotionRecord Entity 작성
- [ ] DevotionDailyCheck Entity 작성
- [ ] 주차 기준 계산 방식 확정
- [ ] 경건생활 제출 중복 방지
- [ ] PenaltyRule 작성
- [ ] 벌금 계산 Domain Service 작성
- [ ] 청구 생성 연결
- [ ] 경건생활 벌금 계산 테스트
- [ ] Swagger/API 테스트 시나리오 작성
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-04: Billing & Payment

- [ ] PaymentAccount Entity 작성
- [ ] ChargeItem Entity 작성
- [ ] 계좌 스냅샷 저장
- [ ] 청구 상태 전이 규칙 작성
- [ ] 납부 요청 처리
- [ ] 납부 완료 처리
- [ ] 면제 처리
- [ ] 취소 처리
- [ ] 상태 전이 테스트
- [ ] Devotion/Poll 연동 시나리오 확인
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-05: Poll & Poll Template

- [ ] PollTemplate Entity 작성
- [ ] PollTemplateOption Entity 작성
- [ ] Poll Entity 작성
- [ ] PollOption Entity 작성
- [ ] PollResponse Entity 작성
- [ ] PollComment Entity 작성
- [ ] 템플릿 기반 투표 생성
- [ ] 투표 응답 중복 방지
- [ ] 마감된 투표 응답 방지
- [ ] 커피 투표 옵션 가격 기반 청구 생성
- [ ] 투표 결과 조회 테스트
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-06: Notification & FCM

- [ ] Firebase 설정 확인
- [ ] UserFcmToken Entity 작성
- [ ] FCM 토큰 저장
- [ ] 실패 토큰 비활성화 처리
- [ ] Redis 중복 발송 방지 키 설계
- [ ] Redis Lock 설계
- [ ] notification_logs 저장
- [ ] 알림 발송 실패 시나리오 테스트
- [ ] Secret Key 노출 확인
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-07: Admin Dashboard API

- [ ] 관리자 권한 정책 정리
- [ ] 각 도메인 내부 AdminController 배치
- [ ] 캠퍼스별 경건생활 집계 API 작성
- [ ] 캠퍼스별 벌금 집계 API 작성
- [ ] 캠퍼스별 투표 현황 API 작성
- [ ] 캠퍼스별 미납자 조회 API 작성
- [ ] 관리자 권한 테스트 작성
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-08: Batch / Scheduler

- [ ] 스케줄러 설정
- [ ] 미제출자 탐색 배치 작성
- [ ] 벌금 자동 생성 배치 작성
- [ ] 투표 오픈/마감 배치 작성
- [ ] 미응답자 알림 배치 작성
- [ ] 미납자 알림 배치 작성
- [ ] Redis Lock으로 중복 실행 방지
- [ ] 배치 실패 로그 정책 작성
- [ ] Docker 실행 확인
- [ ] 포트폴리오 증거 기록

## EPIC-09: Observability & Deployment

- [ ] Dockerfile 검증
- [ ] docker-compose.yml 검증
- [ ] PostgreSQL/Redis 환경 변수 정리
- [ ] 운영 profile 문서화
- [ ] Health Check API 작성
- [ ] 기본 로그 정책 정리
- [ ] 배포 README 작성
- [ ] GitHub Actions CI 검증
- [ ] Secret 노출 확인
- [ ] 포트폴리오 증거 기록

## EPIC-10: QA, Docs & Portfolio

- [ ] 핵심 API 테스트 시나리오 작성
- [ ] 인증/인가 테스트 시나리오 작성
- [ ] 경건생활 벌금 계산 테스트 시나리오 작성
- [ ] 투표-청구 연결 테스트 시나리오 작성
- [ ] 알림 중복 방지 테스트 시나리오 작성
- [ ] API 명세 README 작성
- [ ] ERD 설명 문서 작성
- [ ] DDD 패키지 구조 설명 문서 작성
- [ ] Redis 사용 목적 문서 작성
- [ ] 포트폴리오용 프로젝트 회고 작성
