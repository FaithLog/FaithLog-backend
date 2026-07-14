import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);
const JAVA_HARNESS = new URL(
	'../../src/test/java/com/faithlog/performance/notification/NotificationBatchBeforeScenarioTest.java',
	import.meta.url,
);

function readScenario(name) {
	return readFileSync(new URL(name, SCENARIO_ROOT), 'utf8');
}

test('runtime guard rejects shared Docker and every Firebase-capable profile', () => {
	const guard = readScenario('guard-runtime.sh');

	assert.match(guard, /SPRING_PROFILE/);
	assert.match(guard, /local\|test/);
	assert.match(guard, /docker\|prod|prod\|docker/);
	assert.match(guard, /faithlog-latest/);
	assert.match(guard, /com\.docker\.compose\.project/);
	assert.match(guard, /PERF_FCM_ADAPTER.*fake/);
	assert.match(guard, /FIREBASE_CONFIG_(JSON|PATH)/);
	assert.doesNotMatch(
		guard,
		/docker compose[^\n]*(up|build|down)|docker builder prune|docker (system|image|volume) prune/,
	);
});

test('fixture contract separates datasetId and fixtureRunId and permits dummy notification rows only', () => {
	const prepare = readScenario('prepare-fixtures.sh');
	const sql = readScenario('prepare-fixtures.sql');

	assert.match(prepare, /PERF_DATASET_ID/);
	assert.match(prepare, /PERF_FIXTURE_RUN_ID/);
	assert.match(prepare, /PERF_SAMPLE_KIND/);
	assert.match(prepare, /PERF_MEMBER_COUNT.*1000/);
	assert.match(prepare, /manifest\.json/);
	assert.match(prepare, /build\/reports\/k6\/notification-batch\/fixtures/);
	assert.match(sql, /user_fcm_tokens/);
	assert.match(sql, /campus\.name[\s\S]*dataset_id/);
	assert.match(sql, /PERFORMANCE_198_DUMMY/);
	assert.match(sql, /success_count/);
	assert.match(sql, /transient_count/);
	assert.match(sql, /permanent_count/);
	assert.match(sql, /inactive_count/);
	assert.match(sql, /no_token_count/);
	assert.match(sql, /UPDATE\s+user_fcm_tokens[\s\S]*PERFORMANCE_198_DUMMY/i);
	assert.doesNotMatch(sql, /^\s*(TRUNCATE|DELETE\s+FROM|DROP\s+TABLE)\b/im);
	assert.doesNotMatch(prepare, /FIREBASE|FCM_TOKEN=.*[^D]UMMY/i);
});

test('runner keeps fixture preparation separate, holds a global load lock, and records actual Compose labels', () => {
	const runner = readScenario('run-before.sh');

	assert.match(runner, /active-measurement\.lock/);
	assert.match(runner, /NotificationBatchBeforeScenarioTest/);
	assert.match(runner, /--no-daemon/);
	assert.match(runner, /docker stats/);
	assert.match(runner, /redis-cli.*INFO.*commandstats/is);
	assert.match(runner, /environment\.json/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.doesNotMatch(runner, /prepare-fixtures\.sh/);
	assert.doesNotMatch(
		runner,
		/docker compose[^\n]*(up|build|down)|docker builder prune|docker (system|image|volume) prune/,
	);
});

test('test-profile harness exercises the production request and delivery services with a deterministic fake sender', () => {
	const harness = readFileSync(JAVA_HARNESS, 'utf8');

	assert.match(harness, /@ActiveProfiles\("local"\)/);
	assert.match(harness, /@EnabledIfEnvironmentVariable/);
	assert.match(harness, /NotificationRequestCommandService/);
	assert.match(harness, /requestAutomaticNotification/);
	assert.match(harness, /NotificationDeliveryWorker/);
	assert.match(harness, /processRequest/);
	assert.match(harness, /FakeFcmSendPort/);
	assert.match(harness, /Only Issue #198 dummy tokens/);
	assert.match(harness, /FcmSendFailureType\.PERMANENT/);
	assert.match(harness, /FcmSendFailureType\.TRANSIENT/);
	assert.match(harness, /CapturingNotificationDispatchPort/);
	assert.match(harness, /getPrepareStatementCount/);
	assert.match(harness, /scenario-result\.json/);
	assert.doesNotMatch(harness, /FirebaseMessaging|GoogleCredentials/);
});

test('verification contract covers throughput, DB calls, status counts, dedupe, isolation, and partial failure', () => {
	const verifier = readScenario('verify-before.mjs');
	const summarizer = readScenario('summarize-before.mjs');
	const readme = readScenario('README.md');
	const requiredTerms = [
		'durationMs',
		'throughputPerSecond',
		'dbPreparedStatements',
		'perUserDbCalls',
		'createdLogs',
		'logInsertCount',
		'logUpdateCount',
		'tokenLookupCount',
		'tokenUpdateCount',
		'SENT',
		'FAILED',
		'SKIPPED',
		'duplicateReplayCreatedCount',
		'crossCampusMutationCount',
	];

	for (const term of requiredTerms) assert.match(verifier, new RegExp(term));
	for (const percentile of ['p50', 'p95', 'p99', 'max']) {
		assert.match(summarizer, new RegExp(percentile));
	}
	assert.match(summarizer, /providerFakeFailureRate/);
	assert.match(summarizer, /scenarioFailureRate/);
	assert.match(readme, /notificationType \+ campusId \+ scopeId \+ targetUserId \+ businessDate/);
	assert.match(readme, /partial failure/i);
	assert.match(readme, /scenario-ready/i);
	assert.match(readme, /not-measured/i);
	assert.match(readme, /실제 Firebase|external FCM/i);
	assert.match(readme, /production Java/i);
	assert.match(readme, /병렬/);
});
