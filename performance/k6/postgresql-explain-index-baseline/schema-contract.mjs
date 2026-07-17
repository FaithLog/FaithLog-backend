import { createHash } from 'node:crypto';

export function normalizeSchemaState(state) {
	const normalized = {
		flyway: sortRows(state?.flyway, ['installedRank', 'version']),
		columns: sortRows(state?.columns, ['table', 'ordinalPosition', 'name']),
		constraints: sortRows(state?.constraints, ['table', 'name']),
		indexes: sortRows(state?.indexes, ['table', 'name']),
		databaseRole: structuredClone(state?.databaseRole ?? null),
		tableSecurity: sortRows(state?.tableSecurity, ['table']),
	};
	const canonical = stableStringify(normalized);
	const securityCanonical = stableStringify({
		databaseRole: normalized.databaseRole,
		tableSecurity: normalized.tableSecurity,
	});
	return {
		...normalized,
		fingerprint: createHash('sha256').update(canonical).digest('hex'),
		indexFingerprint: createHash('sha256').update(stableStringify(normalized.indexes)).digest('hex'),
		securityFingerprint: createHash('sha256').update(securityCanonical).digest('hex'),
	};
}

export function validateSchemaContinuity(before, after) {
	const reasons = [];
	if (!before?.fingerprint || !after?.fingerprint) reasons.push('schema-index-fingerprint-missing');
	else if (before.fingerprint !== after.fingerprint) reasons.push('schema-index-fingerprint-changed');
	if (before?.indexFingerprint !== after?.indexFingerprint) reasons.push('index-fingerprint-changed');
	if (before?.securityFingerprint !== after?.securityFingerprint) reasons.push('database-security-fingerprint-changed');
	if ((before?.flyway || []).some((row) => row.success !== true)
		|| (after?.flyway || []).some((row) => row.success !== true)) reasons.push('flyway-history-unsuccessful');
	return { stable: reasons.length === 0, reasons };
}

export function validateSchemaSnapshot(state, expectedTables = [], expected = {}) {
	const reasons = [];
	if (!Array.isArray(state?.flyway) || state.flyway.length === 0) reasons.push('flyway-history-missing');
	else if (state.flyway.some((row) => row.success !== true)) reasons.push('flyway-history-unsuccessful');
	for (const group of ['columns', 'constraints', 'indexes']) {
		const tables = new Set((state?.[group] || []).map((row) => row.table));
		for (const table of expectedTables) {
			if (!tables.has(table)) reasons.push(`schema-${group}-missing:${table}`);
		}
	}
	if (!/^[a-f0-9]{64}$/.test(state?.fingerprint ?? '')) reasons.push('schema-index-fingerprint-missing');
	if (!/^[a-f0-9]{64}$/.test(state?.indexFingerprint ?? '')) reasons.push('index-fingerprint-missing');
	if (!/^[a-f0-9]{64}$/.test(state?.securityFingerprint ?? '')) reasons.push('database-security-fingerprint-missing');
	validateFlywayContract(state, expected, reasons);
	validateIndexContract(state, expected, reasons);
	validateDatabaseSecurity(state, expectedTables, expected, reasons);
	return { adoptable: reasons.length === 0, reasons };
}

function validateFlywayContract(state, expected, reasons) {
	if (!expected.flywayCurrentVersion) return;
	const successful = (state?.flyway || []).filter((row) => row.success === true);
	const current = successful.at(-1)?.version;
	if (String(current ?? '') !== String(expected.flywayCurrentVersion)) {
		reasons.push(`flyway-current-version-mismatch:expected-${expected.flywayCurrentVersion}`);
	}
}

function validateIndexContract(state, expected, reasons) {
	const names = new Set((state?.indexes || []).map((row) => row.name));
	for (const name of expected.requiredIndexes || []) {
		if (!names.has(name)) reasons.push(`required-index-missing:${name}`);
	}
	for (const name of expected.forbiddenIndexes || []) {
		if (names.has(name)) reasons.push(`forbidden-index-present:${name}`);
	}
}

function validateDatabaseSecurity(state, expectedTables, expected, reasons) {
	const securityRequired = [
		'requireAllPublicApplicationTablesRls', 'requireRlsNotForced', 'requireNoRlsPolicies',
		'requireNoDataApiOrPublicExposure', 'requireEffectiveJdbcSelect',
	].some((field) => expected[field] === true);
	if (!securityRequired) return;
	const role = state?.databaseRole;
	const rows = Array.isArray(state?.tableSecurity) ? state.tableSecurity : [];
	if (!role || rows.length === 0) {
		reasons.push('database-security-evidence-missing');
		return;
	}
	const tables = new Set(rows.map((row) => row.table));
	for (const table of expectedTables) {
		if (!tables.has(table)) reasons.push(`table-security-missing:${table}`);
	}
	if (expected.requireAllPublicApplicationTablesRls) {
		for (const row of rows) {
			if (row.rowSecurityEnabled !== true) reasons.push(`rls-disabled:${row.table}`);
		}
	}
	if (expected.requireRlsNotForced) {
		for (const row of rows) {
			if (row.rowSecurityForced !== false) reasons.push(`rls-forced-or-unknown:${row.table}`);
		}
	}
	if (expected.requireNoRlsPolicies) {
		for (const row of rows) {
			if (row.policyCount !== 0) reasons.push(`rls-policy-present:${row.table}`);
		}
	}
	if (expected.requireNoDataApiOrPublicExposure
		&& (role.publicExposureCount !== 0 || role.dataApiExposureCount !== 0
			|| role.exposedDefaultAclCount !== 0)) {
		reasons.push('data-api-or-public-privilege-exposure');
	}
	if (expected.requireEffectiveJdbcSelect) {
		if (role.schemaUsage !== true || role.rowSecuritySetting !== 'on') {
			reasons.push('jdbc-role-schema-or-row-security-setting-invalid');
		}
		for (const row of rows) {
			const bypassesPolicies = role.superuser === true || role.bypassRls === true
				|| (row.currentUserOwnsTable === true && row.rowSecurityForced === false);
			if (row.currentUserHasSelect !== true || !bypassesPolicies) {
				reasons.push(`jdbc-select-not-effective:${row.table}`);
			}
		}
	}
}

function sortRows(rows, keys) {
	return structuredClone(Array.isArray(rows) ? rows : []).sort((a, b) => {
		for (const key of keys) {
			const compared = String(a?.[key] ?? '').localeCompare(String(b?.[key] ?? ''), 'en');
			if (compared !== 0) return compared;
		}
		return stableStringify(a).localeCompare(stableStringify(b), 'en');
	});
}

function stableStringify(value) {
	if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
	if (value && typeof value === 'object') {
		return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
	}
	return JSON.stringify(value);
}
