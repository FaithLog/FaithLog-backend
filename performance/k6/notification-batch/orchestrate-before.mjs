import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { createHash } from 'node:crypto';
import {
	chmodSync,
	existsSync,
	mkdirSync,
	readFileSync,
	rmdirSync,
	writeFileSync,
} from 'node:fs';
import { dirname, isAbsolute, join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import {
	assertApprovedSnapshotInputs,
	buildApprovedSamplePlan,
	validateK6V2Metrics,
	validateRestoreReceipt,
	validateSnapshotReceipt,
	validateSnapshotSequence,
} from './snapshot-contract.mjs';
import { validateSeedReceipt as validateSyntheticSeedReceipt } from './seed-contract.mjs';
import { validateRedisCommandstatsBootstrapReceipt } from './redis-commandstats-bootstrap-contract.mjs';

const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const SAFE_BATCH_ID = /^[A-Za-z0-9][A-Za-z0-9_-]{0,30}$/;
const ALLOWED_CHILD_ENVIRONMENT = new Set([
	'PATH', 'HOME', 'TMPDIR', 'TMP', 'TEMP', 'LANG', 'LC_ALL', 'DOCKER_CONFIG',
	'ALLOW_NOTIFICATION_BATCH_BASELINE', 'PERF_SPRING_PROFILE', 'PERF_FCM_ADAPTER',
	'PERF_EXPECTED_COMPOSE_PROJECT', 'POSTGRES_CONTAINER', 'REDIS_CONTAINER',
	'PERF_EXPECTED_POSTGRES_CONTAINER_ID', 'PERF_EXPECTED_REDIS_CONTAINER_ID',
	'PERF_EXPECTED_POSTGRES_SERVICE', 'PERF_EXPECTED_REDIS_SERVICE',
	'PERF_EXPECTED_POSTGRES_IMAGE_ID', 'PERF_EXPECTED_REDIS_IMAGE_ID',
	'POSTGRES_USER', 'POSTGRES_PASSWORD', 'POSTGRES_DB', 'PERF_EXPECTED_POSTGRES_ROLE',
	'PERF_REDIS_AUTH_MODE', 'REDIS_PASSWORD',
	'PERF_EXPECTED_HARNESS_HEAD', 'PERF_EXPECTED_HARNESS_CONTRACT_DIGEST',
	'PERF_EXPECTED_POSTGRES_SERVER_ADDRESS',
	'PERF_DATASET_ID', 'PERF_CAMPUS_ID', 'PERF_MEMBER_COUNT',
	'PERF_SUCCESS_COUNT', 'PERF_TRANSIENT_COUNT', 'PERF_PERMANENT_COUNT',
	'PERF_INACTIVE_COUNT', 'PERF_NO_TOKEN_COUNT', 'PERF_BUSINESS_DATE',
	'PERF_DOCKER_STATS_SAMPLE_INTERVAL_SECONDS', 'PERF_DOCKER_STATS_MAX_GAP_MILLISECONDS',
	'PERF_REDIS_DATABASE', 'PERF_REDIS_SNAPSHOT_DATABASE',
	'PERF_REPORT_ROOT', 'EXPECTED_WARMUP_SAMPLES', 'EXPECTED_MEASURED_SAMPLES',
	'CUMULATIVE_STATE_STRATEGY',
	'PERF_SEED_RECEIPT_PATH',
	'PERF_REDIS_BOOTSTRAP_RECEIPT_PATH',
	'PERF_ORCHESTRATION_LOCK_RECEIPT',
]);

const REPORT_PATH_ENVIRONMENT = new Set([
	'PERF_ORCHESTRATION_LOCK_RECEIPT', 'PERF_SEED_RECEIPT_PATH', 'PERF_SNAPSHOT_ROOT',
	'PERF_REDIS_BOOTSTRAP_RECEIPT_PATH',
	'PERF_SNAPSHOT_RECEIPT_PATH', 'PERF_RESTORE_RECEIPT_PATH', 'MANIFEST_PATH',
	'RUN_DIRS_FILE', 'OUTPUT_PATH', 'SNAPSHOT_RECEIPT_PATH', 'RESTORE_RECEIPTS_FILE',
]);

const sanitizedChildEnvironment = (source, additions = {}) => {
	const result = {};
	for (const [key, value] of Object.entries(source ?? {})) {
		if (ALLOWED_CHILD_ENVIRONMENT.has(key) && value !== undefined && value !== '') result[key] = String(value);
	}
	for (const [key, value] of Object.entries(additions)) {
		if (value !== undefined && value !== '') result[key] = String(value);
	}
	return result;
};

const orchestrationChildEnvironment = (source, reportRoot, additions = {}) => {
	const approvedRoot = resolve(reportRoot);
	const result = sanitizedChildEnvironment(source, additions);
	assert.equal(result.PERF_REPORT_ROOT, approvedRoot, 'Child PERF_REPORT_ROOT must equal the parent-approved root');
	for (const key of REPORT_PATH_ENVIRONMENT) {
		if (result[key] === undefined) continue;
		assert.ok(isAbsolute(result[key]), `${key} must be absolute`);
		const pathFromRoot = relative(approvedRoot, resolve(result[key]));
		assert.ok(pathFromRoot.length > 0 && !pathFromRoot.startsWith('..') && !isAbsolute(pathFromRoot),
			`${key} must stay contained in PERF_REPORT_ROOT`);
	}
	return result;
};

const runFixedCommand = (scriptName, childEnvironment) => {
	const result = spawnSync('bash', [join(SCRIPT_DIR, scriptName)], {
		env: childEnvironment,
		stdio: 'inherit',
	});
	if (result.error) throw new Error(`${scriptName} failed to start`);
	if (result.status !== 0) throw new Error(`${scriptName} failed with exit ${result.status}`);
};

const runFixedNode = (scriptName, childEnvironment) => {
	const result = spawnSync(process.execPath, [join(SCRIPT_DIR, scriptName)], {
		env: childEnvironment,
		stdio: 'inherit',
	});
	if (result.error) throw new Error(`${scriptName} failed to start`);
	if (result.status !== 0) throw new Error(`${scriptName} failed with exit ${result.status}`);
};

const defaultAdapters = {
	provisionSyntheticDataset: async ({ childEnvironment }) => {
		runFixedCommand('provision-isolated-dataset.sh', childEnvironment);
	},
	prepareCanonicalFixture: async ({ childEnvironment }) => {
		runFixedCommand('prepare-fixtures.sh', childEnvironment);
	},
	initializeRedisCommandstats: async ({ childEnvironment }) => {
		runFixedCommand('bootstrap-redis-commandstats.sh', childEnvironment);
	},
	captureSnapshot: async ({ childEnvironment }) => {
		runFixedCommand('capture-state-snapshot.sh', childEnvironment);
	},
	restoreSnapshot: async ({ childEnvironment }) => {
		runFixedCommand('restore-state-snapshot.sh', childEnvironment);
	},
	runSample: async ({ childEnvironment }) => {
		runFixedCommand('run-before.sh', childEnvironment);
	},
	summarize: async ({ childEnvironment }) => {
		runFixedNode('summarize-before.mjs', childEnvironment);
	},
};

const readJson = (path) => JSON.parse(readFileSync(path, 'utf8'));
const sha256File = (path) => createHash('sha256').update(readFileSync(path)).digest('hex');

const writeFirstRejection = (path, stage, reason) => {
	try {
		writeFileSync(path, `${JSON.stringify({
			status: 'rejected',
			stage,
			reason,
			accepted: false,
			automaticAdoption: false,
			reusable: false,
		})}\n`, { flag: 'wx', mode: 0o600 });
	} catch (error) {
		if (error?.code !== 'EEXIST') throw error;
	}
};

const acquireDefaultOrchestrationLocks = ({ batchRoot, composeProject }) => {
	const globalLockDir = '/tmp/faithlog-performance-global.lock';
	const projectLockDir = `/tmp/faithlog-performance-${composeProject}.lock`;
	const receiptPath = join(batchRoot, 'orchestration-lock.json');
	mkdirSync(globalLockDir);
	try {
		mkdirSync(projectLockDir);
	} catch (error) {
		rmdirSync(globalLockDir);
		throw error;
	}
	try {
		writeFileSync(receiptPath, `${JSON.stringify({
			schemaVersion: 1,
			ownerPid: process.pid,
			composeProject,
			globalLockDir,
			projectLockDir,
			automaticAdoption: false,
		})}\n`, { flag: 'wx', mode: 0o600 });
	} catch (error) {
		rmdirSync(projectLockDir);
		rmdirSync(globalLockDir);
		throw error;
	}
	let released = false;
	return {
		childEnvironment: { PERF_ORCHESTRATION_LOCK_RECEIPT: receiptPath },
		release: () => {
			if (released) return;
			rmdirSync(projectLockDir);
			rmdirSync(globalLockDir);
			released = true;
		},
	};
};

const createSampleManifest = (canonicalManifest, sample, path) => {
	assert.equal(canonicalManifest.sampleKind, 'canonical');
	mkdirSync(dirname(path), { recursive: true, mode: 0o700 });
	writeFileSync(path, `${JSON.stringify({
		...canonicalManifest,
		sampleKind: sample.sampleKind,
	})}\n`, { flag: 'wx', mode: 0o600 });
};

export async function orchestrateNotificationBatchBefore(options, suppliedAdapters = defaultAdapters) {
	const {
		batchId,
		reportRoot,
		expectedWarmupSamples,
		expectedMeasuredSamples,
		cumulativeStateStrategy,
		expectedComposeProject,
		actualComposeProject,
		baseChildEnvironment = process.env,
		lockCoordinator = acquireDefaultOrchestrationLocks,
	} = options;
	assert.match(batchId ?? '', SAFE_BATCH_ID, 'Batch ID must be a safe 1-31 character identifier');
	assert.ok(isAbsolute(reportRoot ?? ''), 'PERF_REPORT_ROOT must be an absolute path');
	assertApprovedSnapshotInputs({
		expectedWarmupSamples,
		expectedMeasuredSamples,
		cumulativeStateStrategy,
		expectedComposeProject,
		actualComposeProject,
	});
	for (const adapter of [
		'provisionSyntheticDataset', 'prepareCanonicalFixture', 'initializeRedisCommandstats',
		'captureSnapshot', 'restoreSnapshot', 'runSample',
	]) {
		assert.equal(typeof suppliedAdapters[adapter], 'function', `${adapter} adapter is required`);
	}

	const batchRoot = join(resolve(reportRoot), 'orchestrations', batchId);
	mkdirSync(dirname(batchRoot), { recursive: true, mode: 0o700 });
	mkdirSync(batchRoot, { recursive: false, mode: 0o700 });
	chmodSync(batchRoot, 0o700);
	const rejectionPath = join(batchRoot, 'first-rejection.json');
	const canonicalFixtureRunId = `${batchId}-fixture`;
	const canonicalManifestPath = join(
		resolve(reportRoot), 'fixtures', canonicalFixtureRunId, 'manifest.json',
	);
	const snapshotReceiptPath = join(batchRoot, 'snapshot-receipt.json');
	const seedReceiptPath = join(batchRoot, 'seed-receipt.json');
	const redisBootstrapReceiptPath = join(batchRoot, 'redis-commandstats-bootstrap.json');
	const snapshotId = `${batchId}-snapshot`;
	const plan = buildApprovedSamplePlan(batchId);
	const restores = [];
	const restorePaths = [];
	const runDirs = [];
	let lockHandle;
	let stage = 'orchestration-lock';
	try {
		lockHandle = lockCoordinator({ batchRoot, composeProject: expectedComposeProject });
		assert.equal(typeof lockHandle?.release, 'function', 'Lock coordinator release is required');
	} catch (error) {
		writeFirstRejection(rejectionPath, stage, `${stage}-failed`);
		throw error;
	}
	let base = sanitizedChildEnvironment(baseChildEnvironment, {
		PERF_REPORT_ROOT: resolve(reportRoot),
		PERF_EXPECTED_COMPOSE_PROJECT: expectedComposeProject,
		EXPECTED_WARMUP_SAMPLES: expectedWarmupSamples,
		EXPECTED_MEASURED_SAMPLES: expectedMeasuredSamples,
		CUMULATIVE_STATE_STRATEGY: cumulativeStateStrategy,
		...lockHandle.childEnvironment,
	});

	stage = 'synthetic-seed';
	try {
		await suppliedAdapters.provisionSyntheticDataset({
			receiptPath: seedReceiptPath,
			childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
				PERF_SEED_RECEIPT_PATH: seedReceiptPath,
			}),
		});
		assert.ok(existsSync(seedReceiptPath), 'Synthetic seed receipt was not created');
		const seedReceipt = validateSyntheticSeedReceipt(readJson(seedReceiptPath));
		assert.equal(seedReceipt.composeProject, expectedComposeProject);
		assert.equal(seedReceipt.datasetId, base.PERF_DATASET_ID);
		const seedReceiptSha256 = sha256File(seedReceiptPath);
		base = orchestrationChildEnvironment(base, reportRoot, { PERF_CAMPUS_ID: seedReceipt.campusId });

		stage = 'canonical-fixture';
		await suppliedAdapters.prepareCanonicalFixture({
			manifestPath: canonicalManifestPath,
			childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
				PERF_FIXTURE_RUN_ID: canonicalFixtureRunId,
				PERF_SAMPLE_KIND: 'canonical',
			}),
		});
		assert.ok(existsSync(canonicalManifestPath), 'Canonical fixture manifest was not created');
		const canonicalManifest = readJson(canonicalManifestPath);
		assert.equal(canonicalManifest.fixtureRunId, canonicalFixtureRunId);
		assert.equal(canonicalManifest.sampleKind, 'canonical');
		assert.equal(canonicalManifest.composeProject, expectedComposeProject);

		stage = 'redis-commandstats-bootstrap';
		await suppliedAdapters.initializeRedisCommandstats({
			receiptPath: redisBootstrapReceiptPath,
			childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
				PERF_BATCH_ID: batchId,
				PERF_REDIS_BOOTSTRAP_RECEIPT_PATH: redisBootstrapReceiptPath,
			}),
		});
		const redisBootstrapReceipt = validateRedisCommandstatsBootstrapReceipt(readJson(redisBootstrapReceiptPath));
		assert.equal(redisBootstrapReceipt.composeProject, expectedComposeProject);
		assert.equal(redisBootstrapReceipt.redisContainerId, base.PERF_EXPECTED_REDIS_CONTAINER_ID);
		assert.equal(redisBootstrapReceipt.database, Number(base.PERF_REDIS_DATABASE));
		const redisBootstrapReceiptSha256 = sha256File(redisBootstrapReceiptPath);

		stage = 'snapshot-capture';
		await suppliedAdapters.captureSnapshot({
			outputPath: snapshotReceiptPath,
			manifestPath: canonicalManifestPath,
			childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
				PERF_SNAPSHOT_ID: snapshotId,
				PERF_SNAPSHOT_ROOT: batchRoot,
				PERF_SNAPSHOT_RECEIPT_PATH: snapshotReceiptPath,
			}),
		});
		const snapshot = validateSnapshotReceipt(readJson(snapshotReceiptPath));
		assert.equal(snapshot.snapshotId, snapshotId);
		assert.equal(snapshot.composeProject, expectedComposeProject);
		const snapshotReceiptSha256 = sha256File(snapshotReceiptPath);

		for (let index = 0; index < plan.length; index += 1) {
			const sample = plan[index];
			const restoreOrdinal = index + 1;
			stage = 'snapshot-restore';
			const restorePath = join(
				batchRoot, 'restores', `${String(restoreOrdinal).padStart(2, '0')}-${sample.sampleKind}.json`,
			);
			mkdirSync(dirname(restorePath), { recursive: true, mode: 0o700 });
			await suppliedAdapters.restoreSnapshot({
				outputPath: restorePath,
				sample,
				restoreOrdinal,
				snapshot,
				snapshotReceiptSha256,
				childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
					PERF_SNAPSHOT_ROOT: batchRoot,
					PERF_SNAPSHOT_RECEIPT_PATH: snapshotReceiptPath,
					PERF_RESTORE_RECEIPT_PATH: restorePath,
					PERF_RESTORE_ORDINAL: restoreOrdinal,
					PERF_SAMPLE_KIND: sample.sampleKind,
					PERF_SAMPLE_INDEX: sample.sampleIndex,
				}),
			});
			const restore = readJson(restorePath);
			validateRestoreReceipt({ snapshot, snapshotReceiptSha256, sample, restore, restoreOrdinal });
			restores.push(restore);
			restorePaths.push(restorePath);

			stage = 'sample-run';
			const sampleManifestPath = join(
				dirname(canonicalManifestPath), 'sample-manifests', `${sample.sampleId}.json`,
			);
			createSampleManifest(canonicalManifest, sample, sampleManifestPath);
			const runDir = join(resolve(reportRoot), 'runs', sample.sampleId);
			await suppliedAdapters.runSample({
				sample,
				runDir,
				manifestPath: sampleManifestPath,
				childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
					MANIFEST_PATH: sampleManifestPath,
					RUN_ID: sample.sampleId,
					PERF_SAMPLE_KIND: sample.sampleKind,
				}),
			});
			runDirs.push(runDir);
			const k6SummaryPath = join(runDir, 'k6-summary.json');
			if (existsSync(k6SummaryPath)) {
				const k6Summary = readJson(k6SummaryPath);
				validateK6V2Metrics(k6Summary.metrics, 10);
			}
		}

		const sequence = validateSnapshotSequence({ snapshot, snapshotReceiptSha256, plan, restores });
		const runDirsFile = join(batchRoot, 'run-dirs.txt');
		const restoreReceiptsFile = join(batchRoot, 'restore-receipts.txt');
		writeFileSync(runDirsFile, `${runDirs.join('\n')}\n`, { flag: 'wx', mode: 0o600 });
		writeFileSync(restoreReceiptsFile, `${restorePaths.join('\n')}\n`, { flag: 'wx', mode: 0o600 });
		if (typeof suppliedAdapters.summarize === 'function') {
			stage = 'summary';
			await suppliedAdapters.summarize({
				childEnvironment: orchestrationChildEnvironment(base, reportRoot, {
					RUN_DIRS_FILE: runDirsFile,
					OUTPUT_PATH: join(batchRoot, 'summary', 'baseline-summary.json'),
					SNAPSHOT_RECEIPT_PATH: snapshotReceiptPath,
					RESTORE_RECEIPTS_FILE: restoreReceiptsFile,
					PERF_BATCH_ID: batchId,
				}),
			});
		}
		const receipt = {
			schemaVersion: 1,
			batchId,
			composeProject: expectedComposeProject,
			fixturePrepareCount: 1,
			...sequence,
			canonicalFixtureRunId,
			seedReceiptSha256,
			seedCampusId: seedReceipt.campusId,
			redisBootstrapReceiptSha256,
			snapshotId,
			snapshotReceiptSha256,
			runDirs,
			accepted: false,
			automaticAdoption: false,
			measurementStatus: 'conditional-isolated-snapshot-restored',
		};
		writeFileSync(join(batchRoot, 'orchestration-receipt.json'), `${JSON.stringify(receipt, null, 2)}\n`, {
			flag: 'wx', mode: 0o600,
		});
		return receipt;
	} catch (error) {
		writeFirstRejection(rejectionPath, stage, `${stage}-failed`);
		throw error;
	} finally {
		try {
			lockHandle.release();
		} catch (error) {
			writeFirstRejection(rejectionPath, 'orchestration-lock-release', 'orchestration-lock-release-failed');
			throw error;
		}
	}
}

const isMain = process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url);
if (isMain) {
	await orchestrateNotificationBatchBefore({
		batchId: process.env.PERF_BATCH_ID,
		reportRoot: process.env.PERF_REPORT_ROOT,
		expectedWarmupSamples: process.env.EXPECTED_WARMUP_SAMPLES,
		expectedMeasuredSamples: process.env.EXPECTED_MEASURED_SAMPLES,
		cumulativeStateStrategy: process.env.CUMULATIVE_STATE_STRATEGY,
		expectedComposeProject: process.env.PERF_EXPECTED_COMPOSE_PROJECT,
		actualComposeProject: process.env.PERF_EXPECTED_COMPOSE_PROJECT,
	});
}
