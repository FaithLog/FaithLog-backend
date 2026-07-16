import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { validateSummary } from '../lib/validate-k6-summary.mjs';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPOSITORY_ROOT = path.resolve(ISSUE_DIR, '..', '..', '..');
const id = (character) => character.repeat(64);
const image = (character) => `sha256:${id(character)}`;

const issueFile = (relativePath) => fs.readFileSync(path.join(ISSUE_DIR, relativePath), 'utf8');

test('runtime contract requires Redis, all service images, and exact Flyway identity with no fallback', () => {
	for (const runnerName of ['run-devotion-baseline.sh', 'run-retention-dry-verify.sh']) {
		const runner = issueFile(runnerName);
		for (const variable of [
			'REDIS_CONTAINER', 'EXPECTED_REDIS_COMPOSE_SERVICE', 'EXPECTED_DB_IMAGE_ID', 'EXPECTED_REDIS_IMAGE_ID',
			'EXPECTED_FLYWAY_VERSION', 'EXPECTED_FLYWAY_SCRIPT', 'EXPECTED_FLYWAY_CHECKSUM',
			'DB_HOST', 'REDIS_HOST', 'EXPECTED_DB_PORT', 'EXPECTED_REDIS_PORT', 'REJECTION_EVIDENCE_FILE',
		]) {
			assert.match(runner, new RegExp(`\\b${variable}\\b`));
			assert.doesNotMatch(runner, new RegExp(`${variable.replaceAll('_', '\\_')}:-`));
		}
		assert.match(runner, /psql\s+-h "\$DB_HOST"/);
		assert.match(runner, /docker exec "\$REDIS_CONTAINER" redis-cli -h "\$REDIS_HOST" -p "\$EXPECTED_REDIS_PORT" --raw INFO server/);
	}
	const identitySql = issueFile('runtime-identity.sql');
	assert.match(identitySql, /flyway_schema_history/i);
	assert.match(identitySql, /ORDER BY installed_rank DESC/i);
});

test('pre/post-lock and final identity includes full app, DB, and Redis identity plus Flyway and Redis server continuity', async () => {
	const devotionRunner = issueFile('run-devotion-baseline.sh');
	for (const comparison of [
		'"$current_app_project" != "$app_compose_project"',
		'"$current_app_service" != "$app_compose_service"',
		'"$current_app_port" != "$app_published_port"',
		'"$current_db_project" != "$db_compose_project"',
		'"$current_db_service" != "$db_compose_service"',
	]) assert.match(devotionRunner, new RegExp(comparison.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	const validator = await import(`${pathToFileURL(path.join(ISSUE_DIR, 'lib/validate-runtime-identity.mjs')).href}?audit=${Date.now()}`);
	const identity = {
		app: {
			containerId: id('a'), imageId: image('1'), startedAt: '2026-07-16T00:00:00.000Z',
			composeProject: 'faithlog-perf-197-audit', composeService: 'app', publishedPort: 28080,
			revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97', jarSha256: id('2'), apiContractSha256: id('3'),
		},
		databaseContainer: {
			containerId: id('b'), imageId: image('4'), startedAt: '2026-07-16T00:00:00.000Z',
			composeProject: 'faithlog-perf-197-audit', composeService: 'postgres',
		},
		redisContainer: {
			containerId: id('c'), imageId: image('5'), startedAt: '2026-07-16T00:00:00.000Z',
			composeProject: 'faithlog-perf-197-audit', composeService: 'redis',
		},
		databaseServer: {
			currentDatabase: 'faithlog', serverAddress: '172.20.0.2', serverPort: 5432,
			postmasterStartTime: '2026-07-16T00:00:01.000Z', flywayVersion: '11',
			flywayScript: 'V11__secure_supabase_data_api.sql', flywayChecksum: '-123456789',
		},
		redisServer: { runId: 'd'.repeat(40), redisVersion: '7.4.2', tcpPort: 6379 },
	};
	assert.equal(validator.validateRuntimeIdentity(identity), identity);
	const checkpoints = Object.fromEntries(['warmupBefore', 'measuredBefore', 'measuredAfter', 'final']
		.map((checkpoint) => [checkpoint, structuredClone(identity)]));
	assert.equal(validator.validateRuntimeIdentitySeries(identity, checkpoints).adoptable, true);
	checkpoints.final.redisServer.runId = 'e'.repeat(40);
	const rejected = validator.validateRuntimeIdentitySeries(identity, checkpoints);
	assert.equal(rejected.adoptable, false);
	assert.match(JSON.stringify(rejected.failures), /redisServer\.runId/);
	assert.throws(() => validator.validateRuntimeIdentity({ ...identity, app: { ...identity.app, containerId: 'short-id' } }), /full Docker container ID/);
});

test('resource evidence requires the exact full-ID app, database, and Redis set across cadence window', async () => {
	const { validateResourceWindow } = await import(`${pathToFileURL(path.join(ISSUE_DIR, 'lib/validate-resource-window.mjs')).href}?audit=${Date.now()}`);
	const config = {
		samplingIntervalSeconds: 1, maxGapSeconds: 2,
		measuredStart: '2026-07-16T00:00:01.000Z', measuredEnd: '2026-07-16T00:00:03.000Z',
		appContainerId: id('a'), databaseContainerId: id('b'), redisContainerId: id('c'),
	};
	const samples = [];
	for (const [index, observedAt] of ['2026-07-16T00:00:00.900Z', '2026-07-16T00:00:02.000Z', '2026-07-16T00:00:03.100Z'].entries()) {
		for (const [roleIndex, [role, containerId]] of [['app', id('a')], ['database', id('b')], ['redis', id('c')]].entries()) {
			samples.push({ observedAt: new Date(Date.parse(observedAt) + roleIndex).toISOString(), role, containerId, cpuPercent: index + roleIndex, memoryBytes: 1024 + index + roleIndex });
		}
	}
	const evidence = validateResourceWindow(samples, config);
	assert.equal(evidence.adoptable, true, JSON.stringify(evidence.failures));
	assert.deepEqual(Object.keys(evidence.byRole).sort(), ['app', 'database', 'redis']);
	assert.equal(validateResourceWindow(samples.filter((sample) => sample.role !== 'redis'), config).adoptable, false);
	assert.equal(validateResourceWindow(samples.map((sample) => sample.role === 'redis' ? { ...sample, containerId: 'short-id' } : sample), config).adoptable, false);
});

test('the first machine-readable rejection is immutable and disables automatic adoption', async () => {
	const contractPath = path.join(ISSUE_DIR, 'lib/rejection-contract.mjs');
	assert.equal(fs.existsSync(contractPath), true, 'rejection contract must exist');
	const { writeFirstRejection } = await import(pathToFileURL(contractPath));
	const directory = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-197-rejection-'));
	try {
		const output = path.join(directory, 'first-rejection.json');
		writeFirstRejection(output, { scenario: 'devotion', stage: 'runtime-identity', exitCode: 1 }, '2026-07-16T00:00:00.000Z');
		writeFirstRejection(output, { scenario: 'devotion', stage: 'later-stage', exitCode: 2 }, '2026-07-16T00:01:00.000Z');
		const evidence = JSON.parse(fs.readFileSync(output, 'utf8'));
		assert.deepEqual(evidence, {
			schemaVersion: 1, scenario: 'devotion', status: 'rejected', automaticAdoption: false,
			stage: 'runtime-identity', exitCode: 1, rejectedAt: '2026-07-16T00:00:00.000Z',
		});
		assert.equal(fs.statSync(output).mode & 0o077, 0);
	} finally {
		fs.rmSync(directory, { recursive: true, force: true });
	}
});

test('k6 v2 direct and values Counter/Rate/Trend math is exact and fail-closed', () => {
	const metrics = (valuesShape) => {
		const wrap = (value) => valuesShape ? { values: value } : value;
		return { metrics: {
			devotion_weekly_measured: wrap({ 'p(50)': 1, 'p(95)': 2, 'p(99)': 3, max: 4 }),
			devotion_weekly_measured_failure: wrap({ rate: 0 }),
			devotion_weekly_measured_transactions: wrap({ count: 1000 }),
			iterations: wrap({ rate: 125.5 }),
		} };
	};
	for (const shape of [false, true]) assert.equal(validateSummary(metrics(shape), 'measured', 1000).transactions, 1000);
	const failed = metrics(true);
	failed.metrics.devotion_weekly_measured_failure.values.rate = Number.MIN_VALUE;
	assert.throws(() => validateSummary(failed, 'measured', 1000), /failure rate must be zero/);
	const unordered = metrics(false);
	unordered.metrics.devotion_weekly_measured['p(95)'] = 0;
	assert.throws(() => validateSummary(unordered, 'measured', 1000), /latency percentiles/);
});

test('PostgreSQL cumulative and pgss contracts retain decimal-string BigInt and availability continuity', () => {
	const db = issueFile('lib/validate-db-window.mjs');
	const attribution = issueFile('lib/validate-activity-attribution.mjs');
	for (const source of [db, attribution]) {
		assert.match(source, /BigInt\(/);
		assert.match(source, /lossless non-negative decimal string|decimalCounter/);
		assert.match(source, /availability drift|pg_stat_statements availability/);
	}
});

test('latest source/Flyway and related fixture correctness remain anchored without unrelated pagination coupling', async () => {
	const migrations = fs.readdirSync(path.join(REPOSITORY_ROOT, 'src/main/resources/db/migration')).filter((name) => /^V\d+__/.test(name));
	const latest = migrations.sort((left, right) => Number(left.match(/^V(\d+)/)[1]) - Number(right.match(/^V(\d+)/)[1])).at(-1);
	assert.equal(latest, 'V11__secure_supabase_data_api.sql');
	assert.match(issueFile('README.md'), /6796ed146244d8f3f5b5dd7048ebe16865084a97/);
	const { isAnnualTerminalRetentionCandidate } = await import('../lib/current-develop-contract.mjs');
	assert.equal(isAnnualTerminalRetentionCandidate({ status: 'UNPAID', createdAt: '2025-01-02T00:00:00.000Z', paymentAccountDeletedAt: '2026-01-01T00:00:00.000Z' }, {
		startInclusive: '2025-01-01T00:00:00.000Z', endExclusive: '2026-01-01T00:00:00.000Z',
	}), false);
	assert.doesNotMatch(issueFile('retention-dry-verify.sql'), /ORDER BY|LIMIT|OFFSET/i);
	assert.match(issueFile('retention-dry-verify.sql'), /BEGIN TRANSACTION READ ONLY/i);
});

test('approved clean detached checkout and later image creation replace unavailable image revision labels', async () => {
	const contractPath = path.join(ISSUE_DIR, 'lib/source-image-provenance.mjs');
	assert.equal(fs.existsSync(contractPath), true, 'source/image provenance contract must exist');
	const { validateSourceImageProvenance } = await import(pathToFileURL(contractPath));
	const sourceWorktree = '/private/tmp/FaithLog-perf-206-deploy';
	const facts = {
		schemaVersion: 1,
		proofMode: 'clean-detached-checkout-image-created-after-checkout',
		sourceWorktree,
		composeWorkingDir: sourceWorktree,
		revision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		detached: true,
		clean: true,
		checkoutAt: '2026-07-16T04:20:28.000Z',
		imageId: image('1'),
		imageCreatedAt: '2026-07-16T04:22:48.810Z',
		apiContractSha256: id('2'),
		limitation: 'image-alone-revision-label-unavailable',
	};
	const expected = {
		sourceWorktree,
		revision: facts.revision,
		imageId: facts.imageId,
		apiContractSha256: facts.apiContractSha256,
	};
	assert.equal(validateSourceImageProvenance(facts, expected), facts);
	assert.throws(
		() => validateSourceImageProvenance({ ...facts, clean: false }, expected),
		/clean/i,
	);
	assert.throws(
		() => validateSourceImageProvenance({ ...facts, imageCreatedAt: facts.checkoutAt }, expected),
		/after checkout/i,
	);
	assert.throws(
		() => validateSourceImageProvenance({ ...facts, composeWorkingDir: '/private/tmp/other' }, expected),
		/working directory/i,
	);
});

test('detached checkout time comes from the newest HEAD reflog selector even when its subject is empty', async () => {
	const contractPath = path.join(ISSUE_DIR, 'lib/source-image-provenance.mjs');
	const { parseNewestHeadReflogCheckoutAt } = await import(`${pathToFileURL(contractPath).href}?reflog=${Date.now()}`);
	const actualCheckoutAt = '2026-07-16T13:20:28+09:00';
	const reflog = [
		`2026-07-16T13:19:29+09:00\tHEAD@{${actualCheckoutAt}}\t`,
		'2026-07-15T10:00:00+09:00\tHEAD@{2026-07-15T10:00:01+09:00}\tcheckout: moving from develop to HEAD',
	].join('\n');
	assert.equal(typeof parseNewestHeadReflogCheckoutAt, 'function');
	assert.equal(parseNewestHeadReflogCheckoutAt(reflog), actualCheckoutAt);
	assert.notEqual(parseNewestHeadReflogCheckoutAt(reflog), '2026-07-16T13:19:29+09:00', 'committer time is not worktree checkout time');
	assert.throws(
		() => parseNewestHeadReflogCheckoutAt('2026-07-16T13:19:29+09:00\tHEAD@{not-a-time}\t'),
		/reflog selector.*timestamp/i,
	);
});

test('both runners use source/image provenance without requiring unavailable OCI revision labels', () => {
	for (const runnerName of ['run-devotion-baseline.sh', 'run-retention-dry-verify.sh']) {
		const runner = issueFile(runnerName);
		assert.match(runner, /APP_SOURCE_WORKTREE/);
		assert.match(runner, /source-image-provenance\.mjs/);
		assert.match(runner, /com\.docker\.compose\.project\.working_dir/);
		assert.match(runner, /docker image inspect[^\n]*\.Created/);
		assert.doesNotMatch(runner, /org\.opencontainers\.image\.(revision|api-contract-sha256)/);
	}
});

test('devotion credentials are child-scoped and every fixture report namespace is fresh', () => {
	const devotionRunner = issueFile('run-devotion-baseline.sh');
	const retentionRunner = issueFile('run-retention-dry-verify.sh');
	assert.match(devotionRunner, /credentials_file="\$CREDENTIALS_FILE"[\s\S]*unset CREDENTIALS_FILE/);
	assert.match(devotionRunner, /CREDENTIALS_FILE="\$credentials_file"[\s\\]*\n[\t ]*PHASE="?\$phase"?/);
	for (const runner of [devotionRunner, retentionRunner]) {
		assert.match(runner, /report_base="\$\{PERF_REPORT_ROOT:-build\/reports\/k6\/issue-197\}"/);
		assert.match(runner, /fixture_report_root="\$report_base\/\$fixture_run_id"/);
		assert.match(runner, /mkdir -m 700 "\$fixture_report_root"/);
		assert.match(runner, /fixture report namespace already exists/i);
		assert.doesNotMatch(runner, /mkdir -p "\$report_root"/);
	}
});
