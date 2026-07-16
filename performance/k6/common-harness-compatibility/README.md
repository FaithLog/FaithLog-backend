# Issue #208 common performance harness compatibility audit

This read-only audit inventories issue #192–#199 tooling before any actual load. It never runs HTTP, Docker lifecycle, fixtures, database writes, or EXPLAIN. Reports are supporting evidence only (`automaticAdoption=false`) and remain `scenario-ready/not-measured`.

All eight `ISSUE_19x_WORKTREE` variables and an absolute, nonexistent `AUDIT_REPORT_ROOT` are runtime-required. There are no target or workload fallbacks. The first `audit-report.json` is exclusively created with mode `0600`; any compatibility finding sets `actualLoadBlocked=true`.

The report groups findings by JSON initialization, k6 v2 metric math, token serialization/TTL, Docker units and identity, psql machine output, report freshness, DB attribution, runtime continuity, and macOS/k6 v2 portability. Every failure includes issue, file, line, counterexample, and recommended patch. N/A is limited to a cited workload-scope file and line: #194 is EXPLAIN-only and #198 is a local Gradle notification harness.

Installed-k6 verification is restricted to `k6 version`, `k6 inspect`, and the no-HTTP synthetic serialization fixture. The report preserves the fixture hash, command-output hashes, sentinel absence, and HTTP sample count without raw output. Target checks use only allowlisted `node --test <contained-relative-file>` commands with a 60-second timeout; exit status and output hashes are recorded without environment values or raw output. Initial/final HEAD and status hashes must remain identical and clean. Actual issue workloads remain blocked until the generated report says otherwise and PM performs the sequential measurement gate.
