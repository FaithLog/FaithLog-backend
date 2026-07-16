import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { test } from 'node:test';

const ROOT = new URL('./poll-settlement/', import.meta.url);
const PROJECT_ROOT = new URL('../../', import.meta.url);

function read(name) {
	return readFileSync(new URL(name, ROOT), 'utf8');
}

function readProject(name) {
	return readFileSync(new URL(name, PROJECT_ROOT), 'utf8');
}

test('target contract exact-binds the PM-approved immutable develop server', () => {
	const target = JSON.parse(read('target-contract.json'));

	assert.equal(target.sourceCommit, '355f79df5b2e47636b7d1a17dea029da6c93c62d');
	assert.equal(target.baseUrl, 'http://127.0.0.1:28080');
	assert.equal(target.containers.app.name, 'faithlog-latest-app');
	assert.equal(target.containers.app.id, '901dbab3949fc669e7902e6c1471f4d60ffc80b049efa0f9a5203343710a7868');
	assert.equal(target.containers.app.imageId, 'sha256:759dbf31b1a3ae2261ccc6e409af3a1c82f64c487b53bfe7f1af74d5bd2f4d07');
	assert.equal(target.containers.postgres.id, '81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9');
	assert.equal(target.containers.redis.id, '4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9');
	assert.equal(target.database.statsReset, null);
	assert.equal(target.resourceSampling.samplingIntervalMs, 1000);
	assert.equal(target.flywayVersion, '11');
});

test('runner wires strict collectors and gates every boundary before correctness and summary adoption', () => {
	const runner = read('run-baseline.sh');
	const dbCollector = read('capture-db-evidence.mjs');
	const resourceCollector = read('capture-resource-sample.mjs');

	assert.match(runner, /target-contract\.json/);
	assert.match(runner, /capture-runtime\.mjs/);
	assert.match(runner, /capture-db-evidence\.mjs/);
	assert.match(runner, /capture-db-evidence\.test\.mjs/);
	assert.match(runner, /run-baseline-fail-fast\.test\.mjs/);
	assert.match(runner, /run-baseline-sampler\.test\.mjs/);
	assert.match(runner, /capture-resource-sample\.mjs/);
	assert.match(runner, /validate-evidence\.mjs/);
	assert.match(runner, /faithlog-performance-\$\{COMPOSE_PROJECT\}\.lock/);
	assert.match(runner, /\$\{BASE_URL\}\/api-docs/);
	assert.doesNotMatch(runner, /\$\{BASE_URL\}\/v3\/api-docs/);
	assert.match(runner, /method:\s*"patch"[\s\S]*\/api\/v1\/admin\/campuses\/\{campusId\}\/polls\/\{pollId\}\/close/);
	assert.match(runner, /method:\s*"post"[\s\S]*\/api\/v1\/campuses\/\{campusId\}\/meal\/polls\/\{pollId\}\/charges/);
	for (const phase of ['initial', 'post-lock', 'before', 'after', 'final']) assert.match(runner, new RegExp(phase));
	for (const mode of ['coffee-sequential', 'meal-sequential', 'coffee-concurrent', 'meal-concurrent']) assert.match(`${runner}\n${read('single-mode-contract.mjs')}`, new RegExp(mode));
	assert.match(runner, /samplingIntervalMs/);
	assert.match(runner, /maxGapMs/);
	assert.match(resourceCollector, /--no-stream[\s\S]*--no-trunc/);
	assert.doesNotMatch(`${runner}\n${dbCollector}`, /pg_stat_statements_reset|CREATE\s+EXTENSION|ALTER\s+SYSTEM|shared_preload_libraries/i);
	assert.doesNotMatch(runner, /snapshot_pg|sample_stats|--summary-export/);
	const finalRuntime = runner.lastIndexOf('runtime-final.json');
	const correctness = runner.lastIndexOf('verify-baseline.mjs');
	const summary = runner.lastIndexOf('summarize-results.mjs');
	assert.ok(correctness > 0 && finalRuntime > correctness && summary > finalRuntime, 'correctness must precede final runtime continuity and summary classification');
	const warmup = runner.indexOf('PHASE=warmup');
	const token = runner.lastIndexOf('prepare-measured-token.mjs');
	const dbBefore = runner.indexOf('capture_db "${db_before}"');
	const measured = runner.lastIndexOf('PHASE=measured');
	assert.ok(warmup > 0 && token > warmup && dbBefore > token && measured > dbBefore, 'warmup and measured token login must finish before measured DB/resource boundaries');
	assert.doesNotMatch(runner, /docker compose|docker (build|restart|stop|rm|system|image|volume|builder)|prune/);
});

test('maintenance readiness retains exact 5/30/180 quiet and sanitized read-only observation contracts', () => {
	const target = JSON.parse(read('target-contract.json'));
	const runner = read('run-baseline.sh');
	const gate = read('wait-maintenance-readiness.mjs');
	assert.deepEqual({ pollIntervalSeconds: target.maintenanceReadiness.pollIntervalSeconds, quietSeconds: target.maintenanceReadiness.quietSeconds, timeoutSeconds: target.maintenanceReadiness.timeoutSeconds }, { pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180 });
	for (const name of ['MAINTENANCE_POLL_INTERVAL_SECONDS', 'MAINTENANCE_QUIET_SECONDS', 'MAINTENANCE_TIMEOUT_SECONDS']) {
		assert.match(runner, new RegExp(name));
	}
	assert.match(gate, /backend_type='autovacuum worker'/);
	for (const field of ['n_mod_since_analyze', 'reltuples', 'autovacuum_analyze_threshold', 'autovacuum_analyze_scale_factor', 'pg_options_to_table']) assert.match(gate, new RegExp(field));
	assert.doesNotMatch(gate, /\b(?:VACUUM|ANALYZE)\s+(?:VERBOSE\s+)?[a-z]|pg_stat_reset|CREATE\s+EXTENSION|ALTER\s+SYSTEM/i);
	assert.match(read('summarize-results.mjs'), /maintenanceReadiness/);
});

test('approved per-mode readiness gates exact-bind charge writes and precede every mode warmup', () => {
	const target = JSON.parse(read('target-contract.json'));
	const runner = read('run-baseline.sh');
	assert.deepEqual(target.maintenanceReadiness, {
		pollIntervalSeconds: 5, quietSeconds: 30, timeoutSeconds: 180,
		expectedChargeWrites: { 'coffee-sequential': 11000, 'meal-sequential': 11000, 'coffee-concurrent': 6000, 'meal-concurrent': 6000 },
	});
	const runMode = runner.slice(runner.indexOf('run_mode() {'), runner.indexOf('\nRUN_STATUS=0'));
	const readiness = runMode.indexOf('wait-maintenance-readiness.mjs');
	const warmup = runMode.indexOf('PHASE=warmup');
	assert.ok(readiness > 0 && warmup > readiness, 'each mode readiness gate must precede its warmup');
	assert.match(runMode, /MODE="\$\{mode\}"[^\n]*wait-maintenance-readiness\.mjs[^\n]*\|\| return/);
	assert.doesNotMatch(runner, /maintenance-stability\.json/);
	assert.doesNotMatch(read('wait-maintenance-readiness.mjs'), /\b(?:VACUUM|ANALYZE)\s+(?:VERBOSE\s+)?[a-z]|pg_stat_reset|CREATE\s+EXTENSION|ALTER\s+SYSTEM/i);
});

test('runner requires one exact MODE before any runtime, fixture, Docker, or k6 work', () => {
	const runner = read('run-baseline.sh');
	const modeGate = runner.indexOf('requireExactMode');
	const firstRuntime = runner.indexOf('INITIAL_TEMP=');
	assert.match(runner, /for name in[^\n]*MODE/);
	assert.ok(modeGate > 0 && modeGate < firstRuntime, 'exact MODE gate must precede runtime and mutable work');
	assert.match(runner, /run_mode "\$\{MODE\}"/);
	assert.doesNotMatch(runner, /for mode in coffee-sequential meal-sequential coffee-concurrent meal-concurrent/);
	assert.match(runner, /MODE="\$\{MODE\}"[^\n]*verify-baseline\.mjs/);
	assert.match(runner, /MODE="\$\{MODE\}"[^\n]*summarize-results\.mjs/);
});

test('#200 fixture models multiple COFFEE duties while preserving creator and account ownership', () => {
	const seed = read('seed-fixtures.sql');
	const manifest = read('seed-fixtures.mjs');

	assert.match(seed, /generate_series\(2,\s*:member_count\)/);
	assert.match(seed, /actor_id[\s\S]*observer_id[\s\S]*'COFFEE'/);
	assert.match(seed, /owner_user_id[\s\S]*:actor_id/);
	assert.match(seed, /template_id[\s\S]*NULL/);
	assert.match(seed, /created_by[\s\S]*:actor_id/);
	assert.doesNotMatch(seed, /INSERT\s+INTO\s+poll_templates/i);
	assert.match(manifest, /coffeeDuties:\s*2/);
	assert.match(manifest, /creatorOwnedCoffeePolls/);
	assert.match(manifest, /accountNeutralTemplates/);
	assert.match(manifest, /schedulerCreatedCoffeePolls/);
	assert.match(manifest, /Existing dataset state is forbidden/);
	assert.doesNotMatch(manifest, /reuseExistingDataset/);
});

test('#200 production keeps creator ownership, account-neutral templates, and COFFEE auto-create exclusion', () => {
	const status = readProject('src/main/java/com/faithlog/poll/service/PollStatusCommandService.java');
	const settlement = readProject('src/main/java/com/faithlog/poll/service/CoffeePollSettlementSupport.java');
	const scheduler = readProject('src/main/java/com/faithlog/batch/service/ScheduledPollCreationService.java');
	const migration = readProject('src/main/resources/db/migration/V10__neutralize_coffee_template_accounts.sql');

	assert.match(status, /requireCoffeePollOwnerForUpdate/);
	assert.match(settlement, /scope\.getCreatedBy\(\)/);
	assert.match(settlement, /poll\.createdBy\(\)\.equals\(account\.ownerUserId\(\)\)/);
	assert.match(scheduler, /CoffeeOperationClassifier[\s\S]*continue;/);
	assert.match(migration, /SET payment_account_id = NULL/);
});

test('k6 measures only the unchanged settlement endpoints and keeps reminders out of scope', () => {
	const scenario = read('settlement-baseline.js');
	const summary = read('summarize-results.mjs');

	assert.match(scenario, /\/api\/v1\/admin\/campuses\/\$\{manifest\.campusId\}\/polls\/\$\{fixture\.pollId\}\/close/);
	assert.match(scenario, /\/api\/v1\/campuses\/\$\{manifest\.campusId\}\/meal\/polls\/\$\{fixture\.pollId\}\/charges/);
	assert.match(scenario, /new Trend\('coffee_settlement_duration'/);
	assert.match(scenario, /new Trend\('meal_settlement_duration'/);
	assert.match(scenario, /new Counter\('coffee_settlement_requests'/);
	assert.match(scenario, /new Counter\('meal_settlement_requests'/);
	assert.match(summary, /validateMetricEvidence/);
	assert.doesNotMatch(summary, /http_reqs/);
	assert.match(scenario, /vus:\s*5/);
	assert.match(scenario, /iterations:\s*5/);
	assert.doesNotMatch(scenario, /reminders|notifications|notification-logs|payment-unpaid/i);
	assert.match(scenario, /requireExactBaseUrl/);
	assert.match(scenario, /PHASE/);
	assert.match(scenario, /TOKEN_PATH/);
	const setup = /export function setup\(\) \{[\s\S]*?\n\}/.exec(scenario)?.[0] || '';
	assert.doesNotMatch(setup, /settle\(|const warmupToken|const measuredToken\s*=\s*login/);
	assert.doesNotMatch(scenario, /29280/);
});

test('#201 post-check uses page size 10 and proves UNPAID settlement rows survive archive filtering', () => {
	const verifier = read('verify-baseline.mjs');
	const billingController = readProject('src/main/java/com/faithlog/billing/controller/BillingController.java');

	assert.match(verifier, /includeArchived=false/);
	assert.match(verifier, /page=0&size=10/);
	assert.match(verifier, /totalElements/);
	assert.match(verifier, /totalPages/);
	assert.match(`${verifier}\n${read('single-mode-contract.mjs')}`, /terminalStatusCharges:\s*0/);
	assert.match(`${verifier}\n${read('single-mode-contract.mjs')}`, /notificationSideEffects:\s*0/);
	assert.match(billingController, /defaultValue = "false"\) boolean includeArchived/);
	assert.match(billingController, /defaultValue = "10"\) int size/);
});

test('approved load remains 1,000 ACTIVE members, warmup 1, sequential 10, and VUS 5 concurrent', () => {
	const seed = read('seed-fixtures.mjs');
	const scenario = read('settlement-baseline.js');
	const token = read('prepare-measured-token.mjs');

	assert.match(seed, /PERF_MEMBER_COUNT\s*=\s*1000/);
	assert.match(seed, /sequentialWarmup:\s*1/);
	assert.match(seed, /sequentialMeasured:\s*10/);
	assert.match(seed, /concurrentWarmup:\s*1/);
	assert.match(seed, /concurrentMeasured:\s*5/);
	assert.match(scenario, /iterations:\s*10/);
	assert.match(scenario, /endsWith\('-sequential'\)[\s\S]*maxDuration:\s*'27m'/);
	assert.doesNotMatch(scenario, /maxDuration:\s*'29m'/);
	assert.match(scenario, /vus:\s*5/);
	assert.match(scenario, /endsWith\('-concurrent'\)[\s\S]*maxDuration:\s*'14m'/);
	assert.match(token, /endsWith\('-sequential'\)\s*\?\s*1620/);
	assert.match(token, /endsWith\('-concurrent'\)\s*\?\s*840/);
});
