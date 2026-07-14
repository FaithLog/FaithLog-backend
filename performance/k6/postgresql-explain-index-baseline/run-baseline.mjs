import { createHash } from 'node:crypto';
import { spawnSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { normalizeExplain } from './normalize-plan.mjs';

const scenarioRoot = path.dirname(fileURLToPath(import.meta.url));
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
];

for (const name of requiredRuntimeInputs) {
	if (!process.env[name]) {
		throw new Error(`${name} is required at runtime.`);
	}
}
if (process.env.ALLOW_EXPLAIN_ANALYZE !== 'true') {
	throw new Error('EXPLAIN ANALYZE is blocked. Set ALLOW_EXPLAIN_ANALYZE=true only in the approved isolated measurement window.');
}

const warmRuns = Number(process.env.WARM_RUNS || 3);
if (!Number.isInteger(warmRuns) || warmRuns < 1 || warmRuns > 20) {
	throw new Error('WARM_RUNS must be an integer from 1 through 20.');
}

fs.mkdirSync(reportsRoot, { recursive: true });
const lockPath = path.resolve(scenarioRoot, '..', '.faithlog-performance-runner.lock');
try {
	fs.mkdirSync(lockPath, { recursive: false });
} catch (error) {
	if (error.code === 'EEXIST') {
		throw new Error('Another performance or load run holds the Issue #194 runner lock. Do not run scenarios in parallel.');
	}
	throw error;
}

let exitCode = 0;
try {
	await run();
} catch (error) {
	exitCode = 1;
	console.error(error instanceof Error ? error.message : String(error));
} finally {
	fs.rmSync(lockPath, { recursive: true, force: true });
}
process.exitCode = exitCode;

async function run() {
	const crossIssueReportPath = path.resolve(process.env.CROSS_ISSUE_REPORT);
	const crossIssueReportText = fs.readFileSync(crossIssueReportPath, 'utf8');
	const crossIssueReport = JSON.parse(crossIssueReportText);
	validateCrossIssueReport(crossIssueReport);

	const inventory = JSON.parse(fs.readFileSync(path.join(scenarioRoot, 'inventory.json'), 'utf8'));
	const reportContract = JSON.parse(fs.readFileSync(path.join(scenarioRoot, 'report-contract.json'), 'utf8'));
	const composeIdentity = inspectComposeIdentity(process.env.POSTGRES_CONTAINER);
	const variables = validateAnchors(crossIssueReport.anchors);
	const startedAt = new Date().toISOString();
	const reportDirectory = path.join(
		reportsRoot,
		`${safeSegment(process.env.DATASET_ID)}__${safeSegment(process.env.FIXTURE_RUN_ID)}__${startedAt.replaceAll(':', '-')}`
	);
	fs.mkdirSync(path.join(reportDirectory, 'raw'), { recursive: true });
	fs.mkdirSync(path.join(reportDirectory, 'normalized'), { recursive: true });

	const before = capturePlannerState(inventory.observedTables);
	const queryReports = [];
	for (const query of inventory.queries) {
		const sqlPath = path.join(scenarioRoot, query.sqlFile);
		const selectSql = stripTrailingSemicolon(fs.readFileSync(sqlPath, 'utf8'));
		assertReadOnlySelect(selectSql, query.id);

		const phases = [
			{ id: 'cold_like_observation', runs: 1, cacheResetPerformed: false },
			{ id: 'warm_cache', runs: warmRuns, cacheResetPerformed: false },
		];
		for (const phase of phases) {
			for (let runNumber = 1; runNumber <= phase.runs; runNumber += 1) {
				const capturedAt = new Date().toISOString();
				const rawExplain = explain(selectSql, variables);
				const normalized = normalizeExplain(rawExplain);
				const fileStem = `${query.id}__${phase.id}__${String(runNumber).padStart(2, '0')}`;
				writeJson(path.join(reportDirectory, 'raw', `${fileStem}.json`), rawExplain);
				writeJson(path.join(reportDirectory, 'normalized', `${fileStem}.json`), {
					queryId: query.id,
					issueNumber: query.issueNumber,
					phase: phase.id,
					runNumber,
					cacheResetPerformed: false,
					capturedAt,
					...normalized,
				});
				queryReports.push({
					queryId: query.id,
					issueNumber: query.issueNumber,
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
	const after = capturePlannerState(inventory.observedTables);
	const finishedAt = new Date().toISOString();
	const report = {
		schemaVersion: reportContract.schemaVersion,
		status: 'measured-before-index-baseline',
		issueNumber: 194,
		datasetId: process.env.DATASET_ID,
		fixtureRunId: process.env.FIXTURE_RUN_ID,
		memberCount: crossIssueReport.memberCount,
		crossIssueReport: {
			path: crossIssueReportPath,
			sha256: createHash('sha256').update(crossIssueReportText).digest('hex'),
			issueReports: crossIssueReport.issueReports,
		},
		startedAt,
		finishedAt,
		sequentialExecution: true,
		cacheResetPerformed: false,
		cacheInterpretation: reportContract.measurementPhases,
		credentialPolicy: {
			source: 'runtime-environment-only',
			requiredVariables: ['PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD'],
			valuesRecorded: false,
		},
		composeIdentity,
		plannerState: {
			before,
			after,
			autoanalyzeChanges: compareAutoanalyze(before.tableStatistics, after.tableStatistics),
		},
		queries: queryReports,
	};
	const reportPath = path.join(reportDirectory, 'baseline-report.json');
	writeJson(reportPath, report);
	console.log(JSON.stringify({ reportPath, queryRunCount: queryReports.length }, null, 2));
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

function validateAnchors(anchors) {
	if (!anchors || typeof anchors !== 'object') {
		throw new Error('CROSS_ISSUE_REPORT must include anchors.');
	}
	const integerFields = [
		'campus_id', 'poll_id', 'meal_poll_id', 'member_user_id', 'payment_account_id',
		'prayer_season_id', 'prayer_week_id', 'page_size', 'page_offset',
	];
	const isoDateFields = ['week_start_date'];
	const isoInstantFields = ['stale_before', 'range_start', 'range_end'];
	const variables = {};
	for (const field of integerFields) {
		const value = Number(anchors[field]);
		if (!Number.isSafeInteger(value) || value < 0) {
			throw new Error(`anchors.${field} must be a non-negative safe integer.`);
		}
		variables[field] = String(value);
	}
	for (const field of isoDateFields) {
		if (!/^\d{4}-\d{2}-\d{2}$/.test(anchors[field])) {
			throw new Error(`anchors.${field} must use YYYY-MM-DD.`);
		}
		variables[field] = anchors[field];
	}
	for (const field of isoInstantFields) {
		if (typeof anchors[field] !== 'string' || Number.isNaN(Date.parse(anchors[field]))) {
			throw new Error(`anchors.${field} must be an ISO-8601 instant.`);
		}
		variables[field] = anchors[field];
	}
	if (typeof anchors.keyword_pattern !== 'string' || anchors.keyword_pattern.length > 200) {
		throw new Error('anchors.keyword_pattern must be a string of at most 200 characters.');
	}
	variables.keyword_pattern = anchors.keyword_pattern;
	return variables;
}

function inspectComposeIdentity(container) {
	const output = runCommand('docker', ['inspect', container]);
	const inspected = JSON.parse(output);
	if (!Array.isArray(inspected) || inspected.length !== 1) {
		throw new Error('docker inspect must return exactly one PostgreSQL container.');
	}
	const item = inspected[0];
	const labels = item.Config?.Labels || {};
	const composeProject = labels['com.docker.compose.project'];
	const composeService = labels['com.docker.compose.service'];
	if (!composeProject || !composeService) {
		throw new Error('PostgreSQL container is missing actual Docker Compose project/service labels.');
	}
	return {
		postgresContainerId: item.Id,
		postgresContainerName: item.Name,
		composeProject,
		composeService,
		composeConfigFiles: labels['com.docker.compose.project.config_files'] || null,
		composeWorkingDir: labels['com.docker.compose.project.working_dir'] || null,
	};
}

function capturePlannerState(observedTables) {
	const tableList = observedTables.map(sqlLiteral).join(', ');
	const sql = `
		SELECT json_build_object(
			'capturedAt', clock_timestamp(),
			'serverVersion', current_setting('server_version'),
			'settings', (
				SELECT json_object_agg(name, setting ORDER BY name)
				FROM pg_settings
				WHERE name IN (
					'random_page_cost', 'seq_page_cost', 'cpu_tuple_cost', 'cpu_index_tuple_cost',
					'effective_cache_size', 'work_mem', 'shared_buffers', 'default_statistics_target',
					'enable_seqscan', 'enable_indexscan', 'enable_indexonlyscan', 'enable_bitmapscan',
					'enable_hashjoin', 'enable_mergejoin', 'enable_nestloop'
				)
			),
			'tableStatistics', (
				SELECT COALESCE(json_agg(json_build_object(
					'table', relname,
					'lastAnalyze', last_analyze,
					'lastAutoanalyze', last_autoanalyze,
					'nModSinceAnalyze', n_mod_since_analyze,
					'liveTuples', n_live_tup,
					'deadTuples', n_dead_tup
				) ORDER BY relname), '[]'::json)
				FROM pg_stat_all_tables
				WHERE schemaname = current_schema() AND relname IN (${tableList})
			),
			'externalActivity', (
				SELECT json_build_object(
					'activeSessionCount', count(*),
					'sessions', COALESCE(json_agg(json_build_object(
						'pid', pid,
						'applicationName', application_name,
						'state', state,
						'waitEventType', wait_event_type,
						'waitEvent', wait_event,
						'queryStart', query_start
					) ORDER BY pid) FILTER (WHERE pid IS NOT NULL), '[]'::json)
				)
				FROM pg_stat_activity
				WHERE datname = current_database()
					AND pid <> pg_backend_pid()
					AND state <> 'idle'
					AND application_name <> 'faithlog-issue-194-explain'
			)
		);
	`;
	const snapshot = JSON.parse(psql(sql));
	snapshot.tableStatistics ??= [];
	return snapshot;
}

function explain(selectSql, variables) {
	const transaction = `
		BEGIN READ ONLY;
		SET LOCAL statement_timeout = '120s';
		EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON)
		${selectSql};
		ROLLBACK;
	`;
	return JSON.parse(psql(transaction, variables));
}

function psql(sql, variables = {}) {
	const args = ['-X', '-q', '-A', '-t', '-v', 'ON_ERROR_STOP=1'];
	for (const [name, value] of Object.entries(variables)) {
		args.push('-v', `${name}=${value}`);
	}
	args.push('-c', sql);
	return runCommand('psql', args, {
		...process.env,
		PGAPPNAME: 'faithlog-issue-194-explain',
	}).trim();
}

function runCommand(command, args, env = process.env) {
	const result = spawnSync(command, args, {
		cwd: scenarioRoot,
		env,
		encoding: 'utf8',
		maxBuffer: 64 * 1024 * 1024,
	});
	if (result.error) {
		throw result.error;
	}
	if (result.status !== 0) {
		throw new Error(`${command} failed with status ${result.status}: ${result.stderr.trim()}`);
	}
	return result.stdout;
}

function assertReadOnlySelect(sql, queryId) {
	if (!/^\s*(?:--[^\n]*\n\s*)*(?:WITH|SELECT)\b/i.test(sql)) {
		throw new Error(`${queryId} must start with SELECT or a read-only WITH query.`);
	}
	if (/\b(?:INSERT|UPDATE|DELETE|MERGE|CREATE|ALTER|DROP|TRUNCATE|VACUUM|ANALYZE|CALL|COPY)\b/i.test(sql)) {
		throw new Error(`${queryId} contains a forbidden write, DDL, maintenance, or copy statement.`);
	}
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

function safeSegment(value) {
	if (!/^[A-Za-z0-9._-]+$/.test(value)) {
		throw new Error('DATASET_ID and FIXTURE_RUN_ID may contain only letters, digits, dot, underscore, and hyphen.');
	}
	return value;
}

function sqlLiteral(value) {
	return `'${String(value).replaceAll("'", "''")}'`;
}

function writeJson(filePath, value) {
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { mode: 0o600 });
}
