import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { spawnSync } from 'node:child_process';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPO_ROOT = path.resolve(ISSUE_DIR, '../../..');
const APP_CONTAINER_ID = 'a'.repeat(64);
const DB_CONTAINER_ID = 'b'.repeat(64);
const REDIS_CONTAINER_ID = 'c'.repeat(64);
const REPLACEMENT_CONTAINER_ID = 'd'.repeat(64);

function requiredPath(relativePath) {
	const target = path.join(ISSUE_DIR, relativePath);
	assert.equal(fs.existsSync(target), true, `required PM finding contract file is missing: ${relativePath}`);
	return target;
}

function runNode(script, ...args) {
	return spawnSync(process.execPath, [script, ...args], { encoding: 'utf8' });
}

test('workload values have no defaults and missing values fail before any runner action', () => {
	const runner = fs.readFileSync(requiredPath('run-devotion-baseline.sh'), 'utf8');
	const scenario = fs.readFileSync(requiredPath('devotion-write.js'), 'utf8');
	for (const name of [
		'WARMUP_VUS', 'MEASURED_VUS', 'ROLLBACK_VUS',
		'WARMUP_MAX_DURATION', 'MEASURED_MAX_DURATION', 'ROLLBACK_MAX_DURATION',
		'TOKEN_TTL_SAFETY_SECONDS', 'RESOURCE_SAMPLE_INTERVAL_SECONDS', 'RESOURCE_SAMPLE_MAX_GAP_SECONDS',
	]) assert.match(runner, new RegExp(name));
	assert.doesNotMatch(runner, /WARMUP_VUS:-|MEASURED_VUS:-|ROLLBACK_VUS:-|MAX_DURATION:-/);
	assert.doesNotMatch(scenario, /__ENV\.PHASE\s*\|\||__ENV\.VUS\s*\|\||__ENV\.MAX_DURATION\s*\|\|/);

	const missingWorkloadRejection = path.join(os.tmpdir(), `faithlog-197-missing-workload-${process.pid}.json`);
	const result = spawnSync('bash', [path.join(ISSUE_DIR, 'run-devotion-baseline.sh')], {
		encoding: 'utf8',
		env: {
			...process.env,
			FIXTURE_MANIFEST: '/tmp/not-used.json', CREDENTIALS_FILE: '/tmp/not-used-credentials.json',
			ATTRIBUTION_SIGNATURE_FILE: '/tmp/not-used-signature.json',
			APP_CONTAINER: 'not-used-app', DB_CONTAINER: 'not-used-db', REDIS_CONTAINER: 'not-used-redis',
			APP_SOURCE_WORKTREE: REPO_ROOT,
			EXPECTED_COMPOSE_PROJECT: 'not-used-project', EXPECTED_APP_COMPOSE_SERVICE: 'not-used-app-service', EXPECTED_DB_COMPOSE_SERVICE: 'not-used-db-service',
			EXPECTED_REDIS_COMPOSE_SERVICE: 'not-used-redis-service',
			EXPECTED_APP_REVISION: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
			EXPECTED_APP_IMAGE_ID: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
			EXPECTED_APP_JAR_SHA256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
			EXPECTED_API_CONTRACT_SHA256: 'cccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccccc',
			EXPECTED_DB_IMAGE_ID: `sha256:${'4'.repeat(64)}`, EXPECTED_REDIS_IMAGE_ID: `sha256:${'5'.repeat(64)}`,
			EXPECTED_FLYWAY_VERSION: '11', EXPECTED_FLYWAY_SCRIPT: 'V11__secure_supabase_data_api.sql', EXPECTED_FLYWAY_CHECKSUM: '-123456789',
			DB_HOST: '127.0.0.1', REDIS_HOST: '127.0.0.1',
			EXPECTED_DB_PORT: '5432', EXPECTED_REDIS_PORT: '6379',
			REJECTION_EVIDENCE_FILE: missingWorkloadRejection,
			DB_NAME: 'not-used-db-name', DB_USER: 'not-used-db-user',
			BASE_URL: 'http://127.0.0.1:28080',
		},
	});
	assert.notEqual(result.status, 0);
	assert.match(result.stderr, /WARMUP_VUS/);
	fs.rmSync(missingWorkloadRejection, { force: true });

	const missingSignatureRejection = path.join(os.tmpdir(), `faithlog-197-missing-signature-${process.pid}.json`);
	const missingSignature = spawnSync('bash', [path.join(ISSUE_DIR, 'run-devotion-baseline.sh')], {
		encoding: 'utf8',
		env: {
			...process.env,
			FIXTURE_MANIFEST: '/tmp/not-used.json', CREDENTIALS_FILE: '/tmp/not-used-credentials.json',
			APP_CONTAINER: 'not-used-app', DB_CONTAINER: 'not-used-db',
			EXPECTED_COMPOSE_PROJECT: 'not-used-project', EXPECTED_APP_COMPOSE_SERVICE: 'not-used-app-service', EXPECTED_DB_COMPOSE_SERVICE: 'not-used-db-service',
			DB_NAME: 'not-used-db-name', DB_USER: 'not-used-db-user',
			BASE_URL: 'http://127.0.0.1:28080', WARMUP_VUS: '1', MEASURED_VUS: '1', ROLLBACK_VUS: '1',
			WARMUP_MAX_DURATION: '1s', MEASURED_MAX_DURATION: '1s', ROLLBACK_MAX_DURATION: '1s',
			TOKEN_TTL_SAFETY_SECONDS: '1', EXTERNAL_ACTIVITY: 'none',
			REJECTION_EVIDENCE_FILE: missingSignatureRejection,
		},
	});
	assert.notEqual(missingSignature.status, 0);
	assert.match(missingSignature.stderr, /ATTRIBUTION_SIGNATURE_FILE/);
	fs.rmSync(missingSignatureRejection, { force: true });
});

test('runtime workload and JWT claims reject invalid VUS, expiry, and subject without logging tokens', async () => {
	const runtime = await import(`${pathToFileURL(requiredPath('lib/runtime-contract.mjs')).href}?t=${Date.now()}`);
	const manifest = devotionManifest();
	const now = Math.floor(Date.now() / 1000);
	const workload = validWorkload();
	assert.equal(
		runtime.validateRuntimeContract(manifest, devotionCredentials(manifest, now + 3600), workload, now).requiredTokenTtlSeconds,
		4
	);
	assert.throws(
		() => runtime.validateRuntimeContract(manifest, devotionCredentials(manifest, now + 3600), { ...workload, measuredVus: 0 }, now),
		/MEASURED_VUS/
	);
	assert.throws(
		() => runtime.validateRuntimeContract(manifest, devotionCredentials(manifest, now + 3600), { ...workload, measuredMaxDuration: '0s' }, now),
		/MEASURED_MAX_DURATION/
	);
	assert.throws(
		() => runtime.validateRuntimeContract(manifest, devotionCredentials(manifest, now + 3), workload, now),
		/remaining TTL/
	);
	const wrongSubject = devotionCredentials(manifest, now + 3600);
	wrongSubject.tokens[0].accessToken = jwt(999999, now + 3600);
	assert.throws(() => runtime.validateRuntimeContract(manifest, wrongSubject, workload, now), /subject\/userId/);
	const missingUser = devotionCredentials(manifest, now + 3600);
	missingUser.tokens.pop();
	assert.throws(() => runtime.validateRuntimeContract(manifest, missingUser, workload, now), /coverage/);
});

test('BASE_URL exact-matches the inspected app published port', async () => {
	const runtime = await import(`${pathToFileURL(requiredPath('lib/runtime-contract.mjs')).href}?target=${Date.now()}`);
	assert.equal(runtime.validatePublishedTarget('http://127.0.0.1:28080', '28080').port, 28080);
	assert.throws(() => runtime.validatePublishedTarget('http://127.0.0.1:8080', '28080'), /published port/);
	assert.throws(() => runtime.validatePublishedTarget('http://localhost:28080', '28080'), /numeric loopback/);
	assert.throws(() => runtime.validatePublishedTarget('http://host.docker.internal:28080', '28080'), /numeric loopback/);
});

test('devotion preflight validator rejects stale cohorts and wrong calculated amount', () => {
	const validator = requiredPath('lib/validate-devotion-preflight.mjs');
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-preflight-'));
	try {
		const manifest = devotionManifest();
		const manifestPath = writeJson(temporaryDirectory, 'manifest.json', manifest);
		const valid = preflightEvidence(manifest);
		const validPath = writeJson(temporaryDirectory, 'valid.json', valid);
		const stalePath = writeJson(temporaryDirectory, 'stale.json', { ...valid, existingWeeklyCount: 1, calculatedPenaltyAmount: 1 });
		const wrongMembershipPath = writeJson(temporaryDirectory, 'wrong-membership.json', { ...valid, rollbackUsersInSuccessCampus: 1 });
		const wrongRulesPath = writeJson(temporaryDirectory, 'wrong-rules.json', { ...valid, activePenaltyRuleCount: 3 });
		assert.equal(runNode(validator, manifestPath, validPath).status, 0);
		assert.notEqual(runNode(validator, manifestPath, stalePath).status, 0);
		assert.notEqual(runNode(validator, manifestPath, wrongMembershipPath).status, 0);
		assert.notEqual(runNode(validator, manifestPath, wrongRulesPath).status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('k6 adoption supports direct and values shapes and rejects missing or failed metrics', () => {
	const validator = requiredPath('lib/validate-k6-summary.mjs');
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-summary-'));
	try {
		const direct = summary('measured', 1000);
		const wrapped = { metrics: Object.fromEntries(Object.entries(direct.metrics).map(([name, value]) => [name, { values: value }])) };
		const failed = structuredClone(direct);
		failed.metrics.devotion_weekly_measured_failure.rate = 0.01;
		const missing = structuredClone(direct);
		delete missing.metrics.devotion_weekly_measured['p(99)'];
		const paths = Object.fromEntries(Object.entries({ direct, wrapped, failed, missing })
			.map(([name, value]) => [name, writeJson(temporaryDirectory, `${name}.json`, value)]));
		assert.equal(runNode(validator, paths.direct, 'measured', '1000').status, 0);
		assert.equal(runNode(validator, paths.wrapped, 'measured', '1000').status, 0);
		assert.notEqual(runNode(validator, paths.failed, 'measured', '1000').status, 0);
		assert.notEqual(runNode(validator, paths.missing, 'measured', '1000').status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('all k6 phases reject impossible latency, throughput, and transaction values in both summary shapes', async () => {
	const { validateSummary } = await import(
		`${pathToFileURL(requiredPath('lib/validate-k6-summary.mjs')).href}?math=${Date.now()}`
	);
	for (const phase of ['warmup', 'measured', 'rollback']) {
		for (const shape of ['direct', 'wrapped']) {
			const valid = summaryWithShape(phase, 7, shape);
			assert.deepEqual(validateSummary(valid, phase, 7), {
				p50: 10, p95: 20, p99: 30, max: 40,
				throughput: 10, failureRate: 0, transactions: 7,
			});

			for (const metrics of [
				{ p50: -1, p95: 2, p99: 3, max: 4 },
				{ p50: 10, p95: 2, p99: 30, max: 40 },
				{ p50: 10, p95: 20, p99: 41, max: 40 },
			]) {
				assert.throws(() => validateSummary(summaryWithShape(phase, 7, shape, metrics), phase, 7), /latency|p50|p95|p99|max/i);
			}
			assert.throws(() => validateSummary(summaryWithShape(phase, 7, shape, { throughput: 0 }), phase, 7), /throughput/i);
			assert.throws(() => validateSummary(summaryWithShape(phase, 1.5, shape), phase, 1.5), /positive integer|transaction/i);
			assert.throws(() => validateSummary(summaryWithShape(phase, 0, shape), phase, 0), /positive integer|transaction/i);
		}
	}
});

test('resource evidence is bound to approved immutable containers and covers the measured window without sampling gaps', async () => {
	const validatorPath = requiredPath('lib/validate-resource-window.mjs');
	const { validateResourceWindow } = await import(`${pathToFileURL(validatorPath).href}?resource=${Date.now()}`);
	const config = resourceWindowConfig();
	const samples = validResourceSamples();
	assert.equal(validateResourceWindow(samples, config).adoptable, true);

	for (const contaminated of [
		[{ ...samples[0] }],
		samples.map((sample, index) => index === 2 ? { ...sample, containerId: 'replaced-app-id' } : sample),
		samples.map((sample, index) => index === 3 ? { ...sample, observedAt: samples[2].observedAt } : sample),
		samples.filter((_, index) => index !== 2 && index !== 3),
		samples.map((sample, index) => index === 1 ? { ...sample, cpuPercent: '1.5' } : sample),
	]) {
		assert.equal(validateResourceWindow(contaminated, config).adoptable, false);
	}

	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-resources-'));
	try {
		const samplesPath = path.join(temporaryDirectory, 'samples.jsonl');
		fs.writeFileSync(samplesPath, samples.map((sample) => JSON.stringify(sample)).join('\n'));
		const configPath = writeJson(temporaryDirectory, 'config.json', config);
		assert.equal(runNode(validatorPath, samplesPath, configPath, path.join(temporaryDirectory, 'valid.json')).status, 0);
		const badConfigPath = writeJson(temporaryDirectory, 'bad-config.json', { ...config, samplingIntervalSeconds: 0 });
		assert.notEqual(runNode(validatorPath, samplesPath, badConfigPath, path.join(temporaryDirectory, 'invalid.json')).status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('runtime identity remains exact across all checkpoints and rejects app, database container, or postmaster replacement', async () => {
	const validatorPath = requiredPath('lib/validate-runtime-identity.mjs');
	const { validateRuntimeIdentitySeries } = await import(`${pathToFileURL(validatorPath).href}?identity=${Date.now()}`);
	const initial = runtimeIdentity();
	const checkpoints = runtimeIdentityCheckpoints(initial);
	assert.equal(validateRuntimeIdentitySeries(initial, checkpoints).adoptable, true);

	for (const replacement of [
		{ checkpoint: 'measuredBefore', section: 'app', field: 'containerId', value: REPLACEMENT_CONTAINER_ID },
		{ checkpoint: 'measuredAfter', section: 'databaseContainer', field: 'imageId', value: `sha256:${'6'.repeat(64)}` },
		{ checkpoint: 'final', section: 'databaseServer', field: 'postmasterStartTime', value: '2026-07-14T00:01:00.000Z' },
	]) {
		const contaminated = structuredClone(checkpoints);
		contaminated[replacement.checkpoint][replacement.section][replacement.field] = replacement.value;
		const evidence = validateRuntimeIdentitySeries(initial, contaminated);
		assert.equal(evidence.adoptable, false);
		assert.match(JSON.stringify(evidence.failures), new RegExp(replacement.field, 'i'));
	}

	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-runtime-identity-'));
	try {
		const initialPath = writeJson(temporaryDirectory, 'initial.json', initial);
		const replaced = structuredClone(initial);
		replaced.app.containerId = REPLACEMENT_CONTAINER_ID;
		const replacedPath = writeJson(temporaryDirectory, 'replaced.json', replaced);
		const result = runNode(
			validatorPath, 'validate-pair', initialPath, replacedPath, 'measuredAfter', path.join(temporaryDirectory, 'gate.json')
		);
		assert.notEqual(result.status, 0);
		assert.equal(JSON.parse(fs.readFileSync(path.join(temporaryDirectory, 'gate.json'), 'utf8')).adoptable, false);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('database window rejects vacuum and autovacuum drift on every observed table', async () => {
	const { validateDbWindow } = await import(
		`${pathToFileURL(requiredPath('lib/validate-db-window.mjs')).href}?vacuum=${Date.now()}`
	);
	const before = dbSnapshot();
	const after = dbSnapshot({ measured: true });
	for (const field of ['vacuum_count', 'autovacuum_count', 'last_vacuum', 'last_autovacuum']) {
		const contaminated = structuredClone(after);
		const table = contaminated.snapshot.tables.find(({ relname }) => relname === 'weekly_devotion_records');
		table[field] = field.endsWith('_count') ? 1 : '2026-07-14T00:00:30.000Z';
		const evidence = validateDbWindow(before, contaminated, 'none');
		assert.equal(evidence.adoptable, false, `${field} drift was adopted`);
		assert.match(JSON.stringify(evidence.failures), new RegExp(field));
	}
	const missingMaintenanceField = dbSnapshot({ measured: true });
	delete missingMaintenanceField.snapshot.tables[0].vacuum_count;
	assert.throws(() => validateDbWindow(before, missingMaintenanceField, 'none'), /exact table counter and maintenance fields/);
});

test('runner places immutable identity checks outside the pure measured DB counter window and gates resource evidence', () => {
	const runner = fs.readFileSync(requiredPath('run-devotion-baseline.sh'), 'utf8');
	for (const contract of [
		'RESOURCE_SAMPLE_INTERVAL_SECONDS', 'RESOURCE_SAMPLE_MAX_GAP_SECONDS',
		'{{.Id}}', '{{.Image}}', '{{.State.StartedAt}}', 'runtime-identity.sql',
		'warmupBefore', 'measuredBefore', 'measuredAfter', 'final',
		'resource-window-evidence.json', 'runtime-identity-evidence.json',
	]) assert.match(runner, new RegExp(contract.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	assert.doesNotMatch(runner, /RESOURCE_SAMPLE_INTERVAL_SECONDS:-|RESOURCE_SAMPLE_MAX_GAP_SECONDS:-/);
	const measuredIdentity = runner.indexOf('runtime-identity-measured-before.json');
	const databaseBefore = runner.indexOf('db-counters-before.jsonl');
	const measuredPhase = runner.indexOf('PHASE=measured run_phase');
	const databaseAfter = runner.indexOf('db-counters-after.jsonl');
	const measuredAfterIdentity = runner.indexOf('runtime-identity-measured-after.json');
	assert.ok(measuredIdentity < databaseBefore);
	assert.ok(databaseBefore < measuredPhase);
	assert.ok(measuredPhase < databaseAfter);
	assert.ok(databaseAfter < measuredAfterIdentity);
});

test('database window rejects external sessions, planner changes, and declared activity', () => {
	const validator = requiredPath('lib/validate-db-window.mjs');
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-db-window-'));
	try {
		const beforePath = writeJson(temporaryDirectory, 'before.json', dbSnapshot());
		const after = dbSnapshot({ measured: true });
		const afterPath = writeJson(temporaryDirectory, 'after.json', after);
		const external = structuredClone(after);
		external.snapshot.externalActiveSessions = 1;
		const planner = structuredClone(after);
		planner.snapshot.tables[0].autoanalyze_count = 1;
		planner.snapshot.plannerSettings[0].setting = 'off';
		const pgssResetBefore = dbSnapshot();
		const pgssResetAfter = dbSnapshot({ measured: true });
		pgssResetBefore.pgStatStatements = { available: true, statements: [statementSnapshot(5)] };
		pgssResetAfter.pgStatStatements = { available: true, statements: [statementSnapshot(4)] };
		const externalPath = writeJson(temporaryDirectory, 'external.json', external);
		const plannerPath = writeJson(temporaryDirectory, 'planner.json', planner);
		const rollback = structuredClone(after);
		rollback.snapshot.database.xact_rollback = '1';
		const rollbackPath = writeJson(temporaryDirectory, 'rollback.json', rollback);
		const pgssBeforePath = writeJson(temporaryDirectory, 'pgss-before.json', pgssResetBefore);
		const pgssAfterPath = writeJson(temporaryDirectory, 'pgss-after.json', pgssResetAfter);
		assert.equal(runNode(validator, beforePath, afterPath, 'none', path.join(temporaryDirectory, 'delta.json')).status, 0);
		assert.notEqual(runNode(validator, beforePath, externalPath, 'none', path.join(temporaryDirectory, 'external-delta.json')).status, 0);
		assert.notEqual(runNode(validator, beforePath, plannerPath, 'none', path.join(temporaryDirectory, 'planner-delta.json')).status, 0);
		assert.notEqual(runNode(validator, beforePath, rollbackPath, 'none', path.join(temporaryDirectory, 'rollback-delta.json')).status, 0);
		assert.notEqual(runNode(validator, pgssBeforePath, pgssAfterPath, 'none', path.join(temporaryDirectory, 'pgss-delta.json')).status, 0);
		assert.notEqual(runNode(validator, beforePath, afterPath, 'frontend-active', path.join(temporaryDirectory, 'declared.json')).status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('measured activity must be attributable to the warmup query and table-counter signature', () => {
	const validator = requiredPath('lib/validate-activity-attribution.mjs');
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-attribution-'));
	try {
		const warmupBefore = dbCumulativeSnapshot(0, 0, 0);
		const warmupAfter = dbCumulativeSnapshot(1, 1, 1);
		const measuredBefore = structuredClone(warmupAfter);
		const measuredAfter = dbCumulativeSnapshot(1001, 2, 2);
		const transientRead = structuredClone(measuredAfter);
		const users = transientRead.snapshot.tables.find((table) => table.relname === 'users');
		users.idx_scan = (BigInt(users.idx_scan) + 1n).toString();
		transientRead.pgStatStatements.statements[0].calls = (BigInt(transientRead.pgStatStatements.statements[0].calls) + 1n).toString();
		const extraCommit = structuredClone(measuredAfter);
		extraCommit.snapshot.database.xact_commit = (BigInt(extraCommit.snapshot.database.xact_commit) + 1n).toString();
		const signature = approvedActivitySignature(1, 1000);
		const signatureSha256 = createHash('sha256').update(JSON.stringify(signature)).digest('hex');
		const manifest = devotionManifest();
		const paths = Object.fromEntries(Object.entries({ warmupBefore, warmupAfter, measuredBefore, measuredAfter, transientRead, extraCommit, signature, manifest })
			.map(([name, value]) => [name, writeJson(temporaryDirectory, `${name}.json`, value)]));
		assert.equal(runNode(
			validator, paths.warmupBefore, paths.warmupAfter, paths.measuredBefore, paths.measuredAfter,
			'1', '1000', paths.signature, signatureSha256, paths.manifest, 'faithlog', path.join(temporaryDirectory, 'valid.json')
		).status, 0);
		assert.notEqual(runNode(
			validator, paths.warmupBefore, paths.warmupAfter, paths.measuredBefore, paths.transientRead,
			'1', '1000', paths.signature, signatureSha256, paths.manifest, 'faithlog', path.join(temporaryDirectory, 'transient-read.json')
		).status, 0);
		assert.notEqual(runNode(
			validator, paths.warmupBefore, paths.warmupAfter, paths.measuredBefore, paths.extraCommit,
			'1', '1000', paths.signature, signatureSha256, paths.manifest, 'faithlog', path.join(temporaryDirectory, 'extra-commit.json')
		).status, 0);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('fixed approved signature rejects proportional contamination learned in both windows', async () => {
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?fixed=${Date.now()}`
	);
	const warmupBefore = dbCumulativeSnapshot(0, 0, 0);
	const warmupAfter = dbCumulativeSnapshot(1001, 1, 1);
	const measuredBefore = structuredClone(warmupAfter);
	const measuredAfter = dbCumulativeSnapshot(2002, 2, 2);
	const evidence = validateActivityAttribution(
		warmupBefore, warmupAfter, measuredBefore, measuredAfter,
		1000, 1000, approvedActivitySignature(1000, 1000)
	);
	assert.equal(evidence.adoptable, false);
	assert.match(JSON.stringify(evidence.failures), /approved signature/);
});

test('approved signature accepts exact production-shaped weekly and daily update counters', async () => {
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?updates=${Date.now()}`
	);
	const warmupBefore = dbCumulativeSnapshot(0, 0, 0);
	const warmupAfter = dbCumulativeSnapshot(1, 1, 1);
	setCumulativeUpdates(warmupAfter, 1, 4);
	const measuredBefore = structuredClone(warmupAfter);
	const measuredAfter = dbCumulativeSnapshot(1001, 2, 2);
	setCumulativeUpdates(measuredAfter, 1001, 4004);
	const evidence = validateActivityAttribution(
		warmupBefore, warmupAfter, measuredBefore, measuredAfter,
		1, 1000, approvedActivitySignature(1, 1000, { weeklyUpdatesPerUser: 1, dailyUpdatesPerUser: 4 })
	);
	assert.equal(evidence.adoptable, true, JSON.stringify(evidence.failures));
});

test('activity signature runtime validation enforces exact keys and strict JSON types', async () => {
	const { validateApprovedSignature } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?strict=${Date.now()}`
	);
	const valid = approvedActivitySignature(1, 1000);
	assert.throws(() => validateApprovedSignature({ ...valid, unexpected: true }), /exact|unexpected|properties/i);
	assert.throws(() => validateApprovedSignature({ ...valid, warmupUsers: '1' }), /integer|type/i);
	assert.throws(() => validateApprovedSignature({ ...valid, measuredUsers: 999 }), /1000/);
	assert.throws(() => validateApprovedSignature({ ...valid, datasetId: 'not-performance' }), /datasetId/);
	const extraStatementField = structuredClone(valid);
	extraStatementField.windows.warmup.pgStatStatements.statements[0].userid = 1;
	assert.throws(() => validateApprovedSignature(extraStatementField), /exact|userid|properties/i);
});

test('activity signature is frozen to a mode 0600 validated copy before writes', () => {
	const validator = requiredPath('lib/validate-activity-attribution.mjs');
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-signature-freeze-'));
	try {
		const manifest = devotionManifest();
		const manifestPath = writeJson(temporaryDirectory, 'manifest.json', manifest);
		const sourcePath = writeJson(temporaryDirectory, 'source.json', approvedActivitySignature(1, 1000));
		const frozenPath = path.join(temporaryDirectory, 'frozen.json');
		const result = runNode(validator, 'freeze-signature', sourcePath, manifestPath, 'faithlog', frozenPath);
		assert.equal(result.status, 0, result.stderr);
		assert.equal(fs.statSync(frozenPath).mode & 0o777, 0o600);
		fs.writeFileSync(sourcePath, JSON.stringify({ replaced: true }));
		assert.equal(JSON.parse(fs.readFileSync(frozenPath, 'utf8')).fixtureRunId, manifest.fixtureRunId);
	} finally {
		fs.rmSync(temporaryDirectory, { recursive: true, force: true });
	}
});

test('pre-write signature digest rejects a valid frozen file replaced during the run', async () => {
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?digest=${Date.now()}`
	);
	const approved = approvedActivitySignature(1, 1000);
	const expectedSignatureSha256 = createHash('sha256').update(JSON.stringify(approved)).digest('hex');
	const replaced = {
		windows: approved.windows,
		measuredUsers: approved.measuredUsers,
		warmupUsers: approved.warmupUsers,
		databaseName: approved.databaseName,
		fixtureRunId: approved.fixtureRunId,
		datasetId: approved.datasetId,
		schemaVersion: approved.schemaVersion,
	};
	assert.throws(() => validateActivityAttribution(
		dbCumulativeSnapshot(0, 0, 0), dbCumulativeSnapshot(1, 1, 1),
		dbCumulativeSnapshot(1, 1, 1), dbCumulativeSnapshot(1001, 2, 2),
		1, 1000, replaced, { expectedSignatureSha256 }
	), /digest|sha256/i);
});

test('pg_stat_statements duplicate normalized queries are database-scoped and aggregated', async () => {
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?pgss=${Date.now()}`
	);
	const warmupBefore = dbCumulativeSnapshot(0, 0, 0);
	const warmupAfter = dbCumulativeSnapshot(1, 1, 1);
	const measuredBefore = structuredClone(warmupAfter);
	const measuredAfter = dbCumulativeSnapshot(1001, 2, 2);
	setDuplicateStatements(warmupBefore, 0, 0);
	setDuplicateStatements(warmupAfter, 1, 0);
	setDuplicateStatements(measuredBefore, 1, 0);
	setDuplicateStatements(measuredAfter, 501, 500);
	const evidence = validateActivityAttribution(
		warmupBefore, warmupAfter, measuredBefore, measuredAfter,
		1, 1000, approvedActivitySignature(1, 1000)
	);
	assert.equal(evidence.adoptable, true, JSON.stringify(evidence.failures));
	assert.equal(evidence.pgStatStatements.measured.statements.length, 1);
	assert.equal(evidence.pgStatStatements.measured.statements[0].calls, '1000');
	const sql = fs.readFileSync(requiredPath('collect-db-counters.sql'), 'utf8');
	assert.match(sql, /dbid\s*=|JOIN\s+pg_database/is);
	assert.match(sql, /GROUP\s+BY\s+query/i);
});

test('database window rejects activity in another database in the same container', async () => {
	const { validateDbWindow } = await import(
		`${pathToFileURL(requiredPath('lib/validate-db-window.mjs')).href}?instance=${Date.now()}`
	);
	const before = dbSnapshot();
	const after = dbSnapshot({ measured: true });
	after.snapshot.allDatabases.find((database) => database.datname === 'postgres').xact_commit = '1';
	const evidence = validateDbWindow(before, after, 'none');
	assert.equal(evidence.adoptable, false);
	assert.match(JSON.stringify(evidence.failures), /postgres/);
});

test('database window preserves cumulative counter deltas beyond MAX_SAFE_INTEGER', async () => {
	const { validateDbWindow } = await import(
		`${pathToFileURL(requiredPath('lib/validate-db-window.mjs')).href}?bigint-window=${Date.now()}`
	);
	const before = losslessCumulativeSnapshot(0, 0, 0);
	const after = losslessCumulativeSnapshot(1000, 1, 1);
	const evidence = validateDbWindow(before, after, 'none');
	assert.equal(evidence.adoptable, true, JSON.stringify(evidence.failures));
	assert.equal(evidence.databaseDelta.xact_commit, '1001');
	assert.equal(evidence.tableDeltas.devotion_daily_checks.n_tup_ins, '7000');
	assert.equal(evidence.pgStatStatements.statements[0].calls, '1000');
});

test('activity attribution preserves approved signature deltas beyond MAX_SAFE_INTEGER', async () => {
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?bigint-attribution=${Date.now()}`
	);
	const warmupBefore = losslessCumulativeSnapshot(0, 0, 0);
	const warmupAfter = losslessCumulativeSnapshot(1, 1, 1);
	const measuredBefore = structuredClone(warmupAfter);
	const measuredAfter = losslessCumulativeSnapshot(1001, 2, 2);
	const evidence = validateActivityAttribution(
		warmupBefore, warmupAfter, measuredBefore, measuredAfter,
		1, 1000, approvedActivitySignature(1, 1000)
	);
	assert.equal(evidence.adoptable, true, JSON.stringify(evidence.failures));
	assert.equal(evidence.transactionAttribution.measuredApplicationCommits, '1000');
	assert.equal(evidence.tableAttribution.devotion_daily_checks.n_tup_ins.measuredDelta, '7000');
	assert.equal(evidence.pgStatStatements.measured.statements[0].calls, '1000');
});

test('null external-session evidence is non-adoptable in every DB and attribution snapshot', async () => {
	const { validateDbWindow } = await import(
		`${pathToFileURL(requiredPath('lib/validate-db-window.mjs')).href}?null-session=${Date.now()}`
	);
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?null-session=${Date.now()}`
	);
	for (const side of ['before', 'after']) {
		for (const field of ['externalActiveSessions', 'externalActiveSessionsAllDatabases']) {
			const before = dbSnapshot();
			const after = dbSnapshot({ measured: true });
			(side === 'before' ? before : after).snapshot[field] = null;
			assert.equal(validateDbWindow(before, after, 'none').adoptable, false, `${side}.${field} null was adopted`);
		}
	}

	for (const snapshotIndex of [0, 1, 2, 3]) {
		for (const field of ['externalActiveSessions', 'externalActiveSessionsAllDatabases']) {
			const snapshots = [
				dbCumulativeSnapshot(0, 0, 0), dbCumulativeSnapshot(1, 1, 1),
				dbCumulativeSnapshot(1, 1, 1), dbCumulativeSnapshot(1001, 2, 2),
			];
			snapshots[snapshotIndex].snapshot[field] = null;
			const evidence = validateActivityAttribution(
				...snapshots, 1, 1000, approvedActivitySignature(1, 1000)
			);
			assert.equal(evidence.adoptable, false, `snapshot ${snapshotIndex}.${field} null was adopted`);
		}
	}

	for (const invalid of ['0', [], {}, undefined]) {
		const before = dbSnapshot();
		before.snapshot.externalActiveSessions = invalid;
		assert.equal(validateDbWindow(before, dbSnapshot({ measured: true }), 'none').adoptable, false);
		const snapshots = [
			dbCumulativeSnapshot(0, 0, 0), dbCumulativeSnapshot(1, 1, 1),
			dbCumulativeSnapshot(1, 1, 1), dbCumulativeSnapshot(1001, 2, 2),
		];
		snapshots[0].snapshot.externalActiveSessionsAllDatabases = invalid;
		assert.equal(validateActivityAttribution(
			...snapshots, 1, 1000, approvedActivitySignature(1, 1000)
		).adoptable, false);
	}
});

test('actual pg_stat_statements evidence requires strict availability and exact snapshot schemas', async () => {
	const { validateDbWindow } = await import(
		`${pathToFileURL(requiredPath('lib/validate-db-window.mjs')).href}?pgss-schema=${Date.now()}`
	);
	const { validateActivityAttribution } = await import(
		`${pathToFileURL(requiredPath('lib/validate-activity-attribution.mjs')).href}?pgss-schema=${Date.now()}`
	);
	for (const malformed of [
		{ available: 'true', statements: [statementSnapshot(0)] },
		{ available: true, statements: [] },
		{ available: false, reason: '', statements: [] },
		{ available: false, reason: 'not installed', statements: [statementSnapshot(0)] },
		{ available: true, statements: [{ ...statementSnapshot(0), unexpected: true }] },
	]) {
		const before = dbSnapshot();
		const after = dbSnapshot({ measured: true });
		before.pgStatStatements = structuredClone(malformed);
		after.pgStatStatements = structuredClone(malformed);
		assert.equal(validateDbWindow(before, after, 'none').adoptable, false, JSON.stringify(malformed));
	}
	const driftBefore = dbCumulativeSnapshot(1, 1, 1);
	const driftAfter = dbCumulativeSnapshot(1001, 2, 2);
	driftAfter.pgStatStatements = { available: false, reason: 'extension unavailable', statements: [] };
	const driftEvidence = validateDbWindow(driftBefore, driftAfter, 'none');
	assert.equal(driftEvidence.adoptable, false);
	assert.match(JSON.stringify(driftEvidence.failures), /availability/);

	for (const snapshotIndex of [0, 1, 2, 3]) {
		const snapshots = [
			dbCumulativeSnapshot(0, 0, 0), dbCumulativeSnapshot(1, 1, 1),
			dbCumulativeSnapshot(1, 1, 1), dbCumulativeSnapshot(1001, 2, 2),
		];
		snapshots[snapshotIndex].pgStatStatements.available = 'true';
		const evidence = validateActivityAttribution(
			...snapshots, 1, 1000, approvedActivitySignature(1, 1000)
		);
		assert.equal(evidence.adoptable, false, `snapshot ${snapshotIndex} string availability was adopted`);
	}

	const unavailableSignature = approvedActivitySignature(1, 1000);
	for (const phase of ['warmup', 'measured']) unavailableSignature.windows[phase].pgStatStatements = { available: false, statements: [] };
	const validUnavailable = [0, 1, 1, 1001].map((count, index) => dbCumulativeSnapshot(count, index === 0 ? 0 : index < 3 ? 1 : 2, index));
	for (const snapshot of validUnavailable) snapshot.pgStatStatements = { available: false, reason: 'extension unavailable', statements: [] };
	assert.equal(validateActivityAttribution(
		...validUnavailable, 1, 1000, unavailableSignature
	).adoptable, true);
	const missingReason = validUnavailable.map((snapshot) => structuredClone(snapshot));
	missingReason[2].pgStatStatements.reason = '';
	assert.equal(validateActivityAttribution(
		...missingReason, 1, 1000, unavailableSignature
	).adoptable, false);
	const activityDrift = [
		dbCumulativeSnapshot(0, 0, 0), dbCumulativeSnapshot(1, 1, 1),
		dbCumulativeSnapshot(1, 1, 1), dbCumulativeSnapshot(1001, 2, 2),
	];
	activityDrift[1].pgStatStatements = { available: false, reason: 'extension unavailable', statements: [] };
	const activityDriftEvidence = validateActivityAttribution(
		...activityDrift, 1, 1000, approvedActivitySignature(1, 1000)
	);
	assert.equal(activityDriftEvidence.adoptable, false);
	assert.match(JSON.stringify(activityDriftEvidence.failures), /availability drift/);
});

test('devotion and retention reject a lock held by another scenario on the same Compose project', () => {
	const harness = createRunnerHarness();
	const lockDirectory = `/tmp/faithlog-performance-${harness.composeProject}.lock`;
	fs.mkdirSync(lockDirectory);
	try {
		for (const runner of ['devotion', 'retention']) {
			const result = harness.run(runner, { REJECTION_EVIDENCE_FILE: harness.rejectionPath(`held-lock-${runner}.json`) });
			assert.notEqual(result.status, 0, `${runner} unexpectedly acquired a held project lock`);
			assert.match(result.stderr, /Another performance|parallel/);
		}
		assert.equal(fs.existsSync(lockDirectory), true, 'a lock not owned by the runner must remain');
		assert.equal(harness.log().some(isDatabaseOrLoadCommand), false);
	} finally {
		fs.rmdirSync(lockDirectory);
		harness.cleanup();
	}
});

test('devotion rejects a BASE_URL not mapped to the inspected app before DB or k6', () => {
	const harness = createRunnerHarness();
	try {
		const result = harness.run('devotion', { BASE_URL: 'http://127.0.0.1:9999' });
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /published port/);
		assert.equal(harness.log().some(isDatabaseOrLoadCommand), false);
	} finally {
		harness.cleanup();
	}
});

test('devotion rejects same-project containers with unapproved Compose service roles before DB or k6', () => {
	for (const overrides of [
		{ FAKE_APP_SERVICE: 'wrong-app-service' },
		{ FAKE_DB_SERVICE: 'wrong-db-service' },
	]) {
		const harness = createRunnerHarness();
		try {
			const result = harness.run('devotion', overrides);
			assert.notEqual(result.status, 0);
			assert.match(result.stderr, /Compose service.*mismatch|service role/i);
			assert.equal(harness.log().some(isDatabaseOrLoadCommand), false);
		} finally {
			harness.cleanup();
		}
	}
});

test('runner preserves the first machine-readable rejection and disables automatic adoption', () => {
	const harness = createRunnerHarness();
	try {
		const first = harness.run('devotion', { FAKE_APP_SERVICE: 'wrong-app-service' });
		assert.notEqual(first.status, 0);
		const firstEvidence = harness.rejection();
		assert.equal(firstEvidence.automaticAdoption, false);
		assert.equal(firstEvidence.status, 'rejected');
		assert.equal(firstEvidence.stage, 'prelock-identity');
		const second = harness.run('devotion', { FAKE_DB_SERVICE: 'wrong-db-service' });
		assert.notEqual(second.status, 0);
		assert.deepEqual(harness.rejection(), firstEvidence);
	} finally {
		harness.cleanup();
	}
});

test('retention binds approved Compose services before DB access', () => {
	for (const overrides of [
		{ FAKE_APP_SERVICE: 'wrong-app-service' },
		{ FAKE_DB_SERVICE: 'wrong-db-service' },
	]) {
		const harness = createRunnerHarness();
		try {
			const result = harness.run('retention', overrides);
			assert.notEqual(result.status, 0);
			assert.match(result.stderr, /Compose service.*mismatch|service role/i);
			assert.equal(harness.log().some(isDatabaseOrLoadCommand), false);
		} finally {
			harness.cleanup();
		}
	}
});

test('retention rejects a container replaced after the project lock before candidate SQL', () => {
	const replaced = createRunnerHarness();
	try {
		const result = replaced.run('retention', { FAKE_REPLACE_DB_AFTER_PRELOCK: 'true' });
		assert.notEqual(result.status, 0);
		assert.match(result.stderr, /runtime identity|container.*changed|replaced/i);
		assert.equal(replaced.log().filter((line) => line === 'docker:exec-retention').length, 0);
	} finally {
		replaced.cleanup();
	}
});

test('retention rejects a PostgreSQL restart after candidate SQL before adoption', () => {
	const restarted = createRunnerHarness();
	try {
		const result = restarted.run('retention', { FAKE_RESTART_DB_AFTER_SQL: 'true' });
		assert.notEqual(result.status, 0, JSON.stringify({ stderr: result.stderr, log: restarted.log() }));
		assert.match(result.stderr, /runtime identity|postmaster|replaced/i);
		assert.equal(restarted.log().filter((line) => line === 'docker:exec-retention').length, 1);
	} finally {
		restarted.cleanup();
	}
});

function devotionManifest() {
	const referenceDate = seoulToday();
	return {
		scenarioType: 'devotion-write', datasetId: 'PERFORMANCE_ISSUE197_CONTRACT', fixtureRunId: `ISSUE197_CONTRACT_${process.pid}`,
		referenceDate, campusId: 101, rollbackCampusId: 102,
		warmupWeekStartDate: mondayRelativeTo(referenceDate, 1), measuredWeekStartDate: mondayRelativeTo(referenceDate, 2),
		rollbackWeekStartDate: mondayRelativeTo(referenceDate, -1), expectedMeasuredUserCount: 1000, expectedPenaltyAmount: 4300,
		warmupUserIds: [1], measuredUserIds: Array.from({ length: 1000 }, (_, index) => index + 1001), rollbackUserIds: [3001],
	};
}

function devotionCredentials(manifest, expiry) {
	return {
		fixtureRunId: manifest.fixtureRunId,
		tokens: [...manifest.warmupUserIds, ...manifest.measuredUserIds, ...manifest.rollbackUserIds]
			.map((userId) => ({ userId, accessToken: jwt(userId, expiry) })),
	};
}

function validWorkload() {
	return {
		warmupVus: 1, measuredVus: 1, rollbackVus: 1,
		warmupMaxDuration: '1s', measuredMaxDuration: '1s', rollbackMaxDuration: '1s', tokenTtlSafetySeconds: 1,
	};
}

function jwt(userId, exp) {
	const encode = (value) => Buffer.from(JSON.stringify(value)).toString('base64url');
	return `${encode({ alg: 'HS256', typ: 'JWT' })}.${encode({ sub: String(userId), userId, exp, tokenType: 'ACCESS' })}.contract-signature`;
}

function preflightEvidence(manifest) {
	return {
		distinctFixtureUsers: 1002, activeFixtureUsers: 1002, activeCampuses: 2,
		successActiveMembers: 1001, rollbackActiveMembers: 1, successUsersInRollbackCampus: 0, rollbackUsersInSuccessCampus: 0,
		successActivePenaltyAccounts: 1, rollbackActivePenaltyAccounts: 0,
		existingWeeklyCount: 0, existingDailyCount: 0, existingDevotionCharges: 0,
		activePenaltyRuleCount: 4, invalidActivePenaltyRulePairs: 0, calculatedPenaltyAmount: manifest.expectedPenaltyAmount,
	};
}

function summary(phase, transactions) {
	return { metrics: {
		[`devotion_weekly_${phase}`]: { 'p(50)': 10, 'p(95)': 20, 'p(99)': 30, max: 40 },
		[`devotion_weekly_${phase}_failure`]: { passes: 0, fails: transactions, value: 0 },
		[`devotion_weekly_${phase}_transactions`]: { count: transactions, rate: 10 },
		iterations: { count: transactions, rate: 10 },
	} };
}

function summaryWithShape(phase, transactions, shape, overrides = {}) {
	const direct = summary(phase, transactions);
	const trend = direct.metrics[`devotion_weekly_${phase}`];
	trend['p(50)'] = overrides.p50 ?? trend['p(50)'];
	trend['p(95)'] = overrides.p95 ?? trend['p(95)'];
	trend['p(99)'] = overrides.p99 ?? trend['p(99)'];
	trend.max = overrides.max ?? trend.max;
	direct.metrics.iterations.rate = overrides.throughput ?? direct.metrics.iterations.rate;
	if (shape === 'direct') return direct;
	return { metrics: Object.fromEntries(Object.entries(direct.metrics).map(([name, value]) => [name, { values: value }])) };
}

function resourceWindowConfig() {
	return {
		samplingIntervalSeconds: 1,
		maxGapSeconds: 2,
		measuredStart: '2026-07-14T00:00:01.000Z',
		measuredEnd: '2026-07-14T00:00:03.000Z',
		appContainerId: APP_CONTAINER_ID,
		databaseContainerId: DB_CONTAINER_ID,
		redisContainerId: REDIS_CONTAINER_ID,
	};
}

function validResourceSamples() {
	return [
		{ observedAt: '2026-07-14T00:00:00.900Z', role: 'app', containerId: APP_CONTAINER_ID, cpuPercent: 1.5, memoryBytes: 1024 },
		{ observedAt: '2026-07-14T00:00:00.925Z', role: 'database', containerId: DB_CONTAINER_ID, cpuPercent: 2.5, memoryBytes: 2048 },
		{ observedAt: '2026-07-14T00:00:00.950Z', role: 'redis', containerId: REDIS_CONTAINER_ID, cpuPercent: 0.5, memoryBytes: 512 },
		{ observedAt: '2026-07-14T00:00:02.000Z', role: 'app', containerId: APP_CONTAINER_ID, cpuPercent: 3.5, memoryBytes: 3072 },
		{ observedAt: '2026-07-14T00:00:02.025Z', role: 'database', containerId: DB_CONTAINER_ID, cpuPercent: 4.5, memoryBytes: 4096 },
		{ observedAt: '2026-07-14T00:00:02.050Z', role: 'redis', containerId: REDIS_CONTAINER_ID, cpuPercent: 1.5, memoryBytes: 1536 },
		{ observedAt: '2026-07-14T00:00:03.100Z', role: 'app', containerId: APP_CONTAINER_ID, cpuPercent: 5.5, memoryBytes: 5120 },
		{ observedAt: '2026-07-14T00:00:03.125Z', role: 'database', containerId: DB_CONTAINER_ID, cpuPercent: 6.5, memoryBytes: 6144 },
		{ observedAt: '2026-07-14T00:00:03.150Z', role: 'redis', containerId: REDIS_CONTAINER_ID, cpuPercent: 2.5, memoryBytes: 2560 },
	];
}

function runtimeIdentity() {
	return {
		app: {
			containerId: APP_CONTAINER_ID, imageId: `sha256:${'1'.repeat(64)}`, startedAt: '2026-07-13T23:00:00.000Z',
			composeProject: 'faithlog-perf-197-contract', composeService: 'app', publishedPort: 28080,
			revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
			jarSha256: '1111111111111111111111111111111111111111111111111111111111111111',
			apiContractSha256: '2222222222222222222222222222222222222222222222222222222222222222',
		},
		databaseContainer: {
			containerId: DB_CONTAINER_ID, imageId: `sha256:${'4'.repeat(64)}`, startedAt: '2026-07-13T23:00:00.000Z',
			composeProject: 'faithlog-perf-197-contract', composeService: 'postgres',
		},
		redisContainer: {
			containerId: REDIS_CONTAINER_ID, imageId: `sha256:${'5'.repeat(64)}`, startedAt: '2026-07-13T23:00:00.000Z',
			composeProject: 'faithlog-perf-197-contract', composeService: 'redis',
		},
		databaseServer: {
			currentDatabase: 'faithlog', serverAddress: '172.20.0.2', serverPort: 5432,
			postmasterStartTime: '2026-07-13T23:00:01.000Z',
			flywayVersion: '11', flywayScript: 'V11__secure_supabase_data_api.sql', flywayChecksum: '-123456789',
		},
		redisServer: { runId: 'd'.repeat(40), redisVersion: '7.4.2', tcpPort: 6379 },
	};
}

function runtimeIdentityCheckpoints(identity) {
	return Object.fromEntries(['warmupBefore', 'measuredBefore', 'measuredAfter', 'final']
		.map((checkpoint) => [checkpoint, structuredClone(identity)]));
}

function dbSnapshot({ measured = false } = {}) {
	const inserts = { weekly_devotion_records: 1000, devotion_daily_checks: 7000, charge_items: 1000 };
	const tableNames = ['users', 'campuses', 'campus_members', 'penalty_rules', 'payment_accounts', 'weekly_devotion_records', 'devotion_daily_checks', 'charge_items'];
	const database = {
		datname: 'faithlog', stats_reset: null,
		xact_commit: measured ? '1001' : '0', xact_rollback: '0', blks_read: '0', blks_hit: measured ? '100' : '0',
		tup_returned: measured ? '100' : '0', tup_fetched: measured ? '100' : '0',
		tup_inserted: measured ? '9000' : '0', tup_updated: '0', tup_deleted: '0',
	};
	return {
		snapshot: {
			capturedAt: measured ? '2026-07-14T00:01:00.000Z' : '2026-07-14T00:00:00.000Z',
			observerOverhead: {
				databaseWideCountersIncludeSnapshotTransaction: true,
				databaseWideDeltaIsExactQueryCount: false,
				appTableCountersReadApplicationTables: false,
			},
			externalActiveSessions: 0,
			externalActiveSessionsAllDatabases: 0,
			database,
			allDatabases: [structuredClone(database), {
				datname: 'postgres', stats_reset: null,
				xact_commit: '0', xact_rollback: '0', blks_read: '0', blks_hit: '0',
				tup_returned: '0', tup_fetched: '0', tup_inserted: '0', tup_updated: '0', tup_deleted: '0',
			}],
			tables: tableNames.map((relname) => ({
				relname, seq_scan: '0', seq_tup_read: '0', idx_scan: measured ? '1' : '0', idx_tup_fetch: measured ? '1' : '0',
				n_tup_ins: String(measured ? (inserts[relname] || 0) : 0), n_tup_upd: '0', n_tup_del: '0',
				n_mod_since_analyze: String(measured ? (inserts[relname] || 0) : 0),
				last_analyze: null, last_autoanalyze: null, analyze_count: 0, autoanalyze_count: 0,
				last_vacuum: null, last_autovacuum: null, vacuum_count: 0, autovacuum_count: 0,
			})),
			plannerSettings: plannerSettings(),
		},
		pgStatStatements: { available: false, reason: 'not installed', statements: [] },
	};
}

function plannerSettings() {
	return [
		'enable_bitmapscan', 'enable_hashagg', 'enable_hashjoin', 'enable_indexonlyscan', 'enable_indexscan',
		'enable_material', 'enable_mergejoin', 'enable_nestloop', 'enable_seqscan', 'jit', 'plan_cache_mode',
		'random_page_cost', 'work_mem',
	].sort().map((name) => ({ name, setting: 'on', unit: null, source: 'default' }));
}

function statementSnapshot(calls) {
	return { query: 'select * from weekly_devotion_records where user_id = $1', calls: String(calls), rows: String(calls), totalExecTime: calls, sharedBlksHit: String(calls), sharedBlksRead: '0' };
}

function dbCumulativeSnapshot(userCount, observerCommits, minute) {
	const snapshot = dbSnapshot();
	snapshot.snapshot.capturedAt = `2026-07-14T00:${String(minute).padStart(2, '0')}:00.000Z`;
	snapshot.snapshot.database.xact_commit = String(userCount + observerCommits);
	snapshot.snapshot.database.tup_inserted = String(userCount * 9);
	snapshot.snapshot.database.blks_hit = String(userCount * 100);
	snapshot.snapshot.database.tup_returned = String(userCount * 100);
	snapshot.snapshot.database.tup_fetched = String(userCount * 100);
	snapshot.snapshot.allDatabases[0] = structuredClone(snapshot.snapshot.database);
	const insertsPerUser = { weekly_devotion_records: 1, devotion_daily_checks: 7, charge_items: 1 };
	for (const table of snapshot.snapshot.tables) {
		table.idx_scan = String(userCount);
		table.idx_tup_fetch = String(userCount);
		table.n_tup_ins = String(userCount * (insertsPerUser[table.relname] || 0));
		table.n_mod_since_analyze = table.n_tup_ins;
	}
	snapshot.pgStatStatements = { available: true, statements: [statementSnapshot(userCount)] };
	return snapshot;
}

function losslessCumulativeSnapshot(userCount, observerCommits, minute) {
	const evidence = dbCumulativeSnapshot(userCount, observerCommits, minute);
	const base = 9007199254740992n;
	const databaseFields = ['xact_commit', 'xact_rollback', 'blks_read', 'blks_hit', 'tup_returned', 'tup_fetched', 'tup_inserted', 'tup_updated', 'tup_deleted'];
	const tableFields = ['seq_scan', 'seq_tup_read', 'idx_scan', 'idx_tup_fetch', 'n_tup_ins', 'n_tup_upd', 'n_tup_del', 'n_mod_since_analyze'];
	for (const database of [evidence.snapshot.database, ...evidence.snapshot.allDatabases]) {
		for (const field of databaseFields) database[field] = (base + BigInt(database[field])).toString();
	}
	for (const table of evidence.snapshot.tables) {
		for (const field of tableFields) table[field] = (base + BigInt(table[field])).toString();
	}
	for (const statement of evidence.pgStatStatements.statements) {
		for (const field of ['calls', 'rows', 'sharedBlksHit', 'sharedBlksRead']) {
			statement[field] = (base + BigInt(statement[field])).toString();
		}
	}
	return evidence;
}

function setCumulativeUpdates(snapshot, weeklyUpdates, dailyUpdates) {
	snapshot.snapshot.tables.find((table) => table.relname === 'weekly_devotion_records').n_tup_upd = String(weeklyUpdates);
	snapshot.snapshot.tables.find((table) => table.relname === 'devotion_daily_checks').n_tup_upd = String(dailyUpdates);
}

function setDuplicateStatements(snapshot, firstCalls, secondCalls) {
	const query = statementSnapshot(0).query;
	snapshot.pgStatStatements.statements = [
		{ ...statementSnapshot(firstCalls), query },
		{ ...statementSnapshot(secondCalls), query },
	];
}

function approvedActivitySignature(warmupUsers, measuredUsers, { weeklyUpdatesPerUser = 0, dailyUpdatesPerUser = 0 } = {}) {
	const insertsPerUser = { weekly_devotion_records: 1, devotion_daily_checks: 7, charge_items: 1 };
	const tables = dbSnapshot().snapshot.tables.map(({ relname }) => [relname, {
		seq_scan: 0, seq_tup_read: 0, idx_scan: 1, idx_tup_fetch: 1,
		n_tup_ins: insertsPerUser[relname] || 0,
		n_tup_upd: relname === 'weekly_devotion_records' ? weeklyUpdatesPerUser : relname === 'devotion_daily_checks' ? dailyUpdatesPerUser : 0,
		n_tup_del: 0, n_mod_since_analyze: insertsPerUser[relname] || 0,
	}]);
	const window = (users) => ({
		applicationCommits: String(users),
		rollbacks: '0',
		tableDeltas: Object.fromEntries(tables.map(([relname, counters]) => [relname,
			Object.fromEntries(Object.entries(counters).map(([field, value]) => [field, String(value * users)]))
		])),
		pgStatStatements: {
			available: true,
			statements: [{ query: statementSnapshot(0).query, calls: String(users), rows: String(users) }],
		},
	});
	return {
		schemaVersion: 1,
		datasetId: 'PERFORMANCE_ISSUE197_CONTRACT',
		fixtureRunId: `ISSUE197_CONTRACT_${process.pid}`,
		databaseName: 'faithlog',
		warmupUsers,
		measuredUsers,
		windows: { warmup: window(warmupUsers), measured: window(measuredUsers) },
	};
}

function createRunnerHarness() {
	const temporaryDirectory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-runner-'));
	const fakeBin = path.join(temporaryDirectory, 'bin');
	const fakeLog = path.join(temporaryDirectory, 'commands.log');
	const composeProject = `faithlog-perf-197-contract-${process.pid}-${path.basename(temporaryDirectory).replace(/[^A-Za-z0-9._-]/g, '-')}`;
	const sourceWorktree = path.join(temporaryDirectory, 'source-worktree');
	const apiTreeInventory = '100644 blob 1111111111111111111111111111111111111111\tsrc/main/java/com/faithlog/faithlog/domain/devotion/DevotionService.java';
	const apiContractSha256 = createHash('sha256').update(`${apiTreeInventory}\n`).digest('hex');
	fs.mkdirSync(fakeBin);
	fs.mkdirSync(sourceWorktree);
	const manifest = devotionManifest();
	const manifestPath = writeJson(temporaryDirectory, 'devotion-manifest.json', manifest);
	const credentialsPath = writeJson(temporaryDirectory, 'devotion-credentials.json', devotionCredentials(manifest, Math.floor(Date.now() / 1000) + 3600));
	const attributionSignaturePath = writeJson(temporaryDirectory, 'activity-signature.json', approvedActivitySignature(1, 1000));
	fs.chmodSync(credentialsPath, 0o600);
	const retentionFixtureRunId = `ISSUE197_RETENTION_${process.pid}`;
	const retentionPath = writeJson(temporaryDirectory, 'retention-manifest.json', {
		scenarioType: 'retention-dry-verify-only', datasetId: 'PERFORMANCE_ISSUE197_RETENTION', fixtureRunId: retentionFixtureRunId,
		datasetPrefix: `PERFORMANCE_ISSUE197_RETENTION_${retentionFixtureRunId}`, referenceInstant: '2027-01-31T15:00:00Z',
		expectedDeleteCounts: {
			notificationLogs: 1, pollResponseOptions: 1, pollResponses: 1, pollComments: 1, pollOptions: 1, polls: 1,
			softDeletedPollComments: 1, prayerSubmissions: 1, devotionDailyChecks: 1, weeklyDevotionRecords: 1, chargeItems: 1,
		},
	});
	const runtimeIdentityPath = writeJson(temporaryDirectory, 'runtime-identity.json', {
		currentDatabase: 'faithlog', serverAddress: '127.0.0.1', serverPort: 5432,
		postmasterStartTime: '2026-07-13T23:00:01.000Z',
		flywayVersion: '11', flywayScript: 'V11__secure_supabase_data_api.sql', flywayChecksum: '-123456789',
	});
	const retentionEvidencePath = writeJson(temporaryDirectory, 'retention-evidence.json', {
		datasetId: 'PERFORMANCE_ISSUE197_RETENTION', fixtureRunId: retentionFixtureRunId,
		actualCandidateCounts: {
			notificationLogs: 1, pollResponseOptions: 1, pollResponses: 1, pollComments: 1, pollOptions: 1, polls: 1,
			softDeletedPollComments: 1, prayerSubmissions: 1, devotionDailyChecks: 1, weeklyDevotionRecords: 1, chargeItems: 1,
		},
		annualForeignKeyBlockers: 0, outsideFixtureCandidateRoots: 0,
	});
	const rejectionPath = path.join(temporaryDirectory, 'first-rejection.json');
	let invocationCount = 0;

	fs.writeFileSync(path.join(fakeBin, 'node'), `#!/usr/bin/env bash
if [[ "\${1:-}" == '--test' ]]; then exit 0; fi
exec ${shellQuote(process.execPath)} "$@"
`);
	fs.writeFileSync(path.join(fakeBin, 'k6'), `#!/usr/bin/env bash
printf 'k6:%s\n' "$*" >> "$FAKE_LOG"
exit 1
`);
fs.writeFileSync(path.join(fakeBin, 'git'), `#!/usr/bin/env bash
if [[ "$*" == *" status --porcelain=v1 --untracked-files=all"* ]]; then exit 0; fi
if [[ "$*" == *" symbolic-ref -q HEAD"* ]]; then exit 1; fi
if [[ "$*" == *" rev-parse HEAD"* ]]; then printf '%s\n' "$EXPECTED_APP_REVISION"; exit 0; fi
if [[ "$*" == *" reflog --date=iso-strict"* ]]; then printf '2026-07-16T04:19:29.000Z\tHEAD@{2026-07-16T04:20:28.000Z}\t\n'; exit 0; fi
if [[ "$*" == *" ls-tree -r "* ]]; then printf '%s\n' "$FAKE_API_TREE_INVENTORY"; exit 0; fi
exit 1
`);
fs.writeFileSync(path.join(fakeBin, 'docker'), `#!/usr/bin/env bash
printf 'docker:%s\n' "$*" >> "$FAKE_LOG"
if [[ "\${1:-}" == image && "\${2:-}" == inspect ]]; then printf '2026-07-16T04:22:48.810Z\n'; exit 0; fi
if [[ "\${1:-}" == inspect ]]; then
	target="\${@: -1}"
  if [[ "$*" == *com.docker.compose.project.working_dir* ]]; then printf '%s\n' "$APP_SOURCE_WORKTREE";
	elif [[ "$*" == *com.docker.compose.project* ]]; then printf '%s\n' "$FAKE_COMPOSE_PROJECT";
  elif [[ "$*" == *com.docker.compose.service* ]]; then
	  if [[ "$target" == "$FAKE_APP_CONTAINER" ]]; then printf '%s\n' "$FAKE_APP_SERVICE";
	  elif [[ "$target" == "$FAKE_DB_CONTAINER" ]]; then printf '%s\n' "$FAKE_DB_SERVICE"; else printf '%s\n' "$FAKE_REDIS_SERVICE"; fi
  elif [[ "$*" == *"{{.Id}}"* ]]; then
	if [[ "$target" == "$FAKE_APP_CONTAINER" ]]; then printf '%s\n' "$FAKE_APP_CONTAINER_ID";
	elif [[ "$target" == "$FAKE_DB_CONTAINER" ]]; then
	  count=0; [[ -f "$FAKE_DB_ID_COUNT_FILE" ]] && count="$(<"$FAKE_DB_ID_COUNT_FILE")"; count=$((count + 1)); printf '%s' "$count" > "$FAKE_DB_ID_COUNT_FILE"
	  if [[ "\${FAKE_REPLACE_DB_AFTER_PRELOCK:-false}" == true && "$count" -ge 2 ]]; then printf '%s\n' "$FAKE_REPLACEMENT_CONTAINER_ID"; else printf '%s\n' "$FAKE_DB_CONTAINER_ID"; fi
	else printf '%s\n' "$FAKE_REDIS_CONTAINER_ID";
	fi
  elif [[ "$*" == *"{{.Image}}"* ]]; then
	if [[ "$target" == "$FAKE_APP_CONTAINER" ]]; then printf '%s\n' "$EXPECTED_APP_IMAGE_ID";
	elif [[ "$target" == "$FAKE_DB_CONTAINER" ]]; then printf '%s\n' "$EXPECTED_DB_IMAGE_ID"; else printf '%s\n' "$EXPECTED_REDIS_IMAGE_ID"; fi
  elif [[ "$*" == *"{{.State.StartedAt}}"* ]]; then printf '2026-07-13T23:00:00.000Z\n';
  elif [[ "$*" == *NetworkSettings.Ports* ]]; then printf '28080\n';
  elif [[ "$*" == *Config.Env* ]]; then
	printf 'FAITHLOG_SCHEDULER_ENABLED=false\nSPRING_PROFILES_ACTIVE=docker\nSPRING_DATASOURCE_URL=jdbc:postgresql://contract-db-service:5432/faithlog\nSPRING_DATASOURCE_USERNAME=faithlog\nSPRING_DATA_REDIS_HOST=contract-redis-service\nSPRING_DATA_REDIS_PORT=6379\n'
  fi
  exit 0
fi
if [[ "\${1:-}" == exec ]]; then
	if [[ "$*" == *"sha256sum /app/app.jar"* ]]; then printf '%s  /app/app.jar\n' "$EXPECTED_APP_JAR_SHA256"; exit 0; fi
	if [[ "$*" == *"redis-cli -h 127.0.0.1 -p 6379 --raw INFO server"* ]]; then
	  printf 'redis_version:7.4.2\nrun_id:dddddddddddddddddddddddddddddddddddddddd\ntcp_port:6379\n'; exit 0
	fi
	input="$(</dev/stdin)"
	if [[ "$input" == *pg_postmaster_start_time* ]]; then
	  printf 'docker:exec-identity\n' >> "$FAKE_LOG"
	  count=0; [[ -f "$FAKE_IDENTITY_COUNT_FILE" ]] && count="$(<"$FAKE_IDENTITY_COUNT_FILE")"; count=$((count + 1)); printf '%s' "$count" > "$FAKE_IDENTITY_COUNT_FILE"
	  if [[ "\${FAKE_RESTART_DB_AFTER_SQL:-false}" == true && -f "$FAKE_CANDIDATE_EXECUTED_FILE" ]]; then
	    printf '{"currentDatabase":"faithlog","serverAddress":"127.0.0.1","serverPort":5432,"postmasterStartTime":"2026-07-14T00:00:00.000Z","flywayVersion":"11","flywayScript":"V11__secure_supabase_data_api.sql","flywayChecksum":"-123456789"}\n'
	  else
	    command cat "$FAKE_RUNTIME_IDENTITY_FILE"
	  fi
	else
	  printf 'docker:exec-retention\n' >> "$FAKE_LOG"
	  : > "$FAKE_CANDIDATE_EXECUTED_FILE"
	  command cat "$FAKE_RETENTION_EVIDENCE_FILE"
	fi
	exit 0
fi
exit 1
`);
	for (const name of ['node', 'k6', 'git', 'docker']) fs.chmodSync(path.join(fakeBin, name), 0o755);

	const baseEnvironment = {
		...process.env, PATH: `${fakeBin}:${process.env.PATH}`, FAKE_LOG: fakeLog, FAKE_COMPOSE_PROJECT: composeProject,
		FAKE_API_TREE_INVENTORY: apiTreeInventory, APP_SOURCE_WORKTREE: sourceWorktree,
		FAKE_APP_CONTAINER: 'contract-app', FAKE_DB_CONTAINER: 'contract-postgres', FAKE_REDIS_CONTAINER: 'contract-redis',
		FAKE_APP_SERVICE: 'contract-app-service', FAKE_DB_SERVICE: 'contract-db-service', FAKE_REDIS_SERVICE: 'contract-redis-service',
		FAKE_APP_CONTAINER_ID: APP_CONTAINER_ID, FAKE_DB_CONTAINER_ID: DB_CONTAINER_ID,
		FAKE_REDIS_CONTAINER_ID: REDIS_CONTAINER_ID, FAKE_REPLACEMENT_CONTAINER_ID: REPLACEMENT_CONTAINER_ID,
		FAKE_DB_ID_COUNT_FILE: path.join(temporaryDirectory, 'db-id-count'), FAKE_IDENTITY_COUNT_FILE: path.join(temporaryDirectory, 'identity-count'),
		FAKE_CANDIDATE_EXECUTED_FILE: path.join(temporaryDirectory, 'candidate-executed'),
		FAKE_RUNTIME_IDENTITY_FILE: runtimeIdentityPath, FAKE_RETENTION_EVIDENCE_FILE: retentionEvidencePath,
		FIXTURE_MANIFEST: manifestPath, CREDENTIALS_FILE: credentialsPath, ATTRIBUTION_SIGNATURE_FILE: attributionSignaturePath, RETENTION_MANIFEST: retentionPath,
		APP_CONTAINER: 'contract-app', DB_CONTAINER: 'contract-postgres', REDIS_CONTAINER: 'contract-redis', EXPECTED_COMPOSE_PROJECT: composeProject,
		EXPECTED_APP_COMPOSE_SERVICE: 'contract-app-service', EXPECTED_DB_COMPOSE_SERVICE: 'contract-db-service', EXPECTED_REDIS_COMPOSE_SERVICE: 'contract-redis-service',
		EXPECTED_APP_REVISION: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		EXPECTED_APP_IMAGE_ID: 'sha256:aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa',
		EXPECTED_APP_JAR_SHA256: 'bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb',
		EXPECTED_API_CONTRACT_SHA256: apiContractSha256,
		EXPECTED_DB_IMAGE_ID: `sha256:${'4'.repeat(64)}`, EXPECTED_REDIS_IMAGE_ID: `sha256:${'5'.repeat(64)}`,
		EXPECTED_FLYWAY_VERSION: '11', EXPECTED_FLYWAY_SCRIPT: 'V11__secure_supabase_data_api.sql', EXPECTED_FLYWAY_CHECKSUM: '-123456789',
		DB_HOST: '127.0.0.1', REDIS_HOST: '127.0.0.1',
		EXPECTED_DB_PORT: '5432', EXPECTED_REDIS_PORT: '6379',
		REJECTION_EVIDENCE_FILE: rejectionPath,
		DB_NAME: 'faithlog', DB_USER: 'faithlog', BASE_URL: 'http://127.0.0.1:28080',
		WARMUP_VUS: '1', MEASURED_VUS: '1', ROLLBACK_VUS: '1',
		WARMUP_MAX_DURATION: '1s', MEASURED_MAX_DURATION: '1s', ROLLBACK_MAX_DURATION: '1s',
		TOKEN_TTL_SAFETY_SECONDS: '1', RESOURCE_SAMPLE_INTERVAL_SECONDS: '1', RESOURCE_SAMPLE_MAX_GAP_SECONDS: '2',
		EXTERNAL_ACTIVITY: 'none', ALLOW_ISOLATED_RETENTION: 'true',
	};

	return {
		composeProject,
		run(kind, overrides = {}) {
			invocationCount += 1;
			return spawnSync('bash', [path.join(ISSUE_DIR, kind === 'devotion' ? 'run-devotion-baseline.sh' : 'run-retention-dry-verify.sh')], {
				cwd: REPO_ROOT,
				encoding: 'utf8',
				env: { ...baseEnvironment, PERF_REPORT_ROOT: path.join(temporaryDirectory, 'reports', String(invocationCount)), ...overrides },
			});
		},
		log() { return fs.existsSync(fakeLog) ? fs.readFileSync(fakeLog, 'utf8').trim().split('\n').filter(Boolean) : []; },
		rejection() { return JSON.parse(fs.readFileSync(rejectionPath, 'utf8')); },
		rejectionPath(name) { return path.join(temporaryDirectory, name); },
		cleanup() {
			fs.rmSync(temporaryDirectory, { recursive: true, force: true });
		},
	};
}

function writeJson(directory, name, value) {
	const target = path.join(directory, name);
	fs.writeFileSync(target, JSON.stringify(value));
	return target;
}

function shellQuote(value) { return `'${value.replaceAll("'", "'\\''")}'`; }
function isDatabaseOrLoadCommand(line) {
	return line.startsWith('k6:') || (line.startsWith('docker:exec') && !line.includes('sha256sum /app/app.jar'));
}
function seoulToday() {
	return new Intl.DateTimeFormat('en-CA', { timeZone: 'Asia/Seoul', year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
}
function mondayRelativeTo(referenceDate, weekOffset) {
	const date = new Date(`${referenceDate}T00:00:00Z`);
	const daysSinceMonday = (date.getUTCDay() + 6) % 7;
	date.setUTCDate(date.getUTCDate() - daysSinceMonday + weekOffset * 7);
	return date.toISOString().slice(0, 10);
}
