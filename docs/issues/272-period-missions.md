# #272 주간·월간 미션 서버 연결

사용자는 2026-11-12 선택 질문에서 지급 기준 초안 전체 구현을 선택했다. 이후 밥 청구도 무벌금에서 제외하도록 명시했다. 새 시즌·예수님 캐릭터는 후순위이며 이번 변경에서 추가하지 않는다.

## 구현
- 활성 캠퍼스 본인 미션 GET /api/v1/campuses/{campusId}/missions/me
- 수령 POST /api/v1/campuses/{campusId}/missions/me/{mission}/claims
- 금액은 서버 고정 100/50/50/200/300P. 서버 Asia/Seoul 기간 계산. 현재 주 제출/투표, 전주 무벌금, 가장 최근 확정 가능한 월만 제공.
- 월간 대상은 월요일이 해당 월인 주. 마지막 주 종료 전에는 해당 월 보상을 열지 않는다. 응답은 대상 기간과 수령 가능 구간을 함께 제공.
- 최종 제출과 유효한 최초 투표 응답만 근거로 사용. 과거 응답의 responded_at 수정, 빈 응답, 누락 주차는 완료로 인정하지 않음.
- 무벌금과 월간 모두 제출에는 해당 주/월 전체 기간의 현재 활성 멤버십 필요. 가입·재가입 이전 제출과 응답 제외.
- 경건 PENALTY/DEVOTION_RECORD 청구만 확인. COFFEE·MEAL 제외. PAID/WAIVED도 벌금으로 인정. CANCELED는 기존 경건 재오픈·0원 재제출 흐름에 따라 제출 완료 상태일 때만 벌금에서 제외.
- 계정 잠금→멤버십 잠금→동일 기간 기존 수령 확인→원본 근거 확인→지갑 적립→mission_claims 근거 저장, 단일 트랜잭션. 재요청·복수 캠퍼스 중복 방지. 원본 제출·벌금은 단일 SQL snapshot으로 읽음.
- V30 mission_claims PK(user_id,mission_code,period_start), 거래 원장 FK, 지급 근거 JSON snapshot, 원본 캠퍼스 보존. RLS/직접 Data API 권한 차단.
- 관리자 사후 정정은 자동 회수하지 않음. 아래 운영 조회로 수령 당시 근거와 현재 기록 비교 가능.
- 일간 보상도 계정 공통 원장의 수령 여부를 조회하여 다른 캠퍼스에서 이미 받은 항목은 CLAIMED로 표시하고 재수령에 추가 적립하지 않음.

## 운영 확인 SQL
아래는 조회 전용이며 자동 회수나 지급은 수행하지 않는다.
```sql
SELECT m.user_id,m.mission_code,m.period_start,m.source_campus_id,m.evidence_json,m.claimed_at,
       w.id AS changed_week,w.submitted_at
FROM mission_claims m
CROSS JOIN LATERAL jsonb_array_elements(m.evidence_json->'evidence'->'weeks') e
LEFT JOIN weekly_devotion_records w ON w.id=(e->>'id')::bigint
WHERE w.id IS NULL OR w.submitted_at IS NULL
   OR (m.mission_code IN ('WEEKLY_NO_PENALTY','MONTHLY_NO_PENALTY') AND EXISTS (
       SELECT 1 FROM charge_items c WHERE c.campus_id=w.campus_id AND c.user_id=w.user_id
       AND c.source_type='DEVOTION_RECORD' AND c.source_id=w.id AND c.payment_category='PENALTY'
       AND c.amount>0 AND c.status<>'CANCELED'));
```

## 검증
- TDD: 기간/근거 테스트 먼저 작성하고 구현 부재 컴파일 실패 관찰 후 구현. 순수 단위6개 로컬 성공. 변경 Java 소스와 PostgreSQL 테스트 클래스 로컬 컴파일 성공.
- PostgreSQL QA7개 추가: 미션5개 총700P/복수 캠퍼스, 누락·중도 가입, 납부·면제 및 커피·밥 제외, 과거 투표 수정·빈 응답, 동시 지급/rollback, 관리자 정정·다른 사용자/비활성 멤버십, HTTP 인증·조회·수령·재요청·REST Docs.
- 전체 CI/DB 적용 결과는 최종 확인 후 기록한다.
- 운영 DB 사전 조회: 일간 수령0/지갑0/거래0/중복키0. 현재 이관 대상 없음. 운영 QA용 포인트 지급은 수행하지 않음.
- Obsidian 지정 경로와 Projects 접근은 이 환경에서 확인할 수 없어 저장소 문서로 대체.
