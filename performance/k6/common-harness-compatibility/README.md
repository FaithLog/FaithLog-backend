# Issue #208 common performance harness compatibility audit

This read-only audit inventories issue #192–#199 tooling before any actual load. It never runs HTTP, Docker lifecycle, fixtures, database writes, or EXPLAIN. Reports are supporting evidence only (`automaticAdoption=false`) and remain `scenario-ready/not-measured`.

All eight `ISSUE_19x_WORKTREE` variables and an absolute, nonexistent `AUDIT_REPORT_ROOT` are runtime-required. There are no target or workload fallbacks. The first `audit-report.json` is exclusively created with mode `0600`; any compatibility finding sets `actualLoadBlocked=true`.

The report groups findings by JSON initialization, k6 v2 metric math, token serialization/TTL, Docker units and identity, psql machine output, report freshness, DB attribution, runtime continuity, and macOS/k6 v2 portability. Every failure includes issue, file, line, counterexample, and recommended patch. N/A is limited to a cited workload-scope file and line: #194 is EXPLAIN-only and #198 is a local Gradle notification harness.

Installed-k6 verification is restricted to `k6 version`, `k6 inspect`, and the no-HTTP synthetic serialization fixture. The report preserves the fixture hash, command-output hashes, sentinel absence, and HTTP sample count without raw output. Target checks use only allowlisted `node --test <contained-relative-file>` commands with a 60-second timeout; exit status and output hashes are recorded without environment values or raw output. Initial/final HEAD and status hashes must remain identical and clean. Actual issue workloads remain blocked until the generated report says otherwise and PM performs the sequential measurement gate.

## Final integrated audit

The final audit ran against the integrated `origin/develop` tree at `7b96a539bd1bb003649332e41cf7258734392ccb`.

- All applicable cells passed for issues #192 through #199.
- The k6 HTTP-only cells for #194 and #198 are explicitly N/A because those workloads are respectively PostgreSQL EXPLAIN-only and a local Gradle notification harness.
- Installed `k6 v2.0.0` passed module inspection and the no-HTTP serialization probe with `httpSamples=0` and no serialized sentinel token.
- Every target remained clean and on the same HEAD throughout the audit.
- `patchQueue=[]`, `actualLoadBlocked=false`, and the final status is `scenario-ready-not-measured`.

The only integrated compatibility defect found was in #195's test contract: it compared an immutable historical before-source revision with the moving `origin/develop` ref. The test now verifies the preserved before revision directly. No historical report or source identity was rewritten.

This audit did not run an actual performance load and does not create a latency, throughput, capacity, or improvement claim. Its evidence is supporting-only and `automaticAdoption=false`.
