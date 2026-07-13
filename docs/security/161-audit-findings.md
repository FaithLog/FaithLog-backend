# Issue #161 deployment infrastructure and supply-chain security audit findings

## 1. Scope and conclusion

This is a read-only audit of baseline `f3e81fb9b3c2afbc4ad9342eb6cf6bf55e19c553` on 2026-07-13. It covers
the current tree, Git history, Gradle metadata, GitHub Actions and repository settings visible through the API,
Docker build structure, deployment contracts, and the PostgreSQL/Redis/Firebase runtime adapters listed in
[`161-deployment-supply-chain-matrix.md`](161-deployment-supply-chain-matrix.md).

No production, test, configuration, database, Flyway, Docker image, or runtime infrastructure was changed. No
live Cloud Run endpoint, production database, Redis instance, Firebase project, or credential was used.

| Result class | Count | Severity breakdown |
| --- | ---: | --- |
| confirmed | 2 | High 1, Medium 1 |
| false positive / intentional policy | 12 | not findings |
| unverified / console-dependent | 14 | not confirmed |
| new hardening candidates | 2 | Low 2 |
| duplicate predecessor findings | 7 | excluded from #161 totals |

The most urgent candidate is upgrading the affected Spring Security line. The branch-protection gap is a
separate repository-integrity issue. Both require PM approval before a behavior or repository-policy change;
this audit creates no follow-up Issue.

## 2. Method and confidence rule

- Confirmed requires confidence at least 8/10, repository or authenticated GitHub evidence, and a stated exploit
  or failure path.
- Minimum confirmed impact is separated from conditional maximum impact.
- Missing hardening without a direct exploit path is not promoted to a vulnerability.
- Known #157-#160 and #176/#179/#182/#183 findings and fixes are referenced but not duplicated.
- Advisory matching uses official vendor documentation. The available dependency graph was resolved safely,
  but no new scanner/plugin/dependency was installed.
- Secret scans record only counts, classifications, file paths, and commit counts. Matching values are never
  printed or documented.

## 3. Confirmed findings

### F-161-01 — affected Spring Security response-header implementation is deployed

| Field | Result |
| --- | --- |
| Severity | **High** (vendor severity Critical; FaithLog impact narrowed below) |
| Confidence | **9/10** |
| Status | confirmed affected component and configuration; cache-mediated data disclosure remains conditional |
| Mapping | CWE-693, CWE-525; OWASP Top 10 A06:2021; OWASP API8:2023; ASVS V14 configuration |
| Supply-chain class | vulnerable transitive runtime dependency |

**Evidence**

- `build.gradle.kts:38` includes `spring-boot-starter-security` under Spring Boot 3.5.0.
- The resolved runtime graph contains `spring-security-config`, `spring-security-core`, and
  `spring-security-web` **6.5.0**.
- `SecurityConfig.java:32-56` builds a servlet `SecurityFilterChain` and does not set
  `HeaderWriterFilter.shouldWriteHeadersEagerly=true`; therefore it uses the affected default lazy header mode.
- Spring's official [CVE-2026-22732 advisory](https://spring.io/security/cve-2026-22732/) lists servlet
  applications using the default lazy header mode on 6.5.0-6.5.8 as affected and fixes the OSS line in 6.5.9.
- The upstream fix explains that an untracked `Content-Length` set through response-header methods can commit the
  body before the security headers are written.

**Reproduction prerequisite and path**

1. A request traverses the FaithLog servlet security chain.
2. Spring MVC, an exception path, or the container sets `Content-Length` through an affected response method and
   commits the response before the lazy writer runs.
3. The expected Spring Security headers, including cache-control defenses, are omitted.
4. Data disclosure additionally requires a browser, proxy, CDN, or other cache boundary to retain and expose a
   response that should have been non-cacheable.

FaithLog has a direct servlet-response writer at `RestAuthenticationEntryPoint.java:23-35`, but its body is a
generic 401 response. That path supports reachability of the affected filter mechanics; it does not by itself
prove disclosure of sensitive user data. No live endpoint was probed.

**Impact**

- Minimum confirmed: the production runtime carries the vendor-confirmed affected module and default mode, so a
  qualifying committed response can omit expected security headers.
- Conditional maximum: a cache boundary can retain an authenticated response and disclose account, campus,
  billing, devotion, prayer, or notification data, or weaken other header-based browser protections.

**Current defenses**

- FaithLog is primarily a JSON mobile backend, not a browser-rendered HTML application.
- Cloud Run provides managed HTTPS, and Spring Security normally supplies no-store, nosniff, HSTS-on-HTTPS, and
  frame-deny defaults.
- No repository evidence shows a CDN/shared response cache in front of Cloud Run.

**Recommended correction — separate High follow-up candidate**

After PM approval, update the Spring Security line to at least 6.5.11 or a Spring Boot maintenance BOM that
resolves to it, then verify authentication, default response headers, and the full test/build gate. The 6.5.11
floor also avoids later 6.5.0-6.5.10 advisories; an explicit eager-header workaround changes application
behavior and must not be selected without a PM decision. Do not change only the transitive module silently.

### F-161-02 — `main` and `develop` have no enforced repository protection

| Field | Result |
| --- | --- |
| Severity | **Medium** |
| Confidence | **10/10** |
| Status | confirmed through authenticated GitHub repository API |
| Mapping | CWE-284, CWE-862; OWASP Top 10 A08:2021; SLSA source integrity |
| Supply-chain class | source/release branch integrity |

**Evidence**

- The classic branch-protection API returned `404 Branch not protected` for both `main` and `develop`.
- The repository rulesets API returned an empty list.
- Repository Actions settings allow all actions and do not require SHA pinning.
- The default workflow token permission is read-only and cannot approve pull requests, which limits CI token
  impact but does not prevent a human or compromised write credential from pushing directly.
- `.github/workflows/ci.yml:3-14` runs checks on pushes and pull requests, but a workflow run is not an enforced
  merge gate without branch protection or rulesets.

**Reproduction prerequisite and path**

1. An attacker obtains, abuses, or already has a credential with repository write permission.
2. The credential pushes directly to `develop` or `main`, or force-updates/deletes a branch if the actor's role
   permits it.
3. No repository rule requires a pull request, review, or successful CI result before the reference changes.

**Impact**

- Minimum confirmed: unreviewed or failing code can be written directly to integration and release branches.
- Conditional maximum: if an external Cloud Build trigger deploys either branch automatically, the same path can
  promote a compromised artifact to Cloud Run. The trigger source and approval policy are console-unverified, so
  production deployment is not claimed as confirmed impact.

**Current defenses**

- The team follows a documented Issue/PR/review workflow voluntarily.
- CI has read-only contents permission and PR #185 passed all repository checks before merge.
- No GitHub Actions CD workflow exists in the current tree.

**Recommended correction — separate Medium follow-up candidate**

After PM approval, create a repository ruleset for `main` and `develop` requiring pull requests and the exact
required checks, and explicitly decide review count, stale-review dismissal, force-push/deletion blocks, merge
method, and administrator/bypass actors. Confirm external deployment trigger behavior before choosing different
rules per branch.

## 4. Low hardening candidates — not confirmed vulnerabilities

### H-161-01 — Gradle inputs are versioned but not cryptographically verified end to end

- Severity: Low hardening; confidence 10/10.
- Evidence: wrapper 8.14.5 and direct versions are exact, and the local wrapper JAR checksum matches Gradle's
  published checksum. `gradle-wrapper.properties` has no `distributionSha256Sum`; dependency locking and
  `gradle/verification-metadata.xml` are absent.
- Minimum impact today: build reproducibility and artifact-substitution detection are weaker; no malicious
  artifact was found.
- Conditional maximum: an upstream repository, DNS/TLS trust, or reused coordinate compromise could substitute a
  Gradle distribution or dependency and execute code in CI/build context.
- Recommendation: after approval, add the official wrapper distribution checksum, dependency verification
  metadata, and an intentional locking/update workflow. Keep version updates operable rather than freezing them
  without a maintenance process.

### H-161-02 — builder and runtime base images use mutable tags

- Severity: Low hardening; confidence 10/10.
- Evidence: `Dockerfile:1,12` uses versioned Temurin Alpine tags without OCI digests.
- Current defense: multi-stage build; the runtime stage receives only the boot JAR. `.dockerignore` excludes
  secrets, build output, repository metadata, and local agent data.
- Conditional maximum: registry compromise or tag replacement changes the toolchain/runtime without a source
  diff, potentially altering the produced or deployed artifact.
- Recommendation: after approval, adopt a digest refresh policy for both stages and retain readable version tags
  in update tooling/documentation. Do not pin once and abandon security updates.

## 5. False positives and intentional policies (12)

| # | Candidate | Disposition and evidence |
| ---: | --- | --- |
| 1 | current/history secret exposure | false positive: 0 high-signal current files, 0 matching history commits, 0 non-example sensitive-path commits |
| 2 | CI test credentials | intentional dummy fixture: values exist only in the `test` profile/workflow and were not classified as production credentials |
| 3 | public Cloud Run service | intentional architecture from #46/#157; public transport is separated from JWT-protected application authorization |
| 4 | CSRF disabled | intentional stateless bearer-token API policy; no cookie/session authentication is configured |
| 5 | no explicit CORS policy | not an authentication bypass; mobile clients are outside browser CORS enforcement and all protected endpoints still require JWT |
| 6 | actuator `info` anonymously exposed | false positive: config exposes health/info, but `SecurityConfig` anonymously permits only `/actuator/health`; other paths require authentication |
| 7 | source/tests/Gradle cache in runtime image | false positive: multi-stage runtime copy includes only `/app/build/libs/*.jar` |
| 8 | Compose credentials imply production secret | false positive: Compose is the documented local/docker boundary and uses dummy development contracts |
| 9 | raw JWT/refresh token stored in Redis | false positive already established by #158/#176; Redis keys store opaque/hashed identifiers and revocation state, not raw tokens |
| 10 | Redis outage causes auth/notification fail-open | false positive: #158/#176 auth and notification dedup/lock adapters propagate failures and preserve approved fail-closed behavior |
| 11 | Firebase credential failure is silently ignored | false positive: `FirebaseAdminFcmConfig` requires credentials outside local/docker/test and fails startup; post-start send failures are isolated in the async delivery worker |
| 12 | current Spring advisories requiring SAML/X.509/OTT/Querydsl/ProjectedPayload/SpEL input are exploitable | false positive for current code paths: those feature classes/configurations are absent; vulnerable-version maintenance is still addressed by F-161-01's upgrade candidate |

## 6. Duplicate findings excluded from #161 counts (7)

| Existing item | Treatment in #161 |
| --- | --- |
| #157 F-157-01 last active ADMIN withdrawal | fixed outside this audit; no deployment duplicate |
| #158 F-158-01 refresh rotation race | fixed by #176; fail-closed behavior only rechecked |
| #159 F-159-01 request-body template authorization | fixed by #179; no deployment duplicate |
| #160 F-160-01 devotion/charge range integrity | fixed by #182; Flyway V7 counted only |
| #160 F-160-02 client COFFEE price authority | fixed by #183; baseline CI evidence only |
| #157 H-157-01/H-157-02/H-157-03 | Docker non-root, action SHA pinning, and secret scanner remain predecessor hardening items, not new #161 findings |
| #160 U-160-03 raw Firebase provider error persistence | remains predecessor unverified item; notification source-of-truth was not recounted |

## 7. Unverified and console-dependent items (14)

Confidence below 8/10 or missing console evidence prevents these items from being promoted to confirmed.

| ID | Console or external evidence required | Repository evidence available |
| --- | --- | --- |
| U-161-01 | collaborator/team roles, deploy keys, GitHub App permissions, bypass actors | branch/ruleset absence is confirmed separately |
| U-161-02 | external Cloud Build trigger branch/event, approval, substitutions, build service account | current tree has no GitHub CD or `cloudbuild.yaml`; historical GitHub CD was removed |
| U-161-03 | Cloud Run runtime service account and least-privilege IAM | deployment document leaves service/project/region undecided |
| U-161-04 | Secret Manager versions, rotation, accessor scope, and data-access audit logs | secret names are env contracts only; no values are tracked |
| U-161-05 | Cloud Run region, CPU, memory, concurrency, min/max instances, and timeout | no repository IaC defines them |
| U-161-06 | Cloud Run ingress, VPC connector, egress, Cloud Armor, geo controls, CDN/cache | no repository IaC or live probe evidence |
| U-161-07 | live production profile, SpringDoc disablement, actuator exposure, response headers, CORS/upstream headers | production template defaults SpringDoc off and permits only health anonymously |
| U-161-08 | Supabase project SSL enforcement and live JDBC TLS mode | deployment contract says use required SSL, but URL structure cannot prove enforcement |
| U-161-09 | Supabase direct/pooler selection, application/migration DB roles, network restrictions, connection limits | repository defaults pool size 5 and separates credentials |
| U-161-10 | Upstash live TLS, ACL, IP restrictions, credential rotation, audit/usage alerts | production template enables SSL and separates password |
| U-161-11 | Firebase service account IAM, key age/rotation, API scope, quotas and alerting | JSON secret injection is preferred; no key is tracked |
| U-161-12 | GitHub Actions log/artifact/cache retention and access policy | current scripts do not echo secrets; artifact path is test reports only |
| U-161-13 | complete dependency graph and alert coverage | 0 open Dependabot alerts, but repository SBOM endpoint returned 404 and no exhaustive scanner was available |
| U-161-14 | Artifact Registry vulnerability scanning, retention, immutable tags/digests, provenance, signing/Binary Authorization | deployment contract uses a git/release image tag template only |

## 8. Operations console verification checklist

No item below authorizes a configuration change. Capture settings and return them to the PM for a decision.

### GitHub

- [ ] Export repository collaborators, teams, deploy keys, GitHub Apps, and bypass actors.
- [ ] Confirm the desired ruleset for `develop` and `main`, exact required check names, force-push/deletion policy,
  administrator bypass, Dependabot/fork behavior, and merge method.
- [ ] Review Actions allowlist, artifact/log/cache retention, secret scanning, push protection, Dependabot graph,
  alert dismissal policy, and organization audit log.

### Cloud Build, Artifact Registry, and Cloud Run

- [ ] Export the single authoritative deployment trigger, source branch/event, substitutions, approval gate,
  service account, logs, and whether any duplicate trigger exists.
- [ ] Confirm image digest promoted to Cloud Run, registry vulnerability scan, cleanup/immutability, provenance,
  signing, and Binary Authorization policy.
- [ ] Export Cloud Run region, runtime service account, public invoker grant, ingress, VPC/egress, CPU, memory,
  concurrency, min/max instances, timeout, revision traffic, startup/health probes, and audit logs.
- [ ] Confirm Secret Manager version pinning, least-privilege accessor bindings, rotation ownership, and data-access
  logging. Do not paste secret values into the evidence.
- [ ] Confirm upstream cache/CDN/Cloud Armor/geo policy and verify that authenticated responses are not cached.

### Supabase PostgreSQL and Upstash Redis

- [ ] Confirm Supabase SSL enforcement, actual connection mode, migration/application role separation, network
  restrictions, pool limits, backups/PITR, audit logs, and credential rotation.
- [ ] Confirm Upstash TLS, ACL scope, IP restrictions if available, eviction/max-memory behavior, rotation, latency,
  error/rate alerts, and region/data-residency choice.

### Firebase

- [ ] Confirm Admin service-account IAM, key age and rotation, API enablement, quota/error alerts, invalid-token
  metrics, and that only Secret Manager injects production credentials.
- [ ] Review `notification_logs` and `user_fcm_tokens` operational retention/access controls without reading or
  exporting token or personal-data values for this audit.

## 9. Validation and limitations

| Check | Result |
| --- | --- |
| baseline | `origin/develop` at required `f3e81fb9...` |
| Issue/Project | Issue #161 open; no Project card was attached, so no `In Progress` transition was possible |
| PR prerequisite | PR #185 merged to `develop`; all three repository checks succeeded or were intentionally skipped as configured |
| dependency metadata | runtime graph resolved: 15 first-level rows, 208 unique modules, 0 unresolved |
| secret scan | 0 high-signal current/history candidates; no value printed or recorded |
| GitHub protection | both target branches unprotected; repository rulesets 0 |
| Dependabot | open alerts 0; SBOM/coverage unverified |
| focused tests | attempted 7 existing classes; 0 tests executed because Gradle Plugin Portal could not resolve the already-declared Spring Boot 3.5.0 plugin in this environment |
| baseline test evidence | PR #185 checks succeeded; repository record reports 399 tests, 0 failures, 0 errors, 3 skipped at this exact baseline |
| Docker/live services | not used; no live smoke, load, or credential validation |
| code/config/schema changes | 0 production, 0 test, 0 config, 0 DB, 0 Flyway, 0 infrastructure |

The failed focused command changed no source/configuration and produced no new XML result, so no test count is
inferred from stale files. This audit is a point-in-time assessment, not a guarantee that no vulnerability exists.
It did not inspect private cloud/provider consoles, organization-level policy, runtime traffic, production data,
or a complete third-party vulnerability database.

## 10. Official references

- [Spring CVE-2026-22732](https://spring.io/security/cve-2026-22732/)
- [Spring CVE-2026-41003](https://spring.io/security/cve-2026-41003/)
- [Spring Security default response headers](https://docs.spring.io/spring-security/reference/features/exploits/headers.html)
- [Gradle wrapper verification](https://docs.gradle.org/current/userguide/gradle_wrapper.html#sec:verification)
- [Gradle dependency verification](https://docs.gradle.org/current/userguide/dependency_verification.html)
- [Gradle dependency locking](https://docs.gradle.org/current/userguide/dependency_locking.html)
- [GitHub protected branches](https://docs.github.com/en/repositories/configuring-branches-and-merges-in-your-repository/managing-protected-branches/about-protected-branches)
- [GitHub secure use of third-party actions](https://docs.github.com/en/actions/security-for-github-actions/security-guides/security-hardening-for-github-actions)
- [Cloud Run container contract](https://cloud.google.com/run/docs/container-contract)
- [Cloud Run service identity](https://cloud.google.com/run/docs/securing/service-identity)
- [Cloud Run Secret Manager integration](https://cloud.google.com/run/docs/configuring/services/secrets)
- [Supabase database SSL enforcement](https://supabase.com/docs/guides/platform/ssl-enforcement)
- [Upstash Redis TLS](https://upstash.com/docs/redis/features/encryption)
- [Firebase Admin credential setup](https://firebase.google.com/docs/admin/setup)
