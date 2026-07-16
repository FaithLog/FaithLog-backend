import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');

function readRequired(relativePath) {
	const target = path.join(ISSUE_DIR, relativePath);
	assert.equal(fs.existsSync(target), true, `required Issue #197 scenario file is missing: ${relativePath}`);
	return fs.readFileSync(target, 'utf8');
}

test('devotion write scenario isolates 1,000 measured users from warmup and rollback cohorts', () => {
	const script = readRequired('devotion-write.js');

	assert.match(script, /shared-iterations/);
	assert.match(script, /measuredUserIds/);
	assert.match(script, /warmupUserIds/);
	assert.match(script, /rollbackUserIds/);
	assert.match(script, /expectedMeasuredUserCount/);
	assert.match(script, /1000|1_000/);
	assert.match(script, /warmupWeekStartDate/);
	assert.match(script, /measuredWeekStartDate/);
	assert.match(script, /rollbackWeekStartDate/);
	assert.match(script, /fixtureRunId/);
	assert.match(script, /datasetId/);
	assert.match(script, /seoulToday/);
});

test('devotion write scenario measures only the weekly submit and locks response correctness', () => {
	const script = readRequired('devotion-write.js');

	assert.match(script, /\/api\/v1\/campuses\/\$\{[^}]+\}\/devotions\/me\/weeks\/\$\{[^}]+\}/);
	assert.match(script, /dailyChecks/);
	assert.match(script, /submit:\s*true/);
	assert.match(script, /devotion_weekly_warmup/);
	assert.match(script, /devotion_weekly_measured/);
	assert.match(script, /devotion_weekly_rollback/);
	assert.match(script, /p\(50\)/);
	assert.match(script, /p\(95\)/);
	assert.match(script, /p\(99\)/);
	assert.match(script, /max/);
	assert.match(script, /BILLING_REQUIRED_PAYMENT_ACCOUNT_MISSING/);
});

test('runtime credentials and fixture metadata are separate contracts', () => {
	const schema = JSON.parse(readRequired('fixture-manifest.schema.json'));
	const helper = readRequired('lib/fixture-contract.mjs');

	assert.equal(schema.properties.datasetId.pattern, '^PERFORMANCE_');
	assert.equal(schema.properties.fixtureRunId.pattern, '^ISSUE197_');
	assert.equal(schema.properties.measuredUserIds.minItems, 1000);
	assert.equal(schema.properties.measuredUserIds.maxItems, 1000);
	assert.match(helper, /CREDENTIALS_FILE/);
	assert.match(helper, /accessToken/);
	assert.doesNotMatch(JSON.stringify(schema), /password|accessToken|refreshToken/i);
});

test('devotion runner takes a common lock, records real Compose labels, samples resources, and verifies rows', () => {
	const runner = readRequired('run-devotion-baseline.sh');

	assert.match(runner, /faithlog-performance-\$\{[^}]*compose[^}]*\}\.lock/i);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /com\.docker\.compose\.service/);
	assert.match(runner, /EXPECTED_COMPOSE_PROJECT/);
	assert.doesNotMatch(runner, /ATTRIBUTION_SIGNATURE_FILE|freeze-signature|approved-activity-signature|ACTIVITY_SIGNATURE_SHA256/);
	assert.match(runner, /runtime-observed-supporting-only/);
	assert.match(runner, /SPRING_PROFILES_ACTIVE=docker/);
	assert.match(runner, /SPRING_DATASOURCE_URL=jdbc:postgresql:\/\/\$EXPECTED_DB_COMPOSE_SERVICE:\$EXPECTED_DB_PORT\/\$DB_NAME/);
	assert.match(runner, /SPRING_DATASOURCE_USERNAME=\$DB_USER/);
	assert.match(runner, /docker stats/);
	assert.match(runner, /:\s*>\s*"\$stats_file"/);
	assert.match(runner, /issue-197/);
	assert.match(runner, /devotion-write\.js/);
	assert.match(runner, /verify-devotion\.sql/);
	assert.match(runner, /measured-direct-cardinality\.json/);
	assert.match(runner, /validate-devotion-cardinality\.mjs/);
	assert.match(runner, /measuredCardinalityAfter/);
	assert.match(runner, /preflight-devotion\.sql/);
	assert.match(runner, /scenario-contract\.mjs/);
	assert.doesNotMatch(runner, /\$\{REPORT_ROOT:-/);
	assert.doesNotMatch(runner, /WARMUP_VUS:-|MEASURED_VUS:-|ROLLBACK_VUS:-|MAX_DURATION\s*:-/);
	assert.ok(runner.indexOf('PHASE=warmup') < runner.indexOf('sample_stats &'));
	assert.ok(runner.indexOf("STATS_PID=''", runner.indexOf('sample_stats &')) < runner.indexOf('PHASE=rollback'));
	assert.doesNotMatch(runner, /docker\s+compose\s+(up|down|build)|docker\s+(run|rm)|prune/);
});

test('devotion preflight validates exact cohort, account, freshness, and penalty-rule evidence before writes', () => {
	const sql = readRequired('preflight-devotion.sql');
	const runner = readRequired('run-devotion-baseline.sh');
	const validator = readRequired('lib/validate-devotion-preflight.mjs');

	assert.match(sql, /payment_accounts/);
	assert.match(sql, /account_type\s*=\s*'PENALTY'/);
	assert.match(sql, /is_active\s*=\s*TRUE/i);
	assert.ok((sql.match(/deleted_at\s+IS\s+NULL/gi) || []).length >= 2);
	assert.match(validator, /successActivePenaltyAccounts/);
	assert.match(validator, /rollbackActivePenaltyAccounts/);
	assert.match(validator, /existingDevotionCharges/);
	assert.match(sql, /campus_members/);
	assert.match(sql, /penalty_rules/);
	assert.match(sql, /calculatedPenaltyAmount/);
	assert.match(sql, /existingWeeklyCount/);
	assert.match(sql, /existingDailyCount/);
	assert.ok(runner.indexOf('preflight-devotion.sql') < runner.indexOf('PHASE=warmup'));
	assert.doesNotMatch(sql, /\b(DELETE|UPDATE|INSERT|TRUNCATE|ALTER|DROP)\b/i);
});

test('devotion SQL evidence locks weekly, seven daily, charge amount/source uniqueness, and rollback counts', () => {
	const sql = readRequired('verify-devotion.sql');

	assert.match(sql, /weekly_devotion_records/);
	assert.match(sql, /devotion_daily_checks/);
	assert.match(sql, /charge_items/);
	assert.match(sql, /DEVOTION_RECORD/);
	assert.match(sql, /PENALTY/);
	assert.match(sql, /expected_penalty_amount/);
	assert.match(sql, /daily_count\s*=\s*7/i);
	assert.match(sql, /distinctWeeklyUsers/);
	assert.match(sql, /distinctDailyUsers/);
	assert.match(sql, /correctDailyDateCount/);
	assert.match(sql, /distinctChargeUsers/);
	assert.match(sql, /correctChargeBindingCount/);
	assert.match(sql, /chargeAmountSum/);
	assert.match(sql, /successCampusDevotionChargeCount/);
	assert.match(sql, /rollback/i);
	assert.match(sql, /JOIN rollback_users fixture_user ON fixture_user\.user_id = charge\.user_id/);
	assert.doesNotMatch(sql, /JOIN rollback_weekly weekly ON weekly\.id = charge\.source_id/);
	assert.doesNotMatch(sql, /\b(DELETE|UPDATE|INSERT|TRUNCATE|ALTER|DROP)\b/i);
});

test('retention runner is isolated-only, refuses shared Compose, and remains dry verification only', () => {
	const runner = readRequired('run-retention-dry-verify.sh');
	const sql = readRequired('retention-dry-verify.sql');

	assert.match(runner, /ALLOW_ISOLATED_RETENTION/);
	assert.match(runner, /faithlog-perf-197-/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /dry-verify-only/);
	assert.match(runner, /faithlog-performance-\$\{[^}]*compose[^}]*\}\.lock/i);
	assert.doesNotMatch(runner, /FaithLogScheduledJobs|cleanupDueData|cleanupDaily|cleanupAnnualIfDue/);
	assert.doesNotMatch(runner, /docker\s+compose\s+(up|down|build)|docker\s+(run|rm)|prune/);
	assert.match(sql, /notification_logs/);
	assert.match(sql, /polls/);
	assert.match(sql, /prayer_submissions/);
	assert.match(sql, /devotion_daily_checks/);
	assert.match(sql, /weekly_devotion_records/);
	assert.match(sql, /charge_items/);
	assert.match(sql, /fixture_run_id/);
	assert.match(sql, /outsideFixtureCandidateRoots/);
	assert.match(sql, /left\s*\(/i);
	assert.doesNotMatch(sql, /\bLIKE\b/i);
	assert.match(sql, /annualForeignKeyBlockers/);
	assert.doesNotMatch(sql, /\b(DELETE|UPDATE|INSERT|TRUNCATE|ALTER|DROP)\b/i);
});

test('retention verifier requires exact expected delete counters and emits not-measured evidence', () => {
	const verifier = readRequired('retention-dry-verify.mjs');

	assert.match(verifier, /expectedDeleteCounts/);
	assert.match(verifier, /actualCandidateCounts/);
	assert.match(verifier, /outsideFixtureCandidateRoots/);
	assert.match(verifier, /annualForeignKeyBlockers/);
	assert.match(verifier, /scenario-ready/);
	assert.match(verifier, /not-measured/);
	assert.match(verifier, /cleanupBatchEvidence/);
	assert.match(verifier, /p50/);
	assert.match(verifier, /p95/);
	assert.match(verifier, /p99/);
	assert.match(verifier, /throughput/);
	assert.match(verifier, /failureRate/);
});

test('devotion evidence requires every measured and rollback transaction attempt', () => {
	const contract = readRequired('lib/scenario-contract.mjs');

	assert.match(contract, /measured transaction attempts/);
	assert.match(contract, /rollback transaction attempts/);
	assert.match(contract, /conditional-not-adoptable/);
	assert.match(contract, /automaticAdoption:\s*false/);
	assert.match(contract, /measuredDirectCardinalityEvidence/);
	assert.match(contract, /correctChargeBindingCount/);
	assert.match(contract, /chargeAmountSum/);
});

test('measured window records pure database, table, planner, activity, and optional query counters', () => {
	const runner = readRequired('run-devotion-baseline.sh');
	const sql = readRequired('collect-db-counters.sql');
	const validator = readRequired('lib/validate-db-window.mjs');

	assert.match(runner, /db-counters-before/);
	assert.match(runner, /db-counters-after/);
	assert.doesNotMatch(runner, /db-counters-warmup-before/);
	assert.match(runner, /validate-db-window\.mjs/);
	assert.doesNotMatch(runner, /validate-activity-attribution\.mjs/);
	assert.match(sql, /pg_stat_database/);
	assert.match(sql, /pg_stat_user_tables/);
	assert.match(sql, /pg_stat_activity/);
	assert.match(sql, /all_database_counters/);
	assert.match(sql, /externalActiveSessionsAllDatabases/);
	assert.match(sql, /pg_settings/);
	assert.match(sql, /stats_reset/);
	assert.match(sql, /plan_cache_mode/);
	assert.match(sql, /pg_stat_statements/);
	assert.match(sql, /campuses/);
	assert.match(sql, /last_autoanalyze/);
	assert.match(sql, /n_mod_since_analyze/);
	assert.match(validator, /externalActiveSessions/);
	assert.match(validator, /databaseInstanceDelta/);
	assert.match(validator, /autoanalyze_count/);
	assert.match(validator, /supporting-clean/);
	assert.doesNotMatch(sql, /\b(DELETE|UPDATE|INSERT|TRUNCATE|ALTER|DROP|RESET|CREATE)\b/i);
});

test('README keeps write and retention reports separate and documents all pending evidence', () => {
	const readme = readRequired('README.md');

	assert.match(readme, /build\/reports\/k6\/issue-197\/.*devotion/);
	assert.match(readme, /build\/reports\/k6\/issue-197\/.*retention/);
	assert.match(readme, /warmup/i);
	assert.match(readme, /measured/i);
	assert.match(readme, /p50.*p95.*p99.*max/is);
	assert.match(readme, /throughput/i);
	assert.match(readme, /failure/i);
	assert.match(readme, /CPU.*RAM/is);
	assert.match(readme, /query.*counter|SQL.*call/is);
	assert.match(readme, /FCM/i);
	assert.match(readme, /scenario-ready/i);
	assert.match(readme, /not-measured/i);
});
