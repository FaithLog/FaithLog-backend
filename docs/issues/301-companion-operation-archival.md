# 동물 돌봄 멱등성 로그 보관 정책 (#301)

- 최초 90일 동안에는 동일 키가 원래 JSON 응답을 돌려준다. 그 뒤에는 요청 키, user_id, instance_id, kind, fingerprint, created_at을 **영구 보존**하되 오래된 response_json만 NULL로 축소한다.
- 같은 키와 같은 요청을 90일 이후 재전송해도 중복 XP/진화를 실행하지 않는다. 저장된 응답이 아카이빙됐다면 명시적인 409 CONFLICT를 반환하고 최신 상태를 GET 해야 한다.
- 정리 작업은 **기본 비활성화**. 스테이징 DB 백업과 복구·로그 검증 후 `faithlog.companion.operation-archive.enabled=true`와 `faithlog.scheduler.enabled=true`로 활성화.
- 04:20 Asia/Seoul 실행. 한 번에 200행 * 최대 5배치, SKIP LOCKED 적용으로 다른 돌봄 트랜잭션 대기 최소화.
- 운영 환경에서 로그 수 증가, 인덱스 크기, autovacuum/TOAST 절감률을 실측하고 회수 정책을 주기 검토.
- 기존 뽑기/판매 멱등성 로그 `companion_operations`는 고객 이력 API와 연관되므로 이 정책에 포함하지 않는다.
- 사용자의 데이터 삭제 요구 시 FK의 상위 개체 삭제 및 전반적인 계정 삭제 정책과 함께 처리해야 한다.
