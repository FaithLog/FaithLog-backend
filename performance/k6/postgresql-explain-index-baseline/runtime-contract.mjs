import fs from 'node:fs';
import path from 'node:path';

export const REQUIRED_PLANNER_SETTINGS = [
	'random_page_cost', 'seq_page_cost', 'cpu_tuple_cost', 'cpu_index_tuple_cost',
	'effective_cache_size', 'work_mem', 'shared_buffers', 'default_statistics_target',
	'enable_seqscan', 'enable_indexscan', 'enable_indexonlyscan', 'enable_bitmapscan',
	'enable_hashjoin', 'enable_mergejoin', 'enable_nestloop',
];

export function parseWarmRuns(value) {
	if (value === undefined || value === null || value === '') {
		throw new Error('WARM_RUNS is required at runtime; no measurement workload default is approved.');
	}
	const parsed = Number(value);
	if (!Number.isInteger(parsed) || parsed < 1 || parsed > 20) {
		throw new Error('WARM_RUNS must be an integer from 1 through 20.');
	}
	return parsed;
}

export function parseActivitySampleInterval(value) {
	if (value === undefined || value === null || value === '') {
		throw new Error('ACTIVITY_SAMPLE_INTERVAL_MS is required at runtime; no observer cadence default is approved.');
	}
	if (!/^[1-9]\d*$/.test(String(value))) {
		throw new Error('ACTIVITY_SAMPLE_INTERVAL_MS must be a positive integer supplied for the approved measurement window.');
	}
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed > 2_147_483_647) {
		throw new Error('ACTIVITY_SAMPLE_INTERVAL_MS must fit the positive Node.js timer integer range.');
	}
	return parsed;
}

export function parsePageSize(value) {
	const parsed = Number(value);
	if (!Number.isSafeInteger(parsed) || parsed < 1) {
		throw new Error('anchors.page_size must be a positive safe integer.');
	}
	return parsed;
}

export function canonicalProjectLockPath(composeProject, lockRoot = '/tmp') {
	if (typeof composeProject !== 'string' || !/^[A-Za-z0-9._-]+$/.test(composeProject)) {
		throw new Error('Actual Docker Compose project must contain only letters, digits, dot, underscore, and hyphen.');
	}
	return path.join(lockRoot, `faithlog-performance-${composeProject}.lock`);
}

export function acquireProjectLock(composeProject, owner, lockRoot = '/tmp') {
	const lockPath = canonicalProjectLockPath(composeProject, lockRoot);
	try {
		fs.mkdirSync(lockPath, { recursive: false, mode: 0o700 });
	} catch (error) {
		if (error.code === 'EEXIST') {
			throw new Error(`Another performance or load run already holds ${lockPath}. Do not run scenarios in parallel.`);
		}
		throw error;
	}
	const stat = fs.statSync(lockPath);
	return { path: lockPath, owner, acquired: true, device: stat.dev, inode: stat.ino };
}

export function releaseProjectLock(lock) {
	if (!lock?.acquired) {
		return;
	}
	assertProjectLockOwned(lock);
	try {
		fs.rmdirSync(lock.path);
		lock.acquired = false;
	} catch (error) {
		throw error;
	}
}

export function assertProjectLockOwned(lock) {
	if (!lock?.acquired) {
		throw new Error('Performance runner lock is not currently owned.');
	}
	let current;
	try {
		current = fs.statSync(lock.path);
	} catch (error) {
		throw new Error(`Performance runner lock disappeared after acquisition: ${lock.path}`, { cause: error });
	}
	if (current.dev !== lock.device || current.ino !== lock.inode) {
		throw new Error(`Refusing to release ${lock.path}: lock ownership changed after acquisition.`);
	}
	if (fs.readdirSync(lock.path).length > 0) {
		throw new Error(`Refusing to release ${lock.path}: owned lock directory is not empty.`);
	}
}

export function validateDatabaseIdentity(inspected, databaseIdentity, expectedDatabase) {
	if (inspected.configuredDatabase !== expectedDatabase) {
		throw new Error(`PGDATABASE ${expectedDatabase} does not match inspected container POSTGRES_DB ${inspected.configuredDatabase}.`);
	}
	if (databaseIdentity.database !== expectedDatabase) {
		throw new Error(`Connected database ${databaseIdentity.database} does not match PGDATABASE ${expectedDatabase}.`);
	}
	if (!Array.isArray(inspected.containerNetworkAddresses)
		|| !inspected.containerNetworkAddresses.includes(databaseIdentity.serverAddress)
		|| databaseIdentity.serverPort !== inspected.postgresInternalPort) {
		throw new Error('psql target is not the inspected PostgreSQL container network endpoint.');
	}
	const containerStartedAt = Date.parse(inspected.containerStartedAt);
	const postmasterStartedAt = Date.parse(databaseIdentity.postmasterStartedAt);
	if (!Number.isFinite(containerStartedAt)
		|| !Number.isFinite(postmasterStartedAt)
		|| postmasterStartedAt < containerStartedAt) {
		throw new Error('PostgreSQL postmaster start time does not belong to the inspected container lifetime.');
	}
}

export function validateDatabaseContinuity(before, after) {
	const fields = ['serverAddress', 'serverPort', 'database', 'postmasterStartedAt'];
	const changed = fields.filter((field) => before?.[field] !== after?.[field]);
	return {
		stable: changed.length === 0,
		reasons: changed.map((field) => `database-identity-changed:${field}`),
	};
}

export function validateComposeIdentityContinuity(before, after) {
	const fields = [
		'postgresContainerId', 'postgresContainerName', 'postgresImageId', 'postgresImageReference',
		'containerStartedAt', 'composeProject', 'composeService', 'composeConfigFiles',
		'composeWorkingDir', 'configuredDatabase', 'postgresInternalPort',
		'containerNetworkAddresses', 'networkIdentity',
	];
	const changedFields = fields.filter((field) => canonicalValue(before?.[field]) !== canonicalValue(after?.[field]));
	const complete = isCompleteComposeIdentity(before) && isCompleteComposeIdentity(after);
	const reasons = [];
	if (!complete) reasons.push('compose-container-identity-incomplete');
	if (changedFields.length > 0) reasons.push('compose-container-identity-changed');
	return {
		stable: complete && changedFields.length === 0,
		reasons,
		changedFields,
	};
}

function isCompleteComposeIdentity(identity) {
	const stringFields = [
		'postgresContainerId', 'postgresContainerName', 'postgresImageId', 'postgresImageReference',
		'containerStartedAt', 'composeProject', 'composeService', 'configuredDatabase',
	];
	return identity && stringFields.every((field) => typeof identity[field] === 'string' && identity[field].length > 0)
		&& Number.isFinite(Date.parse(identity.containerStartedAt))
		&& Number.isSafeInteger(identity.postgresInternalPort) && identity.postgresInternalPort > 0
		&& Array.isArray(identity.containerNetworkAddresses) && identity.containerNetworkAddresses.length > 0
		&& identity.containerNetworkAddresses.every((address) => typeof address === 'string' && address.length > 0)
		&& Array.isArray(identity.networkIdentity) && identity.networkIdentity.length > 0
		&& identity.networkIdentity.every((network) => typeof network?.name === 'string' && network.name.length > 0
			&& typeof network.networkId === 'string' && network.networkId.length > 0
			&& Boolean(network.ipAddress || network.globalIPv6Address));
}

function canonicalValue(value) {
	return JSON.stringify(value ?? null);
}

export function validateMeasurementIntegrity(before, after, {
	expectedTables = [],
	expectedSettingNames = REQUIRED_PLANNER_SETTINGS,
} = {}) {
	const reasons = [];
	validateSnapshotEvidence(before, 'before', expectedTables, expectedSettingNames, reasons);
	validateSnapshotEvidence(after, 'after', expectedTables, expectedSettingNames, reasons);
	if (stableStringify(before.settings || {}) !== stableStringify(after.settings || {})) {
		reasons.push('planner-settings-changed');
	}
	if (before.postmasterStartedAt !== after.postmasterStartedAt) {
		reasons.push('postmaster-start-changed');
	}
	const beforeByTable = new Map((before.tableStatistics || []).map((row) => [row.table, row]));
	const afterByTable = new Map((after.tableStatistics || []).map((row) => [row.table, row]));
	if (beforeByTable.size !== afterByTable.size
		|| [...beforeByTable.keys()].some((table) => !afterByTable.has(table))) {
		reasons.push('observed-table-set-changed');
	}
	for (const [table, previous] of beforeByTable) {
		const current = afterByTable.get(table);
		if (!current) {
			continue;
		}
		if (previous.lastAnalyze !== current.lastAnalyze) {
			reasons.push(`last-analyze-changed:${table}`);
		}
		if (previous.lastAutoanalyze !== current.lastAutoanalyze) {
			reasons.push(`last-autoanalyze-changed:${table}`);
		}
		if (previous.nModSinceAnalyze !== current.nModSinceAnalyze) {
			reasons.push(`n-mod-since-analyze-changed:${table}`);
		}
		for (const [field, reason] of [
			['lastVacuum', 'last-vacuum-changed'],
			['lastAutovacuum', 'last-autovacuum-changed'],
			['vacuumCount', 'vacuum-count-changed'],
			['autovacuumCount', 'autovacuum-count-changed'],
			['allVisiblePages', 'all-visible-pages-changed'],
		]) {
			if (previous[field] !== current[field]) reasons.push(`${reason}:${table}`);
		}
	}
	if (activeSessionCount(before) > 0) {
		reasons.push('external-activity-present-before');
	}
	if (hasOtherDatabaseActivity(before)) reasons.push('other-database-activity-present-before');
	if (activeSessionCount(after) > 0) {
		reasons.push('external-activity-present-after');
	}
	if (hasOtherDatabaseActivity(after)) reasons.push('other-database-activity-present-after');
	return { adoptable: reasons.length === 0, reasons };
}

export function validateMeasurementStart(snapshot, {
	expectedTables = [],
	expectedSettingNames = REQUIRED_PLANNER_SETTINGS,
} = {}) {
	const reasons = [];
	validateSnapshotEvidence(snapshot, 'start', expectedTables, expectedSettingNames, reasons);
	if (activeSessionCount(snapshot) > 0) {
		reasons.push('external-activity-present-at-start');
	}
	if (hasOtherDatabaseActivity(snapshot)) reasons.push('other-database-activity-present-at-start');
	return { adoptable: reasons.length === 0, reasons };
}

export function decideMeasurementOutcome(integrity, productionBeforeEligibleQueryIds, productionBeforeEvidenceEnabled) {
	if (!integrity?.adoptable) {
		return {
			status: 'invalid-pending-planner-integrity',
			productionBeforeAdoptable: false,
			exitNonZero: true,
		};
	}
	const productionBeforeAdoptable = productionBeforeEvidenceEnabled === true
		&& Array.isArray(productionBeforeEligibleQueryIds)
		&& productionBeforeEligibleQueryIds.length > 0;
	return {
		status: productionBeforeAdoptable
			? 'measured-before-index-baseline'
			: 'measured-diagnostic-not-production-baseline',
		productionBeforeAdoptable,
		exitNonZero: false,
	};
}

function validateSnapshotEvidence(snapshot, phase, expectedTables, expectedSettingNames, reasons) {
	if (!snapshot || typeof snapshot !== 'object') {
		reasons.push(`${phase}-snapshot-missing`);
		return;
	}
	if (typeof snapshot.capturedAt !== 'string' || Number.isNaN(Date.parse(snapshot.capturedAt))) {
		reasons.push(`${phase}-captured-at-missing`);
	}
	if (typeof snapshot.serverVersion !== 'string' || snapshot.serverVersion.length === 0) {
		reasons.push(`${phase}-server-version-missing`);
	}
	if (typeof snapshot.postmasterStartedAt !== 'string' || Number.isNaN(Date.parse(snapshot.postmasterStartedAt))) {
		reasons.push(`${phase}-postmaster-start-missing`);
	}
	if (!snapshot.settings || typeof snapshot.settings !== 'object' || Array.isArray(snapshot.settings)
		|| expectedSettingNames.some((name) => !Object.hasOwn(snapshot.settings, name))) {
		reasons.push(`${phase}-planner-settings-incomplete`);
	}
	const rows = Array.isArray(snapshot.tableStatistics) ? snapshot.tableStatistics : [];
	const byTable = new Map(rows.map((row) => [row?.table, row]));
	if (expectedTables.some((table) => !byTable.has(table))) {
		reasons.push(`${phase}-table-statistics-incomplete`);
	}
	for (const table of expectedTables) {
		const row = byTable.get(table);
		if (!row) {
			continue;
		}
		if (!Object.hasOwn(row, 'lastAnalyze')
			|| !Object.hasOwn(row, 'lastAutoanalyze')
			|| !Object.hasOwn(row, 'lastVacuum')
			|| !Object.hasOwn(row, 'lastAutovacuum')
			|| typeof row.nModSinceAnalyze !== 'number'
			|| !Number.isFinite(row.nModSinceAnalyze)
			|| !Number.isFinite(row.vacuumCount)
			|| !Number.isFinite(row.autovacuumCount)
			|| !Number.isFinite(row.allVisiblePages)) {
			reasons.push(`${phase}-table-statistics-fields-incomplete:${table}`);
		}
	}
	if (typeof snapshot.externalActivity?.activeSessionCount !== 'number'
		|| !Number.isFinite(snapshot.externalActivity.activeSessionCount)
		|| !Array.isArray(snapshot.externalActivity.sessions)) {
		reasons.push(`${phase}-external-activity-incomplete`);
	}
}

function activeSessionCount(snapshot) {
	const value = snapshot.externalActivity?.activeSessionCount;
	return typeof value === 'number' && Number.isFinite(value) ? value : Number.POSITIVE_INFINITY;
}

function hasOtherDatabaseActivity(snapshot) {
	return typeof snapshot?.database === 'string'
		&& (snapshot.externalActivity?.sessions || []).some((session) => session.database !== snapshot.database);
}

function stableStringify(value) {
	if (Array.isArray(value)) {
		return `[${value.map(stableStringify).join(',')}]`;
	}
	if (value && typeof value === 'object') {
		return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
	}
	return JSON.stringify(value);
}
