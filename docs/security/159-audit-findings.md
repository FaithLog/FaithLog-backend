# Issue #159 campus isolation and IDOR security audit findings

## 1. Conclusion

At baseline `5cad9f76df0a0e1bd0a086ab4059f6ccb5e51af2`, the audit revalidated 80 endpoints
in 21 Controllers, 17 object-identifier categories, 56 authorization implementation files,
25 repository files, and 13 focused test classes.

| Classification | Critical | High | Medium | Low | Total |
| --- | ---: | ---: | ---: | ---: | ---: |
| confirmed finding | 0 | 0 | 1 | 0 | 1 |

- Confirmed findings: 1
- False positive / intended policy rows: 8
- Unverified or policy-dependent rows: 4
- Focused tests: 172 / 0 failures / 0 errors / 0 skipped
- Production code, test code, config, database, Flyway, and infrastructure changes: 0
- Docker runs: 0
- Fix Issues, push, and PR: 0

F-157-01 and F-158-01 are not duplicated. They remain separate predecessor findings and do not
contribute to the #159 totals.

## 2. Confirmed finding

### F-159-01 COFFEE duty can update a non-COFFEE template by changing request-body billing fields

- Severity: **MEDIUM**
- Confidence: **10/10**
- Status: **VERIFIED** by two independent static traces of the same control flow; no separate agent was used because this session prohibits sub-agent delegation
- CWE: **CWE-863 Incorrect Authorization**; related **CWE-639 Authorization Bypass Through User-Controlled Key**
- OWASP: API5:2023 Broken Function Level Authorization; API1:2023 Broken Object Level Authorization
- ASVS 5.0: V8 Authorization, V2 Validation and Business Logic
- Trust boundary: authenticated same-campus COFFEE duty → admin Poll template command

#### Evidence

- `src/main/java/com/faithlog/poll/service/PollTemplateCommandService.java:79-96` loads the
  persisted template and confirms the path campus, but passes the persisted `template.pollType()`
  together with request-controlled `chargeGenerationType` and `paymentCategory` to the authorization decision.
- `src/main/java/com/faithlog/poll/service/PollTemplateCommandService.java:161-176` treats the update
  as a COFFEE template whenever the request body sets `paymentCategory=COFFEE`, then permits an active
  COFFEE duty user.
- `src/main/java/com/faithlog/poll/service/PollTemplateCommandService.java:89-96,135-159` requires an
  active same-campus COFFEE account owned by the requester. That is a useful defense, but it does not
  prove that the persisted target is a COFFEE template.
- `src/main/java/com/faithlog/poll/domain/entity/PollTemplate.java:166-190` then updates title,
  selection type, billing mode/category/account, user-option setting, automatic creation schedule,
  and time window. `pollType` remains immutable, so the target is still a non-COFFEE template after
  unauthorized mutation.
- `src/main/java/com/faithlog/poll/controller/dto/request/UpdatePollTemplateRequest.java:18-49`
  exposes all pivot and mutation fields in the request body.
- `src/main/java/com/faithlog/poll/service/result/PollResult.java:12-46` and the member Poll detail
  response expose `templateId` when a Poll was created from a template. Exploitation therefore does
  not depend on keeping sequential IDs secret.
- `src/test/java/com/faithlog/poll/service/PollServiceTest.java:220-301,817-836` covers COFFEE-duty
  updates to COFFEE templates, owner-account rejection, and manager deactivation, but has no negative
  test for a duty user targeting a persisted CUSTOM/WED_SERVICE/SATURDAY_LEADER template.

#### Reproduction prerequisites and exploit path

1. The attacker is an active member and the active `DutyType.COFFEE` assignee in campus A.
2. The attacker owns an active COFFEE payment account in campus A.
3. A manager-owned non-COFFEE template exists in campus A. The attacker obtains its `templateId`
   from a template-derived Poll detail or another legitimate same-campus observation.
4. The attacker sends `PATCH /api/v1/admin/campuses/{campusA}/poll-templates/{templateId}` with
   `paymentCategory=COFFEE`, a COFFEE-compatible charge mode, and the attacker's account ID.
5. `requireTemplateManageAccess` classifies the request from body fields as COFFEE and accepts the
   duty assignment even though `template.pollType()` is not COFFEE.
6. The server persists attacker-selected title, selection type, options, option-add behavior, and
   automatic schedule on the manager-controlled non-COFFEE template.

#### Impact boundaries

- Minimum confirmed impact: unauthorized same-campus integrity change to one non-COFFEE template,
  including its title, options, selection semantics, and schedule.
- Conditional maximum impact: if automatic creation is enabled or later enabled by the attacker,
  future manager-facing and member-facing Polls can be generated from the tampered template until it
  is detected or corrected.
- Not claimed: cross-campus access, prayer/billing record disclosure, or automatic COFFEE charge
  creation. Settlement separately requires `pollType=COFFEE`, so the current evidence does not prove
  that a tampered non-COFFEE template can generate charge items.

#### Current defenses

- valid active principal and ACTIVE campus membership
- active COFFEE duty in the same campus
- path campus must match the persisted template campus; cross-campus mismatch is hidden as 404
- selected COFFEE account must be active, same-campus, COFFEE type, and requester-owned

These defenses limit the actor and tenant but do not enforce the approved function boundary: a duty
user may manage COFFEE templates only.

#### Recommended fix, not approved or implemented

1. Base authorization on the persisted target first. A duty-only requester may update only when
   `template.pollType() == COFFEE`; otherwise require campus manager or service ADMIN.
2. Validate the requested post-update configuration separately so a COFFEE template cannot be moved
   into a contradictory non-COFFEE billing state.
3. Add a regression test where an active COFFEE duty user with an owned account attempts to update a
   CUSTOM/WED_SERVICE/SATURDAY_LEADER template and receives 403 with no field or option changes.
4. Keep existing same-campus and account-owner tests.

Proposed follow-up Issue, not created: `[Security] COFFEE duty Poll template update authorization target 고정`.

## 3. False positives and intended policies

Each row is counted once and is not duplicated in the unverified section.

| # | Candidate | Decision | Evidence |
| ---: | --- | --- | --- |
| 1 | service ADMIN has no campus membership | intended policy | approved global override; active service ADMIN is checked before access |
| 2 | service MANAGER can manage a campus | false positive | MANAGER alone only creates campuses; all later management requires campus role or ADMIN |
| 3 | anonymous Poll stores `poll_responses.user_id` | intended defense | result service creates an empty user map and empty respondent lists for anonymous Polls |
| 4 | all ACTIVE members read all prayer groups | intended product policy | board read is campus-wide; group/self/manager write guards remain separate |
| 5 | normal prayer group submission body contains another `userId` | intended policy | non-managers are limited to target users in the requester's current active group |
| 6 | account numbers are visible to ACTIVE campus members | intended product policy | bank transfer requires the number; admin-only metadata is separated by response surface |
| 7 | explicit notification targets can contain other-campus user IDs | defended | target IDs are intersected with ACTIVE members of the path campus before any log/send |
| 8 | FCM token can move between users | intended defense | prior active ownership is deactivated to prevent shared-device cross-account delivery |

## 4. Unverified and policy-dependent items

These are not confirmed findings and are not counted as false positives.

| ID | Item | Repository evidence | Missing evidence / decision |
| --- | --- | --- | --- |
| U-159-01 | 403 versus 404 existence hiding is inconsistent across ID-based APIs | Poll/template/comment parent mismatch and FCM owner mismatch use 404; charge owner, account owner, and prayer cross-campus manager failures can return 403 | no approved global object-existence policy; responses reveal at most object existence in the reviewed paths, not object fields |
| U-159-02 | application tenant guards are the only demonstrated isolation layer | service/repository predicates consistently include campus/parent/owner for reviewed endpoints | Supabase RLS, DB role privileges, and protection against direct database access are outside the repository evidence |
| U-159-03 | database corruption/orphan rows could affect parent-only child queries | option/comment/submission children are loaded after a scoped parent and by parent ID | clean Flyway FK state is documented, but production constraint health and historical data were not inspected |
| U-159-04 | identifier enumeration practicality | IDs are numeric and some endpoints distinguish absent from forbidden | no approved dynamic test, production dataset, rate-limit evidence, or demonstrated disclosure beyond existence; no active scan was run |

## 5. Missing regression tests and follow-up candidates

No test code is added in this audit.

| Priority | Candidate | Reason |
| --- | --- | --- |
| Medium | `[Security] COFFEE duty Poll template update authorization target 고정` | F-159-01 confirmed; one function boundary |
| Test gap | cross-type template update by duty user | existing COFFEE-template owner tests do not exercise a persisted non-COFFEE target |
| Test gap | uniform 403/404 object-existence contract | depends on PM approval of a cross-domain hiding policy |
| Ops | Supabase tenant/RLS and constraint verification | repository-only audit cannot verify direct DB access controls |

No Issue is created before PM approval.

## 6. OWASP mapping

| Standard | Audit result |
| --- | --- |
| API1:2023 BOLA | 17 identifier categories traced through parent/tenant/owner; no confirmed cross-campus record read; F-159-01 includes target-object authorization weakness |
| API3:2023 BOPLA | anonymous respondent identity and account member/admin DTO separation verified |
| API5:2023 BFLA | F-159-01 confirmed; service/campus role and duty matrix otherwise matched approved policy |
| API6:2023 Sensitive Business Flows | billing payment, template schedule, prayer writes, notification targets, and self APIs traced; predecessor auth findings not duplicated |
| API8:2023 Security Misconfiguration | database-side tenant enforcement remains U-159-02, not a repository finding |
| API9:2023 Inventory Management | 21 Controllers / 80 endpoints and 17 identifier categories counted |
| ASVS V2 | request body must not redefine the privilege class of an existing object; F-159-01 |
| ASVS V8 | campus, parent, owner, role hierarchy, and function-level authorization traced |
| ASVS V14 | account, prayer, notification, and anonymous Poll data exposure boundaries reviewed |

## 7. Verification and scope integrity

Focused command reran 13 existing classes across admin, campus, billing, devotion, Poll, prayer,
notification, and FCM.

Result: **172 tests / 0 failures / 0 errors / 0 skipped**, `BUILD SUCCESSFUL`.

- Docker: not used
- Production/test/config/database/Flyway changes: 0
- Secret/token/personal data/account number/prayer content values recorded: 0
- F-157-01 duplicates: 0
- F-158-01 duplicates: 0
- Push/PR/fix Issue: not performed

## 8. Audit limitations and disclaimer

This audit is based on repository static analysis and safe existing focused tests. It did not inspect
production credentials, cloud consoles, direct database access, mobile storage, deployed images, or
live network behavior, and it did not perform an aggressive scan. AI-assisted review does not replace
an independent professional penetration test. A production service handling personal, account, and
prayer data should receive periodic expert review and explicitly authorized dynamic testing.
