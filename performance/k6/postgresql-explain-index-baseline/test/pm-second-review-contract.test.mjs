import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { spawn } from 'node:child_process';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const scenarioRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const repositoryRoot = path.resolve(scenarioRoot, '..', '..', '..');
const moduleUrl = (name) => pathToFileURL(path.join(scenarioRoot, name)).href;
const sha256 = (value) => createHash('sha256').update(value).digest('hex');

const anchors = {
	campus_id: 10,
	poll_id: 20,
	meal_poll_id: 21,
	member_user_id: 30,
	payment_account_id: 40,
	prayer_season_id: 50,
	prayer_week_id: 60,
	week_start_date: '2026-07-13',
	stale_before: '2026-07-14T00:00:00Z',
	range_start: '2025-01-01T00:00:00+09:00',
	range_end: '2026-01-01T00:00:00+09:00',
	keyword_pattern: '%PERF_%',
	page_size: 20,
	page_offset: 0,
	expected_state: {
		member_status: 'ACTIVE', poll_status: 'OPEN', poll_title: 'PERF poll fixture-1',
		meal_poll_status: 'CLOSED', meal_poll_title: 'PERF meal fixture-1',
		payment_account_is_active: true, payment_account_owner_user_id: 30,
		payment_account_nickname: 'PERF account fixture-1', prayer_season_status: 'ACTIVE',
		prayer_season_name: 'PERF season fixture-1', prayer_week_status: 'OPEN',
	},
};

test('cross-issue artifacts are real, unique, in-tree accepted JSON with matching identity and hashes', async () => {
	const { validateCrossIssueArtifacts } = await import(moduleUrl('cross-issue-contract.mjs'));
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-cross-'));
	try {
		const issueReports = {};
		const accepted = {
			192: { status: 'verified', passed: true },
			193: { status: 'eligible-for-pm-review' },
			195: { status: 'adoptable', adoptable: true },
			196: { status: 'measured', accepted: true, measurementStatus: 'measured' },
			197: { status: 'baseline-measured' },
			198: { status: 'before-baseline' },
			199: { status: 'adoptable', adoptable: true },
		};
		for (const issueNumber of [192, 193, 195, 196, 197, 198, 199]) {
			const relative = `artifacts/issue-${issueNumber}.json`;
			const absolute = path.join(root, relative);
			fs.mkdirSync(path.dirname(absolute), { recursive: true });
			fs.writeFileSync(absolute, JSON.stringify({
				issueNumber,
				datasetId: 'PERF_1000',
				fixtureRunId: 'fixture-1',
				memberCount: 1000,
				expectedAnchors: anchors,
				...accepted[issueNumber],
			}));
			issueReports[issueNumber] = relative;
		}
		const result = validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'),
			issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		});
		assert.equal(result.length, 7);
		assert.ok(result.every((item) => /^[a-f0-9]{64}$/.test(item.sha256)));
		const issue199Path = path.join(root, issueReports[199]);
		const issue199 = JSON.parse(fs.readFileSync(issue199Path, 'utf8'));
		fs.writeFileSync(issue199Path, JSON.stringify({ ...issue199, status: 'scenario-ready/not-measured', adoptable: false }));
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /not measured|adoptable/i);
		fs.writeFileSync(issue199Path, JSON.stringify({ ...issue199, fixtureRunId: 'other-fixture' }));
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /fixtureRunId.*does not match/i);
		fs.writeFileSync(issue199Path, '{invalid-json');
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /not valid JSON/i);
		fs.writeFileSync(issue199Path, JSON.stringify(issue199));

		issueReports[199] = issueReports[198];
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /duplicate/i);
		issueReports[199] = '../outside.json';
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /traversal|outside|relative/i);
		const link = path.join(root, 'artifacts', 'link.json');
		fs.symlinkSync(path.join(root, 'artifacts', 'issue-199.json'), link);
		issueReports[199] = 'artifacts/link.json';
		assert.throws(() => validateCrossIssueArtifacts({
			crossIssueReportPath: path.join(root, 'cross.json'), issueReports,
			datasetId: 'PERF_1000', fixtureRunId: 'fixture-1', memberCount: 1000,
		}), /symbolic link|symlink/i);
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
});

test('anchors require exact accepted-artifact identity and read-only relationship/cardinality preflight', async () => {
	const { validateAnchorPreflight, validateAnchorsAgainstArtifacts } = await import(moduleUrl('anchor-contract.mjs'));
	const artifacts = [192, 193, 195, 196, 197, 198, 199].map((issueNumber) => ({
		issueNumber, artifact: { expectedAnchors: anchors },
	}));
	assert.doesNotThrow(() => validateAnchorsAgainstArtifacts(anchors, artifacts));
	assert.throws(() => validateAnchorsAgainstArtifacts({ ...anchors, campus_id: 11 }, artifacts), /anchor/i);
	const valid = {
		campusExists: true, memberInCampus: true, pollInCampus: true, mealPollInCampus: true,
		paymentAccountInCampus: true, prayerSeasonInCampus: true, prayerWeekInSeason: true,
		memberCount: 1000, pollCount: 1, mealPollCount: 1, paymentAccountCount: 1,
		prayerSeasonCount: 1, prayerWeekCount: 1,
		memberStatus: 'ACTIVE', pollStatus: 'OPEN', pollTitle: 'PERF poll fixture-1',
		mealPollStatus: 'CLOSED', mealPollTitle: 'PERF meal fixture-1',
		paymentAccountIsActive: true, paymentAccountOwnerUserId: 30,
		paymentAccountNickname: 'PERF account fixture-1', prayerSeasonStatus: 'ACTIVE',
		prayerSeasonName: 'PERF season fixture-1', prayerWeekStatus: 'OPEN',
	};
	assert.equal(validateAnchorPreflight(valid, anchors.expected_state).adoptable, true);
	const mismatch = validateAnchorPreflight({ ...valid, memberInCampus: false, pollCount: 0 }, anchors.expected_state);
	assert.equal(mismatch.adoptable, false);
	assert.ok(mismatch.reasons.length >= 2);
});

test('source identity binds SQL bytes, inventory, report contract, production refs, git commit and clean state', async () => {
	const { assertSingleReadOnlySelect, buildSourceIdentity, loadSqlSources, validateSourceContinuity, validateSourceIdentity } = await import(moduleUrl('source-identity.mjs'));
	assert.doesNotThrow(() => assertSingleReadOnlySelect('SELECT 1', 'single'));
	assert.throws(() => assertSingleReadOnlySelect('SELECT 1; SELECT 2', 'multiple'), /exactly one/i);
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-source-'));
	try {
		fs.mkdirSync(path.join(root, 'sql'));
		fs.mkdirSync(path.join(root, 'src'));
		fs.writeFileSync(path.join(root, 'sql', 'q.sql'), 'SELECT 1;\n');
		fs.writeFileSync(path.join(root, 'inventory.json'), '{}\n');
		fs.writeFileSync(path.join(root, 'report-contract.json'), '{}\n');
		fs.writeFileSync(path.join(root, 'source-manifest.json'), '{"productionSourceRefs":{}}\n');
		fs.writeFileSync(path.join(root, 'src', 'Service.java'), 'final class Service {}\n');
		const controlSources = loadSqlSources(root, ['inventory.json', 'report-contract.json', 'source-manifest.json']);
		fs.writeFileSync(path.join(root, 'inventory.json'), '{"changed":true}\n');
		const expected = {
			productionSourceRefs: { 'src/Service.java': sha256('final class Service {}\n') },
		};
		const identity = buildSourceIdentity({
			repositoryRoot: root, scenarioRoot: root, gitCommit: 'abc123', gitDirty: false,
			sqlFiles: ['sql/q.sql'], productionSourceRefs: ['src/Service.java'],
			inventoryPath: 'inventory.json', reportContractPath: 'report-contract.json', expected, controlSources,
		});
		assert.equal(identity.sqlFiles[0].relativePath, 'sql/q.sql');
		assert.equal(identity.sqlFiles[0].sha256, sha256('SELECT 1;\n'));
		assert.equal(identity.inventory.sha256, sha256('{}\n'), 'parsed control bytes and provenance hash must be identical');
		assert.equal(identity.sourceManifest.sha256, sha256('{"productionSourceRefs":{}}\n'));
		assert.equal(validateSourceIdentity(identity).adoptable, true);
		assert.equal(validateSourceIdentity({ ...identity, gitDirty: true }).adoptable, false);
		assert.equal(validateSourceIdentity({ ...identity, sourceHashMismatches: ['src/Service.java'] }).adoptable, false);
		assert.equal(validateSourceContinuity(identity, structuredClone(identity)).stable, true);
		assert.equal(validateSourceContinuity(identity, { ...structuredClone(identity), gitDirty: true }).stable, false);
		const incompleteSet = buildSourceIdentity({
			repositoryRoot: root, scenarioRoot: root, gitCommit: 'abc123', gitDirty: false,
			sqlFiles: ['sql/q.sql'], productionSourceRefs: ['src/Service.java'],
			inventoryPath: 'inventory.json', reportContractPath: 'report-contract.json',
			expected: { productionSourceRefs: {
				'src/Service.java': sha256('final class Service {}\n'),
				'src/Missing.java': '0'.repeat(64),
			} },
		});
		assert.equal(validateSourceIdentity(incompleteSet).adoptable, false);
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
	const inventory = JSON.parse(fs.readFileSync(path.join(scenarioRoot, 'inventory.json'), 'utf8'));
	const manifest = JSON.parse(fs.readFileSync(path.join(scenarioRoot, 'source-manifest.json'), 'utf8'));
	const realIdentity = buildSourceIdentity({
		repositoryRoot, scenarioRoot, gitCommit: 'static-contract-check', gitDirty: false,
		sqlFiles: inventory.queries.map((query) => query.sqlFile),
		productionSourceRefs: inventory.queries.flatMap((query) => query.productionSourceRefs),
		inventoryPath: 'inventory.json', reportContractPath: 'report-contract.json',
		expected: { productionSourceRefs: manifest.productionSourceRefs },
	});
	assert.deepEqual(realIdentity.sourceHashMismatches, []);
	assert.equal(validateSourceIdentity(realIdentity).adoptable, true);
});

test('schema identity canonicalizes Flyway, columns, constraints and indexes and rejects changes', async () => {
	const { normalizeSchemaState, validateSchemaContinuity, validateSchemaSnapshot } = await import(moduleUrl('schema-contract.mjs'));
	const state = {
		flyway: [{ installedRank: 2, version: '2', success: true }, { installedRank: 1, version: '1', success: true }],
		columns: [{ table: 'polls', name: 'id', type: 'bigint', nullable: false }],
		constraints: [{ table: 'polls', name: 'polls_pkey', definition: 'PRIMARY KEY (id)' }],
		indexes: [{ table: 'polls', name: 'polls_pkey', definition: 'CREATE UNIQUE INDEX polls_pkey ON public.polls USING btree (id)' }],
	};
	const first = normalizeSchemaState(state);
	assert.equal(validateSchemaSnapshot(first, ['polls']).adoptable, true);
	assert.equal(validateSchemaSnapshot(normalizeSchemaState({}), ['polls']).adoptable, false);
	const reordered = normalizeSchemaState({ ...state, flyway: [...state.flyway].reverse() });
	assert.equal(first.fingerprint, reordered.fingerprint);
	const changed = normalizeSchemaState({ ...state, indexes: [...state.indexes, {
		table: 'polls', name: 'candidate', definition: 'CREATE INDEX candidate ON public.polls USING btree (status)',
	}] });
	assert.equal(validateSchemaContinuity(first, changed).stable, false);
	assert.match(validateSchemaContinuity(first, changed).reasons.join(','), /schema|index/i);
});

test('query-window monitor retains transient external activity that disappears before the final snapshot', async () => {
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-monitor-'));
	try {
		const bin = path.join(root, 'bin');
		const output = path.join(root, 'window.json');
		fs.mkdirSync(bin);
		const fakePsql = path.join(bin, 'psql');
		fs.writeFileSync(fakePsql, `#!/bin/sh\nprintf '%s\\n' '[{"pid":777,"applicationName":"faithlog-measured","state":"active","queryStart":"2026-07-14T00:00:01Z"},{"pid":902,"applicationName":"frontend","state":"active","queryStart":"2026-07-14T00:00:01Z"},{"pid":903,"applicationName":"faithlog-measured","state":"active","queryStart":"2026-07-14T00:00:01Z"}]'\n`);
		fs.chmodSync(fakePsql, 0o700);
		const worker = spawn(process.execPath, [path.join(scenarioRoot, 'activity-monitor-worker.mjs'), output, 'faithlog-measured'], {
			env: { ...process.env, PATH: `${bin}:${process.env.PATH}` }, stdio: ['pipe', 'pipe', 'pipe'],
		});
		await new Promise((resolve, reject) => {
			const timer = setTimeout(() => reject(new Error('fake worker readiness timed out')), 2000);
			worker.once('error', reject);
			worker.stdout.on('data', (chunk) => {
				const text = chunk.toString();
				if (text.includes('ready')) worker.stdin.write('REGISTER:777:test-window\n');
				if (text.includes('measured-observed:777')) {
					clearTimeout(timer);
					worker.kill('SIGTERM');
					resolve();
				}
			});
		});
		await new Promise((resolve, reject) => {
			worker.once('error', reject);
			worker.once('exit', (code) => code === 0 ? resolve() : reject(new Error(`worker status ${code}`)));
		});
		const result = JSON.parse(fs.readFileSync(output, 'utf8'));
		assert.equal(result.measuredSessionObserved, true);
		assert.equal(result.transientExternalActivityDetected, true);
		assert.deepEqual(result.sessions.map((session) => session.pid), [902, 903]);
		const { validateActivityWindow } = await import(moduleUrl('activity-monitor-contract.mjs'));
		const unsampled = validateActivityWindow({
			sampleCount: 2, measuredSessionObserved: false, measuredSessions: [{ pid: 777, observed: false }], sessions: [],
		});
		assert.equal(unsampled.adoptable, false);
		assert.ok(unsampled.reasons.includes('measured-session-not-observed-by-monitor'));
		const incompleteLifecycle = validateActivityWindow({
			sampleCount: 2, measuredSessionObserved: true,
			measuredSessions: [{ pid: 777, label: 'old-run', registrationToken: 'a'.repeat(32), observed: true, unregistered: true }],
			sessions: [],
		}, { expectedLabels: ['new-run'] });
		assert.equal(incompleteLifecycle.adoptable, false);
		assert.match(incompleteLifecycle.reasons.join(','), /label|registration|lifecycle/i);
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
});

test('activity protocol binds ACK to a nonce and identity, unregisters every backend, and aborts on external activity', () => {
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	const worker = fs.readFileSync(path.join(scenarioRoot, 'activity-monitor-worker.mjs'), 'utf8');
	assert.match(runner, /registrationToken/);
	assert.match(runner, /backendStart/);
	assert.match(runner, /unregisterMeasured/);
	assert.match(runner, /contaminationPromise/);
	assert.match(worker, /measured-observed:\$\{registration\.registrationToken\}/);
	assert.match(worker, /measured-unregistered:/);
	assert.match(worker, /external-observed/);
	assert.match(worker, /applicationName.*backendStart/s);
	assert.match(worker, /state <> 'idle'[\s\S]*OR[\s\S]*application_name LIKE/i);
});

test('runner cancels timeout losers, preserves child-reap lock safety, and records monitor stop failure', () => {
	const runner = fs.readFileSync(path.join(scenarioRoot, 'run-baseline.mjs'), 'utf8');
	assert.match(runner, /function withTimeout/);
	assert.match(runner, /clearTimeout/);
	assert.doesNotMatch(runner, /delayResult\(/);
	assert.match(runner, /allChildrenReaped/);
	assert.match(runner, /activity-monitor-stop-failure/);
	assert.match(runner, /child-process-not-reaped/);
	assert.match(runner, /randomBytes\(8\).*report/s);
});

test('planner integrity rejects autovacuum, vacuum and visibility evidence changes', async () => {
	const { REQUIRED_PLANNER_SETTINGS, validateMeasurementIntegrity } = await import(moduleUrl('runtime-contract.mjs'));
	const settings = Object.fromEntries(REQUIRED_PLANNER_SETTINGS.map((name) => [name, 'same']));
	const table = {
		table: 'polls', lastAnalyze: null, lastAutoanalyze: null, nModSinceAnalyze: 0,
		lastVacuum: null, lastAutovacuum: null, vacuumCount: 0, autovacuumCount: 0,
		allVisiblePages: 1,
	};
	const snapshot = (row) => ({
		capturedAt: '2026-07-14T00:00:00Z', serverVersion: '17',
		postmasterStartedAt: '2026-07-13T00:00:00Z', settings,
		tableStatistics: [row], externalActivity: { activeSessionCount: 0, sessions: [] },
	});
	assert.equal(validateMeasurementIntegrity(snapshot(table), snapshot(table), { expectedTables: ['polls'] }).adoptable, true);
	const result = validateMeasurementIntegrity(snapshot(table), snapshot({
		...table, lastAutovacuum: '2026-07-14T00:00:01Z', autovacuumCount: 1, allVisiblePages: 2,
	}), { expectedTables: ['polls'] });
	assert.equal(result.adoptable, false);
	assert.match(result.reasons.join(','), /autovacuum|visible/i);
	const manualVacuum = validateMeasurementIntegrity(snapshot(table), snapshot({
		...table, lastVacuum: '2026-07-14T00:00:01Z', vacuumCount: 1,
	}), { expectedTables: ['polls'] });
	assert.equal(manualVacuum.adoptable, false);
	assert.match(manualVacuum.reasons.join(','), /vacuum/i);
});

test('start and runtime rejection reports are 0600, structured, redacted and preserve zero/partial query counts', async () => {
	const { writeRejectedReport } = await import(moduleUrl('rejected-report.mjs'));
	const root = fs.mkdtempSync(path.join(os.tmpdir(), 'faithlog-194-rejected-'));
	try {
		const file = path.join(root, 'baseline-report.json');
		writeRejectedReport(file, {
			phase: 'start-integrity', reasons: ['external-activity-present-at-start'], queryRunCount: 0,
			composeIdentity: { composeProject: 'faithlog' }, databaseIdentity: { database: 'faithlog' },
			capturedSnapshot: { capturedAt: '2026-07-14T00:00:00Z' },
			error: new Error('psql failed PGPASSWORD=raw-secret'),
		});
		const report = JSON.parse(fs.readFileSync(file, 'utf8'));
		assert.equal(report.status, 'invalid-pending-start-integrity');
		assert.equal(report.schemaVersion, 2);
		assert.equal(report.queryRunCount, 0);
		assert.deepEqual(report.reasons, ['external-activity-present-at-start']);
		assert.doesNotMatch(fs.readFileSync(file, 'utf8'), /raw-secret|PGPASSWORD|psql failed/);
		assert.equal(fs.statSync(file).mode & 0o777, 0o600);
		writeRejectedReport(file, {
			phase: 'runtime-failure', reasons: ['transient-external-activity-detected'], queryRunCount: 3,
			composeIdentity: { composeProject: 'faithlog' }, databaseIdentity: { database: 'faithlog' },
			activityWindows: [{ sampleCount: 3, measuredSessionObserved: true, sessions: [{ pid: 902 }] }],
		});
		const runtimeReport = JSON.parse(fs.readFileSync(file, 'utf8'));
		assert.equal(runtimeReport.status, 'invalid-pending-runtime-failure');
		assert.equal(runtimeReport.queryRunCount, 3);
		assert.equal(runtimeReport.activityWindows[0].sessions[0].pid, 902);
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
});

test('anchor syntax requires a real Monday, strict RFC3339 instants and increasing range', async () => {
	const { validateAnchors } = await import(moduleUrl('anchor-contract.mjs'));
	assert.doesNotThrow(() => validateAnchors(anchors));
	assert.throws(() => validateAnchors({ ...anchors, week_start_date: '2026-99-99' }), /date|monday/i);
	assert.throws(() => validateAnchors({ ...anchors, week_start_date: '2026-07-14' }), /monday/i);
	assert.throws(() => validateAnchors({ ...anchors, stale_before: 'July 14, 2026' }), /RFC3339/i);
	assert.throws(() => validateAnchors({ ...anchors, stale_before: '2026-07-14T24:00:00Z' }), /RFC3339/i);
	assert.throws(() => validateAnchors({ ...anchors, stale_before: '2026-02-29T00:00:00Z' }), /RFC3339/i);
	assert.throws(() => validateAnchors({ ...anchors, range_start: anchors.range_end, range_end: anchors.range_start }), /range_start.*range_end/i);
	assert.throws(() => validateAnchors({ ...anchors, campus_id: 0 }), /positive/i);
});
