# Resume Bullets

아직 실제 성과 수치를 만들지 않습니다. 구현 후 측정 가능한 값은 `향후 측정 예정`으로 표시합니다.

## 프로젝트 관리

- 기능 단위 작업이 혼재되어 진행 상황 추적이 어려운 문제를 해결하기 위해 GitHub Issue, 칸반 보드, DoR/DoD 기반의 프로젝트 관리 문서를 정립하고, Epic별 백로그와 의존성 맵을 구성하여 작업 우선순위와 리뷰 흐름을 개선했습니다. (향후 이슈 완료율 측정 예정)

## Git-Flow 협업

- 브랜치와 커밋 이력이 기능 단위로 추적되지 않는 문제를 해결하기 위해 `type/issue-number-description` 브랜치 규칙과 `type: #issue-number message` 커밋 규칙을 적용하고, PR target을 `develop`으로 통일하여 작업 추적성과 리뷰 일관성을 높였습니다. (향후 PR 처리 시간 측정 예정)

## 아키텍처

- 계층형 패키지로 도메인 책임이 섞이는 문제를 줄이기 위해 Spring Boot 단일 애플리케이션 안에서 DDD 스타일 모듈러 모놀리스 구조를 설계하고, 도메인별 `domain/application/infrastructure/presentation` 계층을 정의했습니다. (향후 모듈별 변경 영향도 회고 예정)

## 인증/보안

- JWT 인증에서 토큰 만료와 로그아웃 처리가 모호해질 수 있는 문제를 해결하기 위해 Access Token과 Refresh Token 책임을 분리하고, Redis 기반 Refresh Token/blacklist 관리 계획을 수립했습니다. (향후 인증 실패율 및 재발급 테스트 결과 기록 예정)

## Redis 활용

- Redis 사용 목적이 불명확해지는 문제를 막기 위해 token, blacklist, notification deduplication, lock으로 사용처를 제한하고, 각 기능에 TTL/Key 규칙과 실패 시 동작을 정의하도록 DoD에 반영했습니다. (향후 Redis key 정책 문서화 예정)

## 도메인 설계

- 도메인 간 Entity 직접 참조로 결합도가 높아지는 문제를 줄이기 위해 `userId`, `campusId`, `pollId` 등 ID 참조 우선 원칙을 수립하고, Poll-Billing, Devotion-Billing 연동을 command/result 중심으로 설계했습니다. (향후 연동 테스트 결과 기록 예정)

## 결제/청구 상태 관리

- 벌금과 커피비 청구 상태가 섞여 변경 이력이 불명확해질 수 있는 문제를 해결하기 위해 ChargeItem과 BillingStatus 전이 규칙을 분리하고, 계좌 정보는 스냅샷으로 보존하는 방향을 설계했습니다. (향후 상태 전이 테스트 커버리지 측정 예정)

## 알림/비동기 처리

- 미제출자/미응답자/미납자 알림이 중복 발송될 수 있는 문제를 해결하기 위해 FCM 발송 로그와 Redis deduplication key, Redis Lock 기반의 알림/배치 운영 계획을 수립했습니다. (향후 중복 발송 방지 테스트 결과 기록 예정)

## 운영/배포

- 로컬에서는 동작하지만 Docker 환경에서 실패하는 문제를 예방하기 위해 PR 체크리스트에 Docker 실행 확인을 필수화하고, PostgreSQL/Redis를 포함한 Docker Compose 기반 검증 흐름을 정의했습니다. (향후 CI 통과율과 Docker 재현성 기록 예정)
