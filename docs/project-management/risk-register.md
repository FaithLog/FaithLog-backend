# Risk Register

## RISK-001

Risk: DDD 구조를 과하게 적용해서 개발 속도가 느려질 위험  
Impact: 기능 개발 지연  
Probability: Medium  
Mitigation: MVP에서는 Domain Event, Hexagonal Architecture, 과도한 Value Object를 보류합니다.  
Owner: Tech Lead  
Trigger: 단순 CRUD에도 파일 수가 과도하게 늘어남  
Response Plan: 구조를 Command/Result/Repository 분리 수준으로 축소합니다.

## RISK-002

Risk: 도메인 간 참조가 복잡해질 위험  
Impact: 순환 의존성과 변경 전파 증가  
Probability: Medium  
Mitigation: 다른 도메인의 Entity 직접 참조 대신 `userId`, `campusId`, `pollId`를 사용합니다.  
Owner: Backend  
Trigger: Entity에 다른 도메인 Entity 필드가 추가됨  
Response Plan: ID 참조와 application service 조합으로 되돌립니다.

## RISK-003

Risk: Poll과 Billing의 결합도가 높아질 위험  
Impact: 투표 변경이 청구 기능을 깨뜨림  
Probability: Medium  
Mitigation: 커피 투표 청구 생성은 `sourceType`, `sourceId`, `userId`, `amount` 기반으로 연결합니다.  
Owner: Poll/Billing owner  
Trigger: Poll Entity가 Billing Entity를 직접 참조  
Response Plan: 청구 생성 command로 경계를 분리합니다.

## RISK-004

Risk: Devotion과 Billing의 결합도가 높아질 위험  
Impact: 벌금 규칙 변경 시 청구 데이터 충돌  
Probability: Medium  
Mitigation: 계산 결과를 Billing command로 전달하고 기존 청구는 스냅샷으로 보존합니다.  
Owner: Devotion/Billing owner  
Trigger: 벌금 재계산이 기존 청구 금액을 직접 수정  
Response Plan: 취소/재생성 또는 조정 청구 정책을 추가합니다.

## RISK-005

Risk: Redis 사용 목적이 불명확해질 위험  
Impact: DB로 충분한 기능에 Redis가 남용됨  
Probability: Medium  
Mitigation: Redis 사용처를 token, blacklist, notification dedup, lock으로 제한합니다.  
Owner: Tech Lead  
Trigger: 캐시 목적이 불명확한 Redis repository 추가  
Response Plan: Redis 사용 목적 문서 업데이트 후 승인합니다.

## RISK-006

Risk: Redis Key/TTL 설계가 누락될 위험  
Impact: 토큰/알림 데이터가 만료되지 않거나 중복 발송 발생  
Probability: High  
Mitigation: Redis 이슈 DoD에 key, TTL, 실패 시 동작을 필수로 둡니다.  
Owner: Backend  
Trigger: Redis 구현체에 TTL 상수가 없음  
Response Plan: key spec을 작성하고 테스트를 추가합니다.

## RISK-007

Risk: FCM 실패 토큰 처리 누락 위험  
Impact: 비활성 토큰에 계속 발송  
Probability: Medium  
Mitigation: 실패 응답별 토큰 비활성화 정책을 문서화합니다.  
Owner: Notification owner  
Trigger: 발송 실패 로그만 남고 토큰 상태 변화 없음  
Response Plan: 실패 토큰 처리 이슈를 Blocker로 올립니다.

## RISK-008

Risk: 관리자 기능이 여러 도메인에 흩어져 관리가 어려워질 위험  
Impact: 권한 정책 중복과 API 일관성 저하  
Probability: Medium  
Mitigation: 별도 admin 도메인 없이 각 도메인 AdminController를 두되 권한 정책은 공통 문서화합니다.  
Owner: Tech Lead  
Trigger: AdminController마다 권한 체크 방식이 다름  
Response Plan: 관리자 권한 정책 테스트를 추가합니다.

## RISK-009

Risk: 벌금 규칙 변경 시 기존 청구 데이터와 충돌할 위험  
Impact: 사용자 납부 금액 혼선  
Probability: Medium  
Mitigation: ChargeItem에는 생성 당시 금액과 source를 보존합니다.  
Owner: Billing owner  
Trigger: PenaltyRule 변경 후 기존 ChargeItem 금액 변경 요구  
Response Plan: 조정 청구 또는 취소 후 재생성 정책을 적용합니다.

## RISK-010

Risk: PostgreSQL 마이그레이션 관리 누락 위험  
Impact: 환경 간 schema drift 발생  
Probability: Medium  
Mitigation: MVP 초반 이후 Flyway/Liquibase 도입 여부를 결정합니다.  
Owner: Backend  
Trigger: Entity 변경이 운영 DB에 수동 반영됨  
Response Plan: 마이그레이션 도구 도입 이슈를 생성합니다.

## RISK-011

Risk: 테스트 없이 기능이 쌓일 위험  
Impact: 회귀 버그 증가  
Probability: High  
Mitigation: DoD에 테스트 코드 또는 테스트 시나리오를 필수로 둡니다.  
Owner: All  
Trigger: PR에 테스트 항목이 비어 있음  
Response Plan: PR을 QA / Test로 이동하지 않습니다.

## RISK-012

Risk: Docker 환경 검증 없이 PR이 올라올 위험  
Impact: 팀원 환경에서 실행 실패  
Probability: High  
Mitigation: PR 템플릿과 DoD에 Docker 확인을 필수로 둡니다.  
Owner: Author  
Trigger: Docker 체크 미선택 PR  
Response Plan: 리뷰에서 changes requested 처리합니다.

## RISK-013

Risk: `.env` 또는 Secret Key가 노출될 위험  
Impact: 보안 사고  
Probability: Medium  
Mitigation: `.gitignore`, PR 체크, Secret scan 습관을 적용합니다.  
Owner: All  
Trigger: `.env`, `.key`, Firebase json staged  
Response Plan: 즉시 key rotation 후 커밋 제거합니다.

## RISK-014

Risk: 커밋 메시지에 이슈 번호가 누락될 위험  
Impact: 작업 추적성 저하  
Probability: Medium  
Mitigation: commit-msg hook 또는 리뷰 체크로 `type: #issue message`를 강제합니다.  
Owner: All  
Trigger: issue 번호 없는 커밋  
Response Plan: rebase/amend로 메시지를 수정합니다.

## RISK-015

Risk: develop 최신화 없이 브랜치를 생성해 충돌이 커질 위험  
Impact: 병합 충돌과 리뷰 지연  
Probability: Medium  
Mitigation: branch-strategy 체크리스트에 pull 규칙을 둡니다.  
Owner: Author  
Trigger: 오래된 base에서 PR 생성  
Response Plan: 최신 develop rebase 후 재검증합니다.

## RISK-016

Risk: 포트폴리오용 산출물이 개발 후반에 누락될 위험  
Impact: 프로젝트 성과 설명이 약해짐  
Probability: High  
Mitigation: PR 템플릿과 DoD에 portfolio-log 기록을 둡니다.  
Owner: PM  
Trigger: 중요한 설계 결정이 기록되지 않음  
Response Plan: 스프린트 종료 시 portfolio-log를 보강합니다.
