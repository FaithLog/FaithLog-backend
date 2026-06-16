# FaithLog Codex Instructions

이 파일은 FaithLog 저장소의 단일 Agent 규칙 파일이다.

Codex는 계획, 편집, 구현, 테스트 전략, 배포, 모니터링, 이력서 지표 해석을 시작하기 전에 이 파일과 `docs/codex/FAITHLOG_CODEX_HOOK.md`를 읽고 따른다.

## 1. 최우선 의사결정 규칙

사용자가 이 프로젝트의 유일한 의사결정권자다.

Codex는 요구사항, 아키텍처, 스키마, API 동작, 보안 동작, 배포 정책, 테스트 범위, 모니터링 범위, 이력서 지표 해석, 구현 트레이드오프를 추측하거나 조용히 결정하지 않는다.

애매하거나 이상하거나 위험하거나 명시적으로 승인되지 않은 내용은 구현 전에 멈추고 사용자에게 묻는다.

## 2. 문서 우선순위

충돌이 있으면 아래 순서로 따른다.

1. 현재 대화에서 사용자가 명시한 최신 결정
2. `docs/decision-log.md`에 기록된 사용자 승인 결정
3. Notion 최종 기획서, 최종 ERD, API 최종 공통 기준
4. `docs/codex/FAITHLOG_CODEX_HOOK.md`
5. `docs/backend-implementation-policy.md`
6. 기존 코드 패턴

Notion 문서가 있더라도 현재 대화의 사용자 결정이나 `docs/decision-log.md`에 기록된 승인 결정을 덮어쓸 수 없다.

## 3. Mandatory Ask Rule

아래 항목이 불명확하면 Codex는 반드시 질문한 뒤 진행한다.

- 제품 요구사항 또는 사용자-facing 동작
- API 계약, 데이터베이스 스키마, Entity 관계, 검증 규칙
- 보안, 인증, 인가, 결제, 알림, 배포 동작
- 테스트 전략, 품질 게이트, 성능 목표, 모니터링 지표
- 이력서 지표 해석 또는 성과로 집계할 수치
- 파일 삭제, 대규모 리팩터링, 의존성 추가, 브랜치/커밋/PR 범위, 릴리스 시점

질문할 때는 무엇이 애매한지, 왜 중요한지, 가능한 최소 선택지, 추천안을 함께 제시한다.

Codex는 사용자가 명시적으로 승인한 뒤에만 해당 결정을 구현한다.

## 4. Allowed Without Asking

Codex는 아래처럼 방향성을 새로 결정하지 않는 작업은 질문 없이 진행할 수 있다.

- 파일 읽기 및 프로젝트 상태 확인
- 테스트, 빌드, 린트, 상태 확인 같은 안전한 검증 명령 실행
- 프로젝트에서 관찰한 사실 또는 사용자 승인 결정을 문서에 기록
- 측정 결과, 트러블슈팅, 테스트 결과 기록

## 5. 핵심 개발 규칙

1. 작업은 GitHub Issue 기준으로 진행하고, 가능하면 GitHub Projects 칸반 카드와 연결한다.
2. 작업 시작 시 Project 카드 상태를 `In Progress`로 갱신한다.
3. 기능 구현 전 실패하는 테스트를 먼저 작성한다.
4. Controller에서 Entity를 직접 반환하지 않는다.
5. 경건생활 제출 시 벌금 청구는 자동 생성/갱신한다.
6. 커피 투표 응답 시 커피 청구는 자동 생성/갱신한다.
7. `납부했어요`를 누르면 즉시 PAID 처리한다.
8. 투표 응답은 `optionIds` 배열과 `poll_response_options` 구조를 사용한다.
9. 보안 키와 `.env` 파일을 커밋하지 않는다.
10. 작업 완료 후 가능한 경우 `./gradlew test`를 실행한다.
11. 기능 개발 기록, 수치, 테스트 결과, 트러블슈팅은 Obsidian Vault와 저장소 문서에 남긴다.

## 6. Recordkeeping Rule

중요한 제품, 아키텍처, 데이터, 배포, 테스트 전략, 이력서 지표 결정은 `docs/decision-log.md`에 기록한다.

이력서에 사용할 수 있는 수치, 테스트 결과, 개선 결과, 트러블슈팅은 `docs/resume-metrics.md`와 Obsidian 문서에 기록한다.

현재 저장소에서 사용하는 Obsidian 기준 경로는 `docs/codex/FAITHLOG_CODEX_HOOK.md`의 Obsidian 문서화 Hook을 따른다.

## 7. Hard Stop

확신이 없으면 질문한다. 빈칸을 추측으로 채우지 않는다.
