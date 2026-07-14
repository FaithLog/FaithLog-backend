import { createHash, randomBytes } from 'node:crypto';
import { spawn, spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { groupEvidenceQueries, validateEvidenceInventory } from './evidence-contract.mjs';
import { normalizeExplain } from './normalize-plan.mjs';
import { validateAnchorPreflight, validateAnchors, validateAnchorsAgainstArtifacts } from './anchor-contract.mjs';
import { validateActivityWindow } from './activity-monitor-contract.mjs';
import { validateCrossIssueArtifacts } from './cross-issue-contract.mjs';
import { writeRejectedReport as writeRejectedReportFile } from './rejected-report.mjs';
import { allocateReportDirectory, terminateChildProcess, withTimeout } from './runner-safety-contract.mjs';
import { normalizeSchemaState, validateSchemaContinuity, validateSchemaSnapshot } from './schema-contract.mjs';
import { assertSingleReadOnlySelect, buildSourceIdentity, loadSqlSources, validateSourceContinuity, validateSourceIdentity } from './source-identity.mjs';
import {
	REQUIRED_PLANNER_SETTINGS,
	acquireProjectLock,
	assertProjectLockOwned,
	decideMeasurementOutcome,
	parseActivitySampleInterval,
	parseWarmRuns,
	releaseProjectLock,
	validateDatabaseContinuity,
	validateDatabaseIdentity,
	validateComposeIdentityContinuity,
	validateMeasurementIntegrity,
	validateMeasurementStart,
} from './runtime-contract.mjs';

const scenarioRoot = path.dirname(fileURLToPath(import.meta.url));
const repositoryRoot = path.resolve(scenarioRoot, '..', '..', '..');
const reportsRoot = path.join(scenarioRoot, 'reports');
const requiredRuntimeInputs = [
	'DATASET_ID',
	'FIXTURE_RUN_ID',
	'CROSS_ISSUE_REPORT',
	'POSTGRES_CONTAINER',
	'PGHOST',
	'PGPORT',
	'PGDATABASE',
	'PGUSER',
	'PGPASSWORD',
	'WARM_RUNS',
	'ACTIVITY_SAMPLE_INTERVAL_MS',
];

for (const name of requiredRuntimeInputs) {
	if (!process.env[name]) {
		throw new Error(`${name} is required at runtime.`);
	}
}
if (process.env.ALLOW_EXPLAIN_ANALYZE !== 'true') {
	throw new Error('EXPLAIN ANALYZE is blocked. Set ALLOW_EXPLAIN_ANALYZE=true only in the approved isolated measurement window.');
}

const warmRuns = parseWarmRuns(process.env.WARM_RUNS);
const activitySampleIntervalMs = parseActivitySampleInterval(process.env.ACTIVITY_SAMPLE_INTERVAL_MS);

function writeRejectedReport(filePath, details) {
	return writeRejectedReportFile(filePath, { ...details, activitySampleIntervalMs });
}

fs.mkdirSync(reportsRoot, { recursive: true });

let exitCode = 0;
let projectLock = null;
let allChildrenReaped = true;
try {
	await run();
} catch (error) {
	exitCode = 1;
	console.error(error instanceof Error ? error.message : String(error));
} finally {
	if (projectLock && allChildrenReaped) {
		releaseProjectLock(projectLock);
	} else if (projectLock) {
		console.error(`Canonical runner lock retained after child-process-not-reaped: ${projectLock.path}`);
	}
}
process.exitCode = exitCode;

async function run() {
	const startedAt = new Date().toISOString();
	const reportDirectory = allocateReportDirectory(reportsRoot, {
		datasetId: process.env.DATASET_ID,
		fixtureRunId: process.env.FIXTURE_RUN_ID,
		startedAt,
		nonce: randomBytes(8).toString('hex'),
	});
	const reportPath = path.join(reportDirectory, 'baseline-report.json');
	const crossIssueReportPath = path.resolve(process.env.CROSS_ISSUE_REPORT);
	let crossIssueReportText = null;
	let crossIssueReport = null;
	let crossIssueArtifacts = [];
	let inventory = null;
	let reportContract = null;
	let sourceManifestText = null;
	let sourceManifest = null;
	let controlSources = [];
	let sqlSources = [];
	let sqlSourceByPath = null;
	let variables = null;
	let sourceIdentity = null;
	let sourceIntegrity = null;
	let composeIdentityBeforeLock = null;
	let composeIdentityAfterLock = null;
	let composeIdentityAfterMeasurement = null;
	let composeLockContinuity = null;
	let composeMeasurementContinuity = null;
	const composeIdentityEvidence = () => ({
		beforeLock: composeIdentityBeforeLock,
		afterLock: composeIdentityAfterLock,
		afterMeasurement: composeIdentityAfterMeasurement,
		lockContinuity: composeLockContinuity,
		measurementContinuity: composeMeasurementContinuity,
	});
	try {
		crossIssueReportText = fs.readFileSync(crossIssueReportPath, 'utf8');
		crossIssueReport = JSON.parse(crossIssueReportText);
		validateCrossIssueReport(crossIssueReport);
		crossIssueArtifacts = validateCrossIssueArtifacts({
		crossIssueReportPath,
		issueReports: crossIssueReport.issueReports,
		datasetId: process.env.DATASET_ID,
		fixtureRunId: process.env.FIXTURE_RUN_ID,
		memberCount: crossIssueReport.memberCount,
		});

		controlSources = loadSqlSources(scenarioRoot, ['inventory.json', 'report-contract.json', 'source-manifest.json']);
		const controlSourceByPath = new Map(controlSources.map((source) => [source.relativePath, source]));
		inventory = JSON.parse(controlSourceByPath.get('inventory.json').text);
		reportContract = JSON.parse(controlSourceByPath.get('report-contract.json').text);
		sourceManifestText = controlSourceByPath.get('source-manifest.json').text;
		sourceManifest = JSON.parse(sourceManifestText);
		validateEvidenceInventory(inventory, {
		...reportContract.evidenceClassification,
		sourceRoot: repositoryRoot,
		});
		sqlSources = loadSqlSources(scenarioRoot, inventory.queries.map((query) => query.sqlFile));
		sqlSourceByPath = new Map(sqlSources.map((source) => [source.relativePath, source]));
		for (const query of inventory.queries) {
			assertSingleReadOnlySelect(stripTrailingSemicolon(sqlSourceByPath.get(query.sqlFile).text), query.id);
		}
		variables = validateAnchors(crossIssueReport.anchors);
		validateAnchorsAgainstArtifacts(crossIssueReport.anchors, crossIssueArtifacts);
		sourceIdentity = captureSourceIdentity(inventory, sourceManifest, sourceManifestText, sqlSources, controlSources);
		sourceIntegrity = validateSourceIdentity(sourceIdentity);
		if (!sourceIntegrity.adoptable) {
			throw new Error(`Source identity is not adoptable; no Docker/psql/EXPLAIN was run: ${sourceIntegrity.reasons.join(', ')}`);
		}
		composeIdentityBeforeLock = inspectComposeIdentity(process.env.POSTGRES_CONTAINER);
	} catch (error) {
		writeRejectedReport(reportPath, {
			phase: 'start-integrity', reasons: ['input-or-identity-preflight-failure'], queryRunCount: 0,
			composeIdentity: composeIdentityEvidence(), databaseIdentity: null, capturedSnapshot: null, sourceIdentity,
			datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
		});
		throw error;
	}
	try {
		projectLock = acquireProjectLock(composeIdentityBeforeLock.composeProject, 'issue-194');
	} catch (error) {
		writeRejectedReport(reportPath, {
			phase: 'start-integrity', reasons: ['canonical-runner-lock-unavailable'], queryRunCount: 0,
			composeIdentity: composeIdentityEvidence(), databaseIdentity: null, capturedSnapshot: null, sourceIdentity,
			datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
		});
		throw error;
	}
	let databaseIdentity = null;
	let before = null;
	let schemaBefore = null;
	const queryReports = [];
	const activityWindows = [];
	const measurementLabels = [];
	let explainRunCount = 0;
	let measurementMonitor = null;
	try {
		composeIdentityAfterLock = inspectComposeIdentity(process.env.POSTGRES_CONTAINER);
		composeLockContinuity = validateComposeIdentityContinuity(composeIdentityBeforeLock, composeIdentityAfterLock);
		if (!composeLockContinuity.stable) {
			writeRejectedReport(reportPath, {
				phase: 'start-integrity', reasons: composeLockContinuity.reasons, queryRunCount: 0,
				composeIdentity: composeIdentityEvidence(), databaseIdentity: null, capturedSnapshot: null, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
			});
			throw new Error('PostgreSQL container identity changed while acquiring the canonical runner lock; no psql or EXPLAIN was run.');
		}
		databaseIdentity = captureDatabaseIdentity();
		validateDatabaseIdentity(composeIdentityAfterLock, databaseIdentity, process.env.PGDATABASE);
		schemaBefore = captureSchemaState(inventory.observedTables);
		const schemaStartIntegrity = validateSchemaSnapshot(schemaBefore, inventory.observedTables);
		if (!schemaStartIntegrity.adoptable) {
			writeRejectedReport(reportPath, {
				phase: 'start-integrity', reasons: schemaStartIntegrity.reasons, queryRunCount: 0,
				composeIdentity: composeIdentityEvidence(), databaseIdentity, capturedSnapshot: null, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
			});
			throw new Error('Schema/Flyway preflight rejected the measurement; no EXPLAIN was run.');
		}
		const anchorPreflight = captureAnchorPreflight(variables);
		const anchorIntegrity = validateAnchorPreflight(anchorPreflight, crossIssueReport.anchors.expected_state);
		if (!anchorIntegrity.adoptable) {
			writeRejectedReport(reportPath, {
				phase: 'start-integrity', reasons: anchorIntegrity.reasons, queryRunCount: 0,
				composeIdentity: composeIdentityEvidence(), databaseIdentity, capturedSnapshot: null, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
			});
			throw new Error('Anchor relationship/cardinality preflight rejected the measurement; no EXPLAIN was run.');
		}
		before = capturePlannerState(inventory.observedTables);
		const startIntegrity = validateMeasurementStart(before, {
			expectedTables: inventory.observedTables,
		});
		if (!startIntegrity.adoptable) {
			writeRejectedReport(reportPath, {
				phase: 'start-integrity', reasons: startIntegrity.reasons, queryRunCount: 0,
				composeIdentity: composeIdentityEvidence(), databaseIdentity, capturedSnapshot: before, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID, crossIssueArtifacts,
			});
			throw new Error('Measurement start is contaminated or incomplete; no EXPLAIN was run.');
		}
		measurementMonitor = await startActivityMonitor(reportDirectory, 'measurement', 'faithlog-194-');
		for (const query of inventory.queries) {
			const sqlSource = sqlSourceByPath.get(query.sqlFile);
			const sqlSha256 = sqlSource.sha256;
			const selectSql = stripTrailingSemicolon(sqlSource.text);
			const phases = [
				{ id: 'cold_like_observation', runs: 1, cacheResetPerformed: false },
				{ id: 'warm_cache', runs: warmRuns, cacheResetPerformed: false },
			];
			for (const phase of phases) {
				for (let runNumber = 1; runNumber <= phase.runs; runNumber += 1) {
					const capturedAt = new Date().toISOString();
					const fileStem = `${query.id}__${phase.id}__${String(runNumber).padStart(2, '0')}`;
					const applicationName = `faithlog-194-${randomBytes(16).toString('hex')}`;
					measurementLabels.push(fileStem);
					const rawExplain = await explain(selectSql, variables, applicationName, measurementMonitor, fileStem);
					explainRunCount += 1;
					const normalized = normalizeExplain(rawExplain);
					writeJson(path.join(reportDirectory, 'raw', `${fileStem}.json`), rawExplain);
					writeJson(path.join(reportDirectory, 'normalized', `${fileStem}.json`), {
						queryId: query.id,
						issueNumber: query.issueNumber,
						evidenceClass: query.evidenceClass,
						productionBeforeEligible: query.productionBeforeEligible,
						productionSourceRefs: query.productionSourceRefs,
						productionSqlIdentity: query.productionSqlIdentity ?? null,
						productionSqlFingerprint: query.productionSqlIdentity?.fingerprint ?? null,
						sqlFile: query.sqlFile,
						sqlSha256,
						phase: phase.id,
						runNumber,
						cacheResetPerformed: false,
						capturedAt,
						...normalized,
					});
					queryReports.push({
						queryId: query.id,
						issueNumber: query.issueNumber,
						evidenceClass: query.evidenceClass,
						productionBeforeEligible: query.productionBeforeEligible,
						productionSourceRefs: query.productionSourceRefs,
						productionSqlIdentity: query.productionSqlIdentity ?? null,
						productionSqlFingerprint: query.productionSqlIdentity?.fingerprint ?? null,
						sqlFile: query.sqlFile,
						sqlSha256,
						phase: phase.id,
						runNumber,
						cacheResetPerformed: false,
						capturedAt,
						planHash: normalized.planHash,
						metrics: normalized.metrics,
					});
				}
			}
		}
		activityWindows.push(await stopActivityMonitor(measurementMonitor));
		measurementMonitor = null;
		composeIdentityAfterMeasurement = inspectComposeIdentity(process.env.POSTGRES_CONTAINER);
		composeMeasurementContinuity = validateComposeIdentityContinuity(composeIdentityAfterLock, composeIdentityAfterMeasurement);
		if (!composeMeasurementContinuity.stable) {
			writeRejectedReport(reportPath, {
				phase: 'runtime-failure', reasons: composeMeasurementContinuity.reasons,
				queryRunCount: explainRunCount, composeIdentity: composeIdentityEvidence(), databaseIdentity,
				capturedSnapshot: before, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID,
				crossIssueArtifacts, activityWindows,
			});
			throw new Error('PostgreSQL container identity changed during the measurement window.');
		}
		const measuredActivityIntegrity = validateActivityWindow(activityWindows[0], {
			expectedLabels: measurementLabels,
		});
		if (!measuredActivityIntegrity.adoptable) {
			writeRejectedReport(reportPath, {
				phase: 'runtime-failure', reasons: measuredActivityIntegrity.reasons,
				queryRunCount: explainRunCount, composeIdentity: composeIdentityEvidence(), databaseIdentity,
				capturedSnapshot: before, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID,
				crossIssueArtifacts, activityWindows,
			});
			throw new Error('Transient external activity contaminated the continuous measurement window.');
		}
		const after = capturePlannerState(inventory.observedTables);
		const schemaAfter = captureSchemaState(inventory.observedTables);
		const schemaAfterIntegrity = validateSchemaSnapshot(schemaAfter, inventory.observedTables);
		const databaseIdentityAfter = captureDatabaseIdentity();
		validateDatabaseIdentity(composeIdentityAfterMeasurement, databaseIdentityAfter, process.env.PGDATABASE);
		const controlSourcesAfter = loadSqlSources(scenarioRoot, ['inventory.json', 'report-contract.json', 'source-manifest.json']);
		const sourceIdentityAfter = captureSourceIdentity(
			inventory, sourceManifest, sourceManifestText, null, controlSourcesAfter
		);
		const sourceContinuity = validateSourceContinuity(sourceIdentity, sourceIdentityAfter);
		const crossIssueArtifactsAfter = validateCrossIssueArtifacts({
			crossIssueReportPath,
			issueReports: crossIssueReport.issueReports,
			datasetId: process.env.DATASET_ID,
			fixtureRunId: process.env.FIXTURE_RUN_ID,
			memberCount: crossIssueReport.memberCount,
		});
		const crossArtifactContinuity = validateArtifactContinuity(crossIssueArtifacts, crossIssueArtifactsAfter);
		const finishedAt = new Date().toISOString();
		const plannerIntegrity = validateMeasurementIntegrity(before, after, {
			expectedTables: inventory.observedTables,
		});
		const databaseContinuity = validateDatabaseContinuity(databaseIdentity, databaseIdentityAfter);
		const schemaContinuity = validateSchemaContinuity(schemaBefore, schemaAfter);
		const activityIntegrity = combineActivityWindows(activityWindows, measurementLabels);
		const measurementIntegrity = {
			adoptable: plannerIntegrity.adoptable && databaseContinuity.stable
				&& schemaContinuity.stable && schemaAfterIntegrity.adoptable && activityIntegrity.adoptable
				&& sourceContinuity.stable && crossArtifactContinuity.stable
				&& composeLockContinuity.stable && composeMeasurementContinuity.stable,
			reasons: [...plannerIntegrity.reasons, ...databaseContinuity.reasons,
				...schemaContinuity.reasons, ...schemaAfterIntegrity.reasons, ...activityIntegrity.reasons,
				...sourceContinuity.reasons, ...crossArtifactContinuity.reasons,
				...composeLockContinuity.reasons, ...composeMeasurementContinuity.reasons],
		};
		const evidenceGroups = groupEvidenceQueries(inventory.queries);
		const productionBeforeEligibleQueryIds = inventory.queries
			.filter((query) => query.productionBeforeEligible)
			.map((query) => query.id);
		let measurementOutcome = decideMeasurementOutcome(
			measurementIntegrity,
			productionBeforeEligibleQueryIds,
			reportContract.evidenceClassification.productionBeforeEvidenceEnabled
		);
		if (!schemaContinuity.stable || !schemaAfterIntegrity.adoptable
			|| !activityIntegrity.adoptable || !sourceContinuity.stable || !crossArtifactContinuity.stable) {
			measurementOutcome = {
				status: 'invalid-pending-runtime-failure',
				productionBeforeAdoptable: false,
				exitNonZero: true,
			};
		}
		assertProjectLockOwned(projectLock);
		const report = {
		schemaVersion: reportContract.schemaVersion,
		status: measurementOutcome.status,
		issueNumber: 194,
		datasetId: process.env.DATASET_ID,
		fixtureRunId: process.env.FIXTURE_RUN_ID,
		memberCount: crossIssueReport.memberCount,
		crossIssueReport: {
			path: path.basename(crossIssueReportPath),
			sha256: createHash('sha256').update(crossIssueReportText).digest('hex'),
			issueReports: crossIssueArtifacts.map(({ issueNumber, relativePath, sha256 }) => ({ issueNumber, relativePath, sha256 })),
		},
		sourceIdentity: {
			before: { ...sourceIdentity, integrity: sourceIntegrity },
			after: sourceIdentityAfter,
			continuity: sourceContinuity,
		},
		startedAt,
		finishedAt,
		sequentialExecution: true,
		warmRuns,
		activitySampleIntervalMs,
		runnerLock: {
			path: projectLock.path,
			owner: projectLock.owner,
			ownedAtFinish: true,
		},
		cacheResetPerformed: false,
		cacheInterpretation: reportContract.measurementPhases,
		credentialPolicy: {
			source: 'runtime-environment-only',
			requiredVariables: ['PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD'],
			valuesRecorded: false,
		},
		composeIdentity: composeIdentityEvidence(),
		databaseIdentity: {
			before: databaseIdentity,
			after: databaseIdentityAfter,
			...databaseContinuity,
		},
		evidenceClassification: {
			groups: evidenceGroups,
			productionBeforeEligibleQueryIds,
			productionBeforeAdoptable: measurementOutcome.productionBeforeAdoptable,
			policy: 'Only captured exact-current-production SQL with a Hibernate fingerprint may support production before metrics.',
		},
		plannerState: {
			before,
			after,
			autoanalyzeChanges: compareAutoanalyze(before.tableStatistics, after.tableStatistics),
			integrity: measurementIntegrity,
		},
		schemaState: {
			before: schemaBefore,
			after: schemaAfter,
			indexChangeIdentity: {
				before: schemaBefore.indexFingerprint,
				after: schemaAfter.indexFingerprint,
				changed: schemaBefore.indexFingerprint !== schemaAfter.indexFingerprint,
			},
			afterIntegrity: schemaAfterIntegrity,
			continuity: schemaContinuity,
		},
		activityWindows,
		queries: queryReports,
		queryRunCount: explainRunCount,
		};
		writeJson(reportPath, report);
		console.log(JSON.stringify({ reportPath, queryRunCount: explainRunCount, status: report.status }, null, 2));
		if (measurementOutcome.exitNonZero) {
			throw new Error(`Measurement integrity failed; report is invalid/pending and cannot be adopted: ${measurementIntegrity.reasons.join(', ')}`);
		}
	} catch (error) {
		const failureReasons = [before ? 'runtime-tool-failure' : 'start-preflight-failure'];
		if (measurementMonitor) {
			try {
				const stoppedWindow = await stopActivityMonitor(measurementMonitor);
				activityWindows.push(stoppedWindow);
				failureReasons.push(...validateActivityWindow(stoppedWindow, {
					expectedLabels: measurementLabels,
				}).reasons);
			} catch {
				activityWindows.push({
					adoptable: false,
					sampleCount: 0,
					measuredSessionObserved: false,
					transientExternalActivityDetected: false,
					measuredSessions: [],
					sessions: [],
					reasons: ['activity-monitor-stop-failure'],
				});
				failureReasons.push('activity-monitor-stop-failure');
			}
			measurementMonitor = null;
		}
		if (!allChildrenReaped) failureReasons.push('child-process-not-reaped');
		if (!fs.existsSync(reportPath)) {
			writeRejectedReport(reportPath, {
				phase: before ? 'runtime-failure' : 'start-integrity',
				reasons: failureReasons,
				queryRunCount: explainRunCount, composeIdentity: composeIdentityEvidence(), databaseIdentity,
				capturedSnapshot: before, schemaState: schemaBefore, sourceIdentity,
				datasetId: process.env.DATASET_ID, fixtureRunId: process.env.FIXTURE_RUN_ID,
				crossIssueArtifacts, activityWindows,
			});
		}
		throw error;
	}
}

function validateCrossIssueReport(report) {
	if (report.datasetId !== process.env.DATASET_ID) {
		throw new Error('CROSS_ISSUE_REPORT datasetId does not match DATASET_ID.');
	}
	if (report.fixtureRunId !== process.env.FIXTURE_RUN_ID) {
		throw new Error('CROSS_ISSUE_REPORT fixtureRunId does not match FIXTURE_RUN_ID.');
	}
	if (report.memberCount !== 1000) {
		throw new Error('CROSS_ISSUE_REPORT memberCount must be exactly 1000.');
	}
	if (!report.issueReports || typeof report.issueReports !== 'object') {
		throw new Error('CROSS_ISSUE_REPORT must include issueReports.');
	}
	for (const issueNumber of [192, 193, 195, 196, 197, 198, 199]) {
		if (!report.issueReports[String(issueNumber)]) {
			throw new Error(`CROSS_ISSUE_REPORT is missing issueReports.${issueNumber}.`);
		}
	}
}

function inspectComposeIdentity(container) {
	const output = runCommand('docker', ['inspect', container]);
	const inspected = JSON.parse(output);
	if (!Array.isArray(inspected) || inspected.length !== 1) {
		throw new Error('docker inspect must return exactly one PostgreSQL container.');
	}
	const item = inspected[0];
	const labels = item.Config?.Labels || {};
	const configuredDatabase = (item.Config?.Env || [])
		.find((entry) => entry.startsWith('POSTGRES_DB='))
		?.slice('POSTGRES_DB='.length);
	const composeProject = labels['com.docker.compose.project'];
	const composeService = labels['com.docker.compose.service'];
	if (!composeProject || !composeService) {
		throw new Error('PostgreSQL container is missing actual Docker Compose project/service labels.');
	}
	const exposedPortKeys = new Set([
		...Object.keys(item.Config?.ExposedPorts || {}),
		...Object.keys(item.NetworkSettings?.Ports || {}),
	]);
	const postgresPortKey = [...exposedPortKeys].find((key) => key === '5432/tcp');
	const networkIdentity = Object.entries(item.NetworkSettings?.Networks || {})
		.map(([name, network]) => ({
			name,
			networkId: network.NetworkID || null,
			ipAddress: network.IPAddress || '',
			globalIPv6Address: network.GlobalIPv6Address || '',
		}))
		.sort((left, right) => left.name.localeCompare(right.name));
	const containerNetworkAddresses = networkIdentity
		.flatMap((network) => [network.ipAddress, network.globalIPv6Address])
		.filter(Boolean)
		.sort();
	if (!postgresPortKey || containerNetworkAddresses.length === 0 || !item.State?.StartedAt || !configuredDatabase
		|| !item.Id || !item.Name || !item.Image || !item.Config?.Image) {
		throw new Error('PostgreSQL container inspect data is missing container, image, database, port, network, or start-time identity evidence.');
	}
	return {
		postgresContainerId: item.Id,
		postgresContainerName: item.Name,
		postgresImageId: item.Image,
		postgresImageReference: item.Config?.Image,
		composeProject,
		composeService,
		composeConfigFiles: labels['com.docker.compose.project.config_files'] || null,
		composeWorkingDir: labels['com.docker.compose.project.working_dir'] || null,
		configuredDatabase,
		containerStartedAt: item.State.StartedAt,
		postgresInternalPort: Number(postgresPortKey.split('/')[0]),
		containerNetworkAddresses,
		networkIdentity,
	};
}

function captureDatabaseIdentity() {
	const sql = `
		SELECT json_build_object(
			'serverAddress', inet_server_addr()::text,
			'serverPort', inet_server_port(),
			'database', current_database(),
			'postmasterStartedAt', pg_postmaster_start_time()
		);
	`;
	return JSON.parse(psql(sql));
}

function capturePlannerState(observedTables) {
	const tableList = observedTables.map(sqlLiteral).join(', ');
	const sql = `
		SELECT json_build_object(
			'capturedAt', clock_timestamp(),
			'database', current_database(),
			'serverVersion', current_setting('server_version'),
			'postmasterStartedAt', pg_postmaster_start_time(),
			'settings', (
				SELECT json_object_agg(name, setting ORDER BY name)
				FROM pg_settings
				WHERE name IN (${REQUIRED_PLANNER_SETTINGS.map(sqlLiteral).join(', ')})
			),
			'tableStatistics', (
				SELECT COALESCE(json_agg(json_build_object(
					'table', stats.relname,
					'lastAnalyze', stats.last_analyze,
					'lastAutoanalyze', stats.last_autoanalyze,
					'lastVacuum', stats.last_vacuum,
					'lastAutovacuum', stats.last_autovacuum,
					'vacuumCount', stats.vacuum_count,
					'autovacuumCount', stats.autovacuum_count,
					'nModSinceAnalyze', stats.n_mod_since_analyze,
					'liveTuples', stats.n_live_tup,
					'deadTuples', stats.n_dead_tup,
					'allVisiblePages', class.relallvisible
				) ORDER BY stats.relname), '[]'::json)
				FROM pg_stat_all_tables stats
				JOIN pg_class class ON class.oid = stats.relid
				WHERE stats.schemaname = current_schema() AND stats.relname IN (${tableList})
			),
			'externalActivity', (
				SELECT json_build_object(
					'activeSessionCount', count(*),
					'sessions', COALESCE(json_agg(json_build_object(
						'pid', pid,
						'database', datname,
						'backendType', backend_type,
						'applicationName', application_name,
						'state', state,
						'waitEventType', wait_event_type,
						'waitEvent', wait_event,
						'queryStart', query_start
					) ORDER BY pid) FILTER (WHERE pid IS NOT NULL), '[]'::json)
				)
				FROM pg_stat_activity
				WHERE backend_type = 'client backend'
					AND pid <> pg_backend_pid()
					AND state <> 'idle'
			)
		);
	`;
	const snapshot = JSON.parse(psql(sql));
	snapshot.tableStatistics ??= [];
	return snapshot;
}

function captureSchemaState(observedTables) {
	const tableList = observedTables.map(sqlLiteral).join(', ');
	const raw = JSON.parse(psql(`
		SELECT json_build_object(
			'flyway', (SELECT COALESCE(json_agg(json_build_object(
				'installedRank', installed_rank, 'version', version, 'description', description,
				'type', type, 'script', script, 'checksum', checksum, 'success', success
			) ORDER BY installed_rank), '[]'::json) FROM flyway_schema_history),
			'columns', (SELECT COALESCE(json_agg(json_build_object(
				'table', table_name, 'ordinalPosition', ordinal_position, 'name', column_name,
				'type', data_type, 'udtName', udt_name, 'nullable', is_nullable = 'YES',
				'default', column_default
			) ORDER BY table_name, ordinal_position), '[]'::json)
			FROM information_schema.columns
			WHERE table_schema = current_schema() AND table_name IN (${tableList})),
			'constraints', (SELECT COALESCE(json_agg(json_build_object(
				'table', rel.relname, 'name', con.conname, 'type', con.contype,
				'definition', pg_get_constraintdef(con.oid, true)
			) ORDER BY rel.relname, con.conname), '[]'::json)
			FROM pg_constraint con JOIN pg_class rel ON rel.oid = con.conrelid
			JOIN pg_namespace ns ON ns.oid = rel.relnamespace
			WHERE ns.nspname = current_schema() AND rel.relname IN (${tableList})),
			'indexes', (SELECT COALESCE(json_agg(json_build_object(
				'table', tablename, 'name', indexname, 'definition', indexdef
			) ORDER BY tablename, indexname), '[]'::json)
			FROM pg_indexes WHERE schemaname = current_schema() AND tablename IN (${tableList}))
		);
	`));
	return normalizeSchemaState(raw);
}

function captureAnchorPreflight(variables) {
	return JSON.parse(psql(`
		SELECT json_build_object(
			'campusExists', EXISTS (SELECT 1 FROM campuses WHERE id = :'campus_id'::bigint),
			'memberInCampus', EXISTS (SELECT 1 FROM campus_members WHERE campus_id = :'campus_id'::bigint AND user_id = :'member_user_id'::bigint AND status = 'ACTIVE'),
			'pollInCampus', EXISTS (SELECT 1 FROM polls WHERE id = :'poll_id'::bigint AND campus_id = :'campus_id'::bigint),
			'mealPollInCampus', EXISTS (SELECT 1 FROM polls p JOIN meal_poll_settlements s ON s.poll_id = p.id WHERE p.id = :'meal_poll_id'::bigint AND p.campus_id = :'campus_id'::bigint),
			'paymentAccountInCampus', EXISTS (SELECT 1 FROM payment_accounts WHERE id = :'payment_account_id'::bigint AND campus_id = :'campus_id'::bigint AND is_active = true),
			'prayerSeasonInCampus', EXISTS (SELECT 1 FROM prayer_seasons WHERE id = :'prayer_season_id'::bigint AND campus_id = :'campus_id'::bigint),
			'prayerWeekInSeason', EXISTS (SELECT 1 FROM prayer_weeks WHERE id = :'prayer_week_id'::bigint AND season_id = :'prayer_season_id'::bigint AND campus_id = :'campus_id'::bigint AND week_start_date = :'week_start_date'::date),
			'memberStatus', (SELECT status FROM campus_members WHERE campus_id = :'campus_id'::bigint AND user_id = :'member_user_id'::bigint),
			'pollStatus', (SELECT status FROM polls WHERE id = :'poll_id'::bigint),
			'pollTitle', (SELECT title FROM polls WHERE id = :'poll_id'::bigint),
			'mealPollStatus', (SELECT status FROM polls WHERE id = :'meal_poll_id'::bigint),
			'mealPollTitle', (SELECT title FROM polls WHERE id = :'meal_poll_id'::bigint),
			'paymentAccountIsActive', (SELECT is_active FROM payment_accounts WHERE id = :'payment_account_id'::bigint),
			'paymentAccountOwnerUserId', (SELECT owner_user_id FROM payment_accounts WHERE id = :'payment_account_id'::bigint),
			'paymentAccountNickname', (SELECT nickname FROM payment_accounts WHERE id = :'payment_account_id'::bigint),
			'prayerSeasonStatus', (SELECT status FROM prayer_seasons WHERE id = :'prayer_season_id'::bigint),
			'prayerSeasonName', (SELECT name FROM prayer_seasons WHERE id = :'prayer_season_id'::bigint),
			'prayerWeekStatus', (SELECT status FROM prayer_weeks WHERE id = :'prayer_week_id'::bigint),
			'memberCount', (SELECT count(*) FROM campus_members WHERE campus_id = :'campus_id'::bigint AND status = 'ACTIVE'),
			'pollCount', (SELECT count(*) FROM polls WHERE id = :'poll_id'::bigint AND campus_id = :'campus_id'::bigint),
			'mealPollCount', (SELECT count(*) FROM polls p JOIN meal_poll_settlements s ON s.poll_id = p.id WHERE p.id = :'meal_poll_id'::bigint AND p.campus_id = :'campus_id'::bigint),
			'paymentAccountCount', (SELECT count(*) FROM payment_accounts WHERE id = :'payment_account_id'::bigint AND campus_id = :'campus_id'::bigint AND is_active = true),
			'prayerSeasonCount', (SELECT count(*) FROM prayer_seasons WHERE id = :'prayer_season_id'::bigint AND campus_id = :'campus_id'::bigint),
			'prayerWeekCount', (SELECT count(*) FROM prayer_weeks WHERE id = :'prayer_week_id'::bigint AND season_id = :'prayer_season_id'::bigint AND campus_id = :'campus_id'::bigint AND week_start_date = :'week_start_date'::date)
		);
	`, variables));
}

function captureSourceIdentity(inventory, sourceManifest, sourceManifestText, sqlSources = null, controlSources = null) {
	const gitCommit = runCommand('git', ['rev-parse', 'HEAD']).trim();
	const dirtyOutput = runCommand('git', ['status', '--porcelain', '--untracked-files=normal']).trim();
	return buildSourceIdentity({
		repositoryRoot,
		scenarioRoot,
		gitCommit,
		gitDirty: dirtyOutput.length > 0,
		sqlFiles: inventory.queries.map((query) => query.sqlFile),
		sqlSources,
		productionSourceRefs: inventory.queries.flatMap((query) => query.productionSourceRefs),
		inventoryPath: 'inventory.json',
		reportContractPath: 'report-contract.json',
		sourceManifestPath: 'source-manifest.json',
		controlSources,
		expected: {
			productionSourceRefs: sourceManifest.productionSourceRefs,
			manifestSha256: createHash('sha256').update(sourceManifestText).digest('hex'),
		},
	});
}

async function startActivityMonitor(reportDirectory, fileStem, measuredApplicationName) {
	const outputPath = path.join(reportDirectory, 'raw', `${fileStem}__activity-window.json`);
	const child = spawn(process.execPath, [
		path.join(scenarioRoot, 'activity-monitor-worker.mjs'), outputPath, measuredApplicationName, String(activitySampleIntervalMs), process.env.PGDATABASE,
	], {
		cwd: scenarioRoot,
		env: { ...process.env, PGAPPNAME: `faithlog-194-observer-${process.pid}` },
		stdio: ['pipe', 'pipe', 'pipe'],
	});
	let stderr = '';
	let stdoutBuffer = '';
	let ready = false;
	let contaminated = false;
	const preparedTokens = new Set();
	const acknowledgedTokens = new Set();
	const unregisteredTokens = new Set();
	const cancelledTokens = new Set();
	const preparationWaiters = new Map();
	const acknowledgmentWaiters = new Map();
	const unregistrationWaiters = new Map();
	const cancellationWaiters = new Map();
	child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
	child.stdin.on('error', () => {});
	let resolveReady;
	let resolveContamination;
	const readyPromise = new Promise((resolve) => { resolveReady = resolve; });
	const contaminationPromise = new Promise((resolve) => { resolveContamination = resolve; });
	child.stdout.on('data', (chunk) => {
		stdoutBuffer += chunk.toString();
		let newline;
		while ((newline = stdoutBuffer.indexOf('\n')) >= 0) {
			const line = stdoutBuffer.slice(0, newline);
			stdoutBuffer = stdoutBuffer.slice(newline + 1);
			if (line === 'ready' && !ready) {
				ready = true;
				resolveReady();
			}
			const prepared = /^measured-prepared:([a-f0-9]{32})$/.exec(line);
			if (prepared) {
				const registrationToken = prepared[1];
				preparedTokens.add(registrationToken);
				preparationWaiters.get(registrationToken)?.();
				preparationWaiters.delete(registrationToken);
			}
			const observed = /^measured-observed:([a-f0-9]{32})$/.exec(line);
			if (observed) {
				const registrationToken = observed[1];
				acknowledgedTokens.add(registrationToken);
				acknowledgmentWaiters.get(registrationToken)?.();
				acknowledgmentWaiters.delete(registrationToken);
			}
			const unregistered = /^measured-unregistered:([a-f0-9]{32})$/.exec(line);
			if (unregistered) {
				const registrationToken = unregistered[1];
				unregisteredTokens.add(registrationToken);
				unregistrationWaiters.get(registrationToken)?.();
				unregistrationWaiters.delete(registrationToken);
			}
			const cancelled = /^measured-cancelled:([a-f0-9]{32})$/.exec(line);
			if (cancelled) {
				const registrationToken = cancelled[1];
				cancelledTokens.add(registrationToken);
				cancellationWaiters.get(registrationToken)?.();
				cancellationWaiters.delete(registrationToken);
			}
			if (line === 'external-observed' && !contaminated) {
				contaminated = true;
				resolveContamination(true);
			}
		}
	});
	const exitPromise = new Promise((resolve) => {
		child.once('error', (error) => resolve({ error, code: null }));
		child.once('exit', (code) => resolve({ error: null, code }));
	});
	try {
		const readiness = await withTimeout(Promise.race([
			readyPromise.then(() => ({ ready: true })),
			exitPromise.then(({ code }) => ({ ready: false, code })),
		]), 5000, { ready: false, timeout: true });
		if (!readiness.ready) {
			throw new Error(readiness.timeout
				? 'Activity monitor readiness timed out.'
				: `Activity monitor exited before readiness with status ${readiness.code}: ${stderr.trim()}`);
		}
	} catch (error) {
		await terminateChild(child, exitPromise);
		throw error;
	}
	return {
		child, outputPath, exitPromise, preparedTokens, acknowledgedTokens, unregisteredTokens, cancelledTokens,
		preparationWaiters, acknowledgmentWaiters, unregistrationWaiters, cancellationWaiters, contaminationPromise,
		get contaminated() { return contaminated; },
	};
}

async function prepareMeasured(monitor, applicationName, label) {
	if (monitor.contaminated) throw new Error('External activity was observed before the measured query started.');
	const registrationToken = randomBytes(16).toString('hex');
	const registration = { applicationName, registrationToken, label };
	const acknowledgment = monitor.preparedTokens.has(registrationToken)
		? Promise.resolve(true)
		: new Promise((resolve) => monitor.preparationWaiters.set(registrationToken, () => resolve(true)));
	await writeMonitorControl(monitor, `PREPARE:${Buffer.from(JSON.stringify(registration)).toString('base64url')}\n`);
	const prepared = await withTimeout(Promise.race([
		acknowledgment,
		monitor.exitPromise.then(() => false),
	]), 5000, false);
	monitor.preparationWaiters.delete(registrationToken);
	if (!prepared) throw new Error(`Activity monitor did not prepare measured identity for ${label}.`);
	return registrationToken;
}

async function bindMeasuredIdentity(monitor, registrationToken, identity, label) {
	if (!Number.isSafeInteger(identity?.pid) || identity.pid <= 0
		|| typeof identity.applicationName !== 'string'
		|| typeof identity.backendStart !== 'string') {
		throw new Error('Measured PostgreSQL backend identity is invalid.');
	}
	const registration = { ...identity, registrationToken };
	const acknowledgment = monitor.acknowledgedTokens.has(registrationToken)
		? Promise.resolve(true)
		: new Promise((resolve) => monitor.acknowledgmentWaiters.set(registrationToken, () => resolve(true)));
	await writeMonitorControl(monitor, `BIND:${Buffer.from(JSON.stringify(registration)).toString('base64url')}\n`);
	const observed = await withTimeout(Promise.race([
		acknowledgment,
		monitor.exitPromise.then(() => false),
		monitor.contaminationPromise.then(() => false),
	]), 5000, false);
	monitor.acknowledgmentWaiters.delete(registrationToken);
	if (!observed) throw new Error(`Activity monitor did not observe measured PostgreSQL backend identity for ${label}.`);
}

async function unregisterMeasured(monitor, registrationToken) {
	const acknowledgment = monitor.unregisteredTokens.has(registrationToken)
		? Promise.resolve(true)
		: new Promise((resolve) => monitor.unregistrationWaiters.set(registrationToken, () => resolve(true)));
	await writeMonitorControl(monitor, `UNREGISTER:${registrationToken}\n`);
	const unregistered = await withTimeout(Promise.race([
		acknowledgment,
		monitor.exitPromise.then(() => false),
	]), 5000, false);
	monitor.unregistrationWaiters.delete(registrationToken);
	if (!unregistered) throw new Error('Activity monitor did not acknowledge measured backend unregistration.');
}

async function cancelMeasured(monitor, registrationToken) {
	const acknowledgment = monitor.cancelledTokens.has(registrationToken)
		? Promise.resolve(true)
		: new Promise((resolve) => monitor.cancellationWaiters.set(registrationToken, () => resolve(true)));
	await writeMonitorControl(monitor, `CANCEL:${registrationToken}\n`);
	const cancelled = await withTimeout(Promise.race([
		acknowledgment,
		monitor.exitPromise.then(() => false),
	]), 5000, false);
	monitor.cancellationWaiters.delete(registrationToken);
	if (!cancelled) throw new Error('Activity monitor did not acknowledge measured identity cancellation.');
}

function writeMonitorControl(monitor, line) {
	return new Promise((resolve, reject) => {
		monitor.child.stdin.write(line, (error) => error ? reject(error) : resolve());
	});
}

async function stopActivityMonitor(monitor) {
	const result = await terminateChild(monitor.child, monitor.exitPromise);
	if (result.error) throw result.error;
	if (result.code !== 0) throw new Error(`Activity monitor exited with status ${result.code}.`);
	return JSON.parse(fs.readFileSync(monitor.outputPath, 'utf8'));
}

async function terminateChild(child, exitPromise) {
	try {
		return await terminateChildProcess(child, exitPromise);
	} catch (error) {
		if (error.code === 'CHILD_NOT_REAPED') allChildrenReaped = false;
		throw error;
	}
}

function combineActivityWindows(windows, measurementLabels) {
	const reasons = windows.flatMap((window) => validateActivityWindow(window, {
		expectedLabels: measurementLabels,
	}).reasons);
	return { adoptable: reasons.length === 0, reasons: [...new Set(reasons)] };
}

function validateArtifactContinuity(before, after) {
	const beforeHashes = before.map(({ issueNumber, relativePath, sha256 }) => ({ issueNumber, relativePath, sha256 }));
	const afterHashes = after.map(({ issueNumber, relativePath, sha256 }) => ({ issueNumber, relativePath, sha256 }));
	const stable = JSON.stringify(beforeHashes) === JSON.stringify(afterHashes);
	return { stable, reasons: stable ? [] : ['cross-issue-artifact-changed'] };
}

async function explain(selectSql, variables, applicationName, monitor, measurementLabel) {
	const preamble = `
		BEGIN READ ONLY;
		SET LOCAL statement_timeout = '120s';
		SELECT 'FAITHLOG_BACKEND_IDENTITY:' || json_build_object(
			'pid', pg_backend_pid(),
			'applicationName', current_setting('application_name'),
			'backendStart', (SELECT backend_start FROM pg_stat_activity WHERE pid = pg_backend_pid())
		)::text;
	`;
	const measuredStatement = `
		EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
		${selectSql};
		ROLLBACK;
	`;
	const args = ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1'];
	for (const [name, value] of Object.entries(variables)) args.push('-v', `${name}=${value}`);
	const registrationToken = await prepareMeasured(monitor, applicationName, measurementLabel);
	if (monitor.contaminated) {
		await cancelMeasured(monitor, registrationToken);
		throw new Error('External activity was observed before measured psql creation.');
	}
	let registrationObserved = false;
	const child = spawn('psql', args, {
		cwd: scenarioRoot,
		env: { ...process.env, PGAPPNAME: applicationName, PGCONNECT_TIMEOUT: '5' },
		stdio: ['pipe', 'pipe', 'pipe'],
	});
	let stdout = '';
	let stderr = '';
	let resolveBackendIdentity;
	const backendIdentityPromise = new Promise((resolve) => { resolveBackendIdentity = resolve; });
	child.stdout.on('data', (chunk) => {
		stdout += chunk.toString();
		const match = stdout.match(/FAITHLOG_BACKEND_IDENTITY:(\{[^\r\n]+\})\r?\n/);
		if (match) resolveBackendIdentity(JSON.parse(match[1]));
	});
	child.stderr.on('data', (chunk) => { stderr += chunk.toString(); });
	child.stdin.on('error', () => {});
	const exitPromise = new Promise((resolve) => {
		child.once('error', (error) => resolve({ error, code: null }));
		child.once('exit', (code) => resolve({ error: null, code }));
	});
	const closePromise = new Promise((resolve) => child.once('close', (code) => resolve({ code })));
	try {
		child.stdin.write(preamble);
		const identityOutcome = await withTimeout(Promise.race([
			backendIdentityPromise.then((identity) => ({ type: 'identity', identity })),
			exitPromise.then(() => ({ type: 'psql-exited' })),
			monitor.contaminationPromise.then(() => ({ type: 'contaminated' })),
			monitor.exitPromise.then(() => ({ type: 'monitor-exited' })),
		]), 10000, null);
		if (identityOutcome?.type === 'contaminated') {
			throw new Error('Transient external activity was observed while waiting for measured backend identity.');
		}
		if (identityOutcome?.type === 'monitor-exited') {
			throw new Error('Activity monitor exited while waiting for measured backend identity.');
		}
		const backendIdentity = identityOutcome?.type === 'identity' ? identityOutcome.identity : null;
		if (!backendIdentity || backendIdentity.applicationName !== applicationName) {
			throw new Error('psql did not expose its exact measured backend identity to the activity monitor.');
		}
		await bindMeasuredIdentity(monitor, registrationToken, backendIdentity, measurementLabel);
		registrationObserved = true;
		if (monitor.contaminated) throw new Error('External activity was observed before EXPLAIN started.');
		child.stdin.end(measuredStatement);
		const completion = await withTimeout(Promise.race([
			closePromise.then((result) => ({ type: 'closed', result })),
			monitor.contaminationPromise.then(() => ({ type: 'contaminated' })),
			monitor.exitPromise.then(() => ({ type: 'monitor-exited' })),
		]), 135000, null);
		if (!completion) {
			throw new Error('Measured psql exceeded its 135 second client wall timeout.');
		}
		if (completion.type === 'contaminated') {
			throw new Error('Transient external activity was observed; aborting the measured query immediately.');
		}
		if (completion.type === 'monitor-exited') {
			throw new Error('Activity monitor exited during EXPLAIN; aborting the measured query immediately.');
		}
		const result = completion.result;
		if (result.code !== 0) throw new Error(`psql failed with status ${result.code}: ${stderr.trim()}`);
		const jsonText = stdout.replace(/^\s*FAITHLOG_BACKEND_IDENTITY:\{[^\r\n]+\}\r?\n/, '').trim();
		return JSON.parse(jsonText);
	} catch (error) {
		await terminateChild(child, exitPromise);
		throw error;
	} finally {
		if (registrationObserved || monitor.acknowledgedTokens.has(registrationToken)) {
			await unregisterMeasured(monitor, registrationToken);
		}
		else await cancelMeasured(monitor, registrationToken);
	}
}

function psql(sql, variables = {}, applicationName = 'faithlog-issue-194-control') {
	const args = ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1'];
	for (const [name, value] of Object.entries(variables)) {
		args.push('-v', `${name}=${value}`);
	}
	args.push('-c', sql);
	return runCommand('psql', args, {
		...process.env,
		PGAPPNAME: applicationName,
		PGCONNECT_TIMEOUT: '5',
	}).trim();
}

function runCommand(command, args, env = process.env) {
	const result = spawnSync(command, args, {
		cwd: scenarioRoot,
		env,
		encoding: 'utf8',
		maxBuffer: 64 * 1024 * 1024,
		timeout: 30000,
		killSignal: 'SIGKILL',
	});
	if (result.error) {
		throw result.error;
	}
	if (result.status !== 0) {
		throw new Error(`${command} failed with status ${result.status}: ${result.stderr.trim()}`);
	}
	return result.stdout;
}

function compareAutoanalyze(before, after) {
	const beforeByTable = new Map(before.map((row) => [row.table, row]));
	return after.map((row) => {
		const previous = beforeByTable.get(row.table) || {};
		return {
			table: row.table,
			lastAnalyzeBefore: previous.lastAnalyze ?? null,
			lastAnalyzeAfter: row.lastAnalyze ?? null,
			lastAutoanalyzeBefore: previous.lastAutoanalyze ?? null,
			lastAutoanalyzeAfter: row.lastAutoanalyze ?? null,
			nModSinceAnalyzeBefore: previous.nModSinceAnalyze ?? null,
			nModSinceAnalyzeAfter: row.nModSinceAnalyze ?? null,
			changed: previous.lastAnalyze !== row.lastAnalyze
				|| previous.lastAutoanalyze !== row.lastAutoanalyze
				|| previous.nModSinceAnalyze !== row.nModSinceAnalyze,
		};
	});
}

function stripTrailingSemicolon(sql) {
	return sql.trim().replace(/;+\s*$/, '');
}

function sqlLiteral(value) {
	return `'${String(value).replaceAll("'", "''")}'`;
}

function writeJson(filePath, value) {
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}
