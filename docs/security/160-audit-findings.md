# Issue #160 input validation and sensitive-data exposure audit findings

## 1. Conclusion

At baseline `52e0b4ae26995e2a730832d4164d1cf8212b95be`, the audit traced 21 Controllers,
80 endpoints, 36 request DTOs, 57 response DTOs, 123 path/query bindings, 4 page/sort parsers,
3 global exception files, 2 production logger-bearing files, 8 application config files, and
56 persistence-constraint files.

| Classification | Critical | High | Medium | Low | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| confirmed finding | 0 | 0 | 2 | 0 | 2 |

- Confirmed findings: 2
- False positive / intended policy rows: 9
- Unverified / hardening rows: 3
- Predecessor findings or operational checks excluded from #160 totals: 5
- Fix Issues, code/test/config/DB/Flyway changes, Docker, push, and PR: 0
- Existing focused XML suites: 16 classes / 138 tests / 0 failures / 0 errors / 0 skipped
- Gradle task result: failed for cache metadata and concurrent XML reporting reasons, not test assertion failures

## 2. Confirmed findings

### F-160-01 excessive Saturday lateness overflows into a negative PENALTY charge

- Severity: **MEDIUM**
- Confidence: **10/10**
- Status: **VERIFIED** by independent fresh-context static trace
- CWE: **CWE-190 Integer Overflow or Wraparound**, related **CWE-20 Improper Input Validation**
- OWASP: API6:2023 Unrestricted Access to Sensitive Business Flows; A04:2021 Insecure Design
- ASVS 5.0: V2 Validation and Business Logic
- Trust boundary: authenticated ACTIVE member weekly devotion submission -> penalty calculation -> billing charge

#### Evidence

- `src/main/java/com/faithlog/devotion/controller/dto/request/UpdateWeeklyDevotionRequest.java:11-14`
  accepts primitive `saturdayLateMinutes` without an upper bound.
- `src/main/java/com/faithlog/devotion/service/WeeklyDevotionCommandService.java:191-194` rejects only
  negative values.
- `src/main/java/com/faithlog/devotion/domain/DevotionFineCalculator.java:21-24,50-53` uses unchecked
  `int` multiplication, addition, and total summation.
- `src/main/java/com/faithlog/devotion/service/WeeklyDevotionCommandService.java:140-150` skips only an
  exactly zero total, so a wrapped negative total enters charge creation.
- `src/main/java/com/faithlog/billing/service/ChargeCreationService.java:32-75` and
  `src/main/java/com/faithlog/billing/domain/entity/ChargeItem.java:93-153` do not require a positive amount.
- `src/main/resources/db/migration/V1__initial_schema.sql:234-256` declares an integer amount without
  a positive check constraint. No later migration adds one.
- `src/main/java/com/faithlog/admin/service/AdminDashboardQueryService.java:157-175` sums persisted
  unpaid amounts, allowing a negative row to offset legitimate campus debt.

#### Reproduction prerequisites and exploit path

1. The attacker is an authenticated ACTIVE member, has an unsubmitted week, and the campus has an
   active Saturday-lateness rule and PENALTY account.
2. The attacker submits the week with a sufficiently large positive `saturdayLateMinutes` value.
3. The request passes the negative-only validation.
4. `base + minutes * amountPerUnit` wraps below zero as a Java `int`.
5. Because the total is not zero, the transaction persists a negative `UNPAID` PENALTY charge.
6. Member/admin charge views and the campus dashboard include the negative amount in totals.

#### Impact boundaries

- Minimum confirmed impact: the attacker persists a negative charge for their own weekly record and
  corrupts member/admin financial totals.
- Conditional maximum impact: the negative row can offset other members' legitimate unpaid totals in
  the campus dashboard and distort collection decisions.
- Not claimed: direct refund, bank debit, modification of another individual charge row, or cross-campus access.

#### Current defenses

- ACTIVE campus membership and principal-owned weekly submission
- Monday/date/duplicate-final-submission checks
- negative lateness rejection
- active PENALTY account requirement for a nonzero result

These defenses do not bound the positive input or enforce positive arithmetic results at calculator,
charge entity, or database boundaries.

#### Recommended fix, not approved or implemented

1. Approve a domain maximum for `saturdayLateMinutes` and penalty rule money/count fields, then enforce
   it in DTO/application/domain validation.
2. Calculate with `long` and exact arithmetic, reject overflow, and range-check before converting to storage type.
3. Require `amount > 0` in Billing domain creation/update and a database check constraint.
4. Add regression tests for the maximum accepted boundary, overflow rejection, and absence of charge/dashboard mutation.

Proposed follow-up Issue, not created: `[Security] 경건 벌금 계산 overflow와 음수 청구 차단`.

### F-160-02 client-controlled COFFEE option price bypasses the backend catalog authority

- Severity: **MEDIUM**
- Confidence: **9/10**
- Status: **VERIFIED** by independent fresh-context static trace and existing HTTP test behavior
- CWE: **CWE-915 Improperly Controlled Modification of Dynamically-Determined Object Attributes**,
  related **CWE-20 Improper Input Validation**
- OWASP: API6:2023 Unrestricted Access to Sensitive Business Flows; A04:2021 Insecure Design
- ASVS 5.0: V2 Validation and Business Logic
- Trust boundary: COFFEE duty/campus manager Poll creation -> option price snapshot -> member response -> settlement

#### Evidence

- `src/main/java/com/faithlog/poll/controller/dto/request/PollOptionRequest.java:5-13` and
  `PollTemplateOptionRequest.java:5-13` expose nullable `menuId` plus client-controlled `priceAmount`
  without a range or catalog-only rule.
- `src/main/java/com/faithlog/poll/service/PollOptionSnapshotResolver.java:24-37,59-65` trusts the
  request price when `menuId` is null. Only `:67-73` derives the authoritative catalog price.
- `src/main/java/com/faithlog/poll/service/PollCreationCommandService.java:105-132` persists direct
  snapshots; `PollTemplateCommandService.java:39-110` and `PollTemplateOptionSupport.java:24-42`
  persist template snapshots.
- `src/main/java/com/faithlog/poll/service/CoffeePollSettlementSupport.java:72-87` uses the selected
  option snapshot price without catalog reconciliation.
- `src/main/java/com/faithlog/billing/service/ChargeCreationService.java:79-119` and
  `ChargeItem.java:124-153,182-196` forward the amount without a positive/range check.
- `src/test/java/com/faithlog/poll/controller/PollApiRestDocsTest.java:894-918` already demonstrates
  an accepted COFFEE direct option with null menu ID and a client price; there is no rejection test.
- Approved project policy requires paid Compose Coffee option names/prices to originate from the
  backend catalog and be copied as snapshots.

#### Reproduction prerequisites and exploit path

1. The attacker is an active COFFEE duty user or campus manager and owns an active same-campus COFFEE account.
2. The attacker creates a direct COFFEE Poll or COFFEE template with an option whose `menuId` is null
   and whose content/price are attacker supplied.
3. The resolver persists that request price instead of requiring the catalog.
4. A victim selects the forged option and the Poll closes.
5. Settlement creates an `UNPAID` charge using the forged snapshot and the attacker's payment account.

#### Impact boundaries

- Minimum confirmed impact: a falsified COFFEE charge record is created for a responding member.
- Conditional maximum impact: a member who trusts the displayed charge can transfer an inflated amount;
  an auto-created template can repeat the forged price across members and weeks.
- Not claimed: automatic debit, a charge without victim selection, cross-campus access, or hidden account ownership.

#### Current defenses

- COFFEE creation permission
- active same-campus requester-owned COFFEE account
- response option must belong to the Poll
- only CLOSED eligible COFFEE Polls settle

The defenses establish actor, tenant, account, and selected option, but do not establish catalog price provenance.

#### Recommended fix, not approved or implemented

1. For COFFEE Poll/template creation and update, require `menuId` for every chargeable option and reject
   request `content`/`priceAmount` as authoritative input.
2. Resolve name/code/price only from an active catalog row and reject nonpositive/out-of-range amounts in Billing.
3. Add regression tests for null/inactive menu, client price override, negative/overflow price, template
   auto-copy, and no charge mutation on rejection.
4. Preserve direct content/zero-price behavior only for non-COFFEE Poll types under the approved contract.

Proposed follow-up Issue, not created: `[Security] COFFEE 옵션 가격을 backend catalog로 고정`.

## 3. False positives and intended policies

| # | Candidate | Decision | Evidence summary |
| ---: | --- | --- | --- |
| 1 | login/refresh expose bearer tokens | intended credential response | access/refresh and expiry are returned; JTI/sessionId/tokenVersion are not response fields |
| 2 | FCM registration response returns token/client metadata | intended SELF response | principal fixes owner; approved decision explicitly keeps registered token in REST Docs response |
| 3 | ACTIVE members receive full account number/holder/bank | intended product policy | transfer requires these fields; member DTO excludes owner/status/timestamps |
| 4 | admin account DTO adds owner and lifecycle metadata | intended admin boundary | manager/duty/service-admin authorization is enforced before the admin DTO |
| 5 | admin/missing-member/duty responses contain user identity | intended operational identity | service ADMIN or campus manager/duty scope applies; no public endpoint returns these lists |
| 6 | anonymous Poll stores user ID | predecessor intended defense | identity is stored for duplicate/missing logic but anonymous result DTO remains aggregate-only; not recounted from #159 |
| 7 | all ACTIVE campus members read prayer content | intended product policy | approved campus-wide weekly board; write authorization is narrower |
| 8 | unknown JSON fields imply Entity mass assignment | false positive | explicit request records map to commands; no Controller binds/returns an Entity |
| 9 | dynamic filters or seed/Firebase parsing allow injection/traversal | false positive | parameterized Criteria/JPQL, sort allowlists, fixed classpath seed, and environment-only credential path |

## 4. Unverified and hardening items

| ID | Confidence | Item | Evidence / reason not confirmed |
| --- | ---: | --- | --- |
| U-160-01 | 4/10 | notification/prayer/FCM text and target/list request sizes have no application maximum | effective targets are campus-scoped and duplicates are normalized; minimum demonstrated impact is only request/heap/DB/FCM resource use, and gateway limits are unknown |
| U-160-02 | 4/10 | several DTO lengths do not match varchar columns, including Poll memo and patch/name/title paths | an overlong value can reach DB failure/5xx, but no cross-user write, disclosure, durable corruption, or client SQL/stacktrace exposure was demonstrated |
| U-160-03 | 6/10 | raw Firebase provider exception message is persisted as token/log failure reason and manager log response | provider messages may contain internal detail or a registration token, but no real provider response or value was inspected and existing fixtures do not prove an echo |

No item below 8/10 is included in the confirmed total.

## 5. Predecessor exclusions

The following are referenced but not counted again:

1. F-157-01 last active service ADMIN withdrawal guard.
2. F-158-01 refresh rotation race and its #176 atomic fix.
3. F-159-01 request-body template authorization pivot and its #179 persisted-target fix.
4. U-158-05 Cloud Run/load-balancer/APM credential redaction evidence.
5. #157 production SpringDoc/actuator deployment-state verification.

## 6. Missing regression tests and follow-up candidates

No test or Issue is added by this audit.

| Priority | Candidate | Reason |
| --- | --- | --- |
| Medium | `[Security] 경건 벌금 계산 overflow와 음수 청구 차단` | F-160-01, one financial-integrity boundary |
| Medium | `[Security] COFFEE 옵션 가격을 backend catalog로 고정` | F-160-02, one catalog/settlement trust boundary |
| Test gap | exact arithmetic and positive charge invariant | current tests cover negative input, not large positive overflow |
| Test gap | COFFEE null-menu/client-price rejection | existing REST Docs demonstrates acceptance, not rejection |
| Test gap | DTO/DB length and collection maxima | no oversized request coverage |
| Hardening | sanitize/classify provider failure reasons | U-160-03 requires provider-message evidence and an approved retention/visibility policy |

## 7. Standards mapping

| Standard | Audit result |
| --- | --- |
| OWASP API3:2023 BOPLA | member/admin account DTO separation, anonymous Poll, FCM self response, admin identity surfaces traced |
| OWASP API6:2023 Sensitive Business Flows | F-160-01 and F-160-02 affect charge integrity |
| OWASP API8:2023 Security Misconfiguration | fixed error responses and prod example SpringDoc disable policy traced; deployed state not duplicated |
| OWASP API9:2023 Inventory Management | 21 Controllers / 80 endpoints / 36 request / 57 response DTOs counted |
| OWASP A03:2021 Injection | no SQL/JPQL/command/template/unsafe-deserialization request path confirmed |
| ASVS V2 | range, collection, date/order, normalization, exact arithmetic, and catalog provenance reviewed |
| ASVS V8 | sensitive response fields and role boundaries traced; predecessor authorization findings excluded |
| ASVS V14 | error, logging, config, actuator, SpringDoc, REST Docs, and secret-pattern boundaries reviewed |

## 8. Verification and scope integrity

- Counted manifest totals were mechanically compared with repository paths.
- JUnit XML: 16 classes / 138 tests / 0 failures / 0 errors / 0 skipped.
- Gradle did not produce a successful task result because the default cache metadata was unreadable and
  overlapping isolated runs collided while writing XML. The audit stopped after three attempts.
- Current/high-signal history secret prefix candidates: 0; generated high-signal prefix files: 0.
- Generated JWT-shaped values were confined to 244 ignored test-document files; no value was recorded.
- Docker: not used.
- Production code, test code, config, database, Flyway, and infrastructure changes: 0.
- Fix Issues, push, and PR: not performed.
- No new product decision was made, so `docs/decision-log.md` was not changed.

## 9. Audit limitations and disclaimer

This audit used repository static analysis, independent code verification, and existing focused tests.
It did not inspect production credentials, Cloud Run/Supabase/Upstash/Firebase consoles, mobile secure
storage, deployed gateway limits, or live network behavior, and it did not perform an aggressive scan.
AI-assisted review is not a substitute for an independent professional penetration test. A production
service handling personal, account, prayer, and notification data should receive periodic expert review
and explicitly authorized dynamic testing.
