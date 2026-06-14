# Dependency Map

## Dependency Table

| Task | Depends On | Blocks | Reason |
| --- | --- | --- | --- |
| SETUP-003 | SETUP-001 | JPA 기반 Entity 작업 | PostgreSQL 연결과 Docker 환경이 먼저 필요합니다. |
| SETUP-004 | SETUP-001 | AUTH-004, AUTH-005, NOTI-008, NOTI-009, BATCH-007 | Redis 기반 토큰/알림/락 구현의 선행 조건입니다. |
| ARCH-001 | SETUP-001 | 모든 도메인 구현 | DDD 패키지 구조가 먼저 잡혀야 합니다. |
| ARCH-005 | - | 모든 브랜치/PR 작업 | Git-Flow와 PR 규칙 없이 협업을 시작하지 않습니다. |
| USER-001 | ARCH-001 | AUTH-003, CAMPUS-004, DEVOTION-003, POLL-008, BILLING-005 | 대부분의 사용자 기반 기능이 userId를 필요로 합니다. |
| CAMPUS-001 | ARCH-001 | devotion, poll, billing, notification 캠퍼스 기능 | 캠퍼스 단위 운영의 기준입니다. |
| CAMPUS-002 | CAMPUS-001, USER-001 | devotion, poll, billing, notification 캠퍼스 기능 | 캠퍼스 멤버와 역할이 권한의 기준입니다. |
| BILLING-001 | CAMPUS-001 | DEVOTION-010, POLL-013 | 청구 계좌와 스냅샷의 기준입니다. |
| BILLING-002 | BILLING-001, USER-001 | DEVOTION-010, POLL-013 | 청구 항목이 있어야 외부 도메인에서 청구를 생성합니다. |
| POLL-001 | CAMPUS-001 | POLL-008, POLL-010, POLL-013 | 템플릿 기반 투표 생성의 기준입니다. |
| POLL-005 | CAMPUS-001 | POLL-008, POLL-010, POLL-013 | 실제 투표 생명주기의 기준입니다. |
| NOTI-002 | USER-001 | NOTI-005, NOTI-006, 배치 알림 | 알림 대상 토큰이 필요합니다. |
| NOTI-004 | USER-001 | NOTI-005, NOTI-010 | 알림 발송 로그 저장의 기준입니다. |
| BATCH-007 | SETUP-004 | 모든 자동 알림 배치 | Redis Lock 없이 자동 배치 중복 실행을 막기 어렵습니다. |

## Epic Dependency

```mermaid
flowchart TD
  E00["EPIC-00 Setup & Architecture"] --> E01["EPIC-01 User & Auth"]
  E00 --> E02["EPIC-02 Campus & Member"]
  E01 --> E02
  E02 --> E03["EPIC-03 Devotion Check"]
  E02 --> E04["EPIC-04 Billing & Payment"]
  E02 --> E05["EPIC-05 Poll & Template"]
  E04 --> E03
  E04 --> E05
  E01 --> E06["EPIC-06 Notification & FCM"]
  E02 --> E06
  E03 --> E07["EPIC-07 Admin Dashboard API"]
  E04 --> E07
  E05 --> E07
  E06 --> E08["EPIC-08 Batch / Scheduler"]
  E03 --> E08
  E04 --> E08
  E05 --> E08
  E00 --> E09["EPIC-09 Observability & Deployment"]
  E03 --> E10["EPIC-10 QA, Docs & Portfolio"]
  E04 --> E10
  E05 --> E10
```

## Git-Flow Workflow

```mermaid
flowchart LR
  A["Create GitHub Issue"] --> B["Pull latest develop"]
  B --> C["Create branch type/issue-description"]
  C --> D["Develop with small commits"]
  D --> E["Commit type: #issue message"]
  E --> F["Open PR to develop"]
  F --> G["Code Review"]
  G --> H["QA / Test"]
  H --> I["Merge only error-free code"]
```

## Auth, Campus, Devotion, Billing

```mermaid
flowchart TD
  S3["SETUP-003 PostgreSQL"] --> A1["ARCH-001 DDD package"]
  A1 --> U1["USER-001 User Entity"]
  U1 --> AU3["AUTH-003 Login API"]
  AU3 --> C4["CAMPUS-004 Invite Join"]
  C1["CAMPUS-001 Campus"] --> C2["CAMPUS-002 CampusMember"]
  C2 --> D3["DEVOTION-003 Weekly Submit"]
  D3 --> D9["DEVOTION-009 Fine Calculator"]
  B1["BILLING-001 Account"] --> B2["BILLING-002 ChargeItem"]
  B2 --> D10["DEVOTION-010 Create Charges"]
  D9 --> D10
```

## Poll, Billing, Notification, Batch

```mermaid
flowchart TD
  P1["POLL-001 PollTemplate"] --> P4["POLL-004 Create Poll From Template"]
  P5["POLL-005 Poll"] --> P8["POLL-008 Vote API"]
  P8 --> P10["POLL-010 Result API"]
  B2["BILLING-002 ChargeItem"] --> P13["POLL-013 Coffee Charge Link"]
  P8 --> P13
  S4["SETUP-004 Redis"] --> N8["NOTI-008 Dedup Key"]
  S4 --> N9["NOTI-009 Notification Lock"]
  N2["NOTI-002 FCM Token"] --> N5["NOTI-005 Send Notification"]
  N4["NOTI-004 NotificationLog"] --> N10["NOTI-010 Save Log"]
  N8 --> BA7["BATCH-007 Redis Lock"]
  N9 --> BA7
  BA7 --> BA5["BATCH-005 Poll Reminder"]
  BA7 --> BA6["BATCH-006 Payment Reminder"]
```
