import { createHash } from 'node:crypto';

export function normalizeSchemaState(state) {
	const normalized = {
		flyway: sortRows(state?.flyway, ['installedRank', 'version']),
		columns: sortRows(state?.columns, ['table', 'ordinalPosition', 'name']),
		constraints: sortRows(state?.constraints, ['table', 'name']),
		indexes: sortRows(state?.indexes, ['table', 'name']),
	};
	const canonical = stableStringify(normalized);
	return {
		...normalized,
		fingerprint: createHash('sha256').update(canonical).digest('hex'),
		indexFingerprint: createHash('sha256').update(stableStringify(normalized.indexes)).digest('hex'),
	};
}

export function validateSchemaContinuity(before, after) {
	const reasons = [];
	if (!before?.fingerprint || !after?.fingerprint) reasons.push('schema-index-fingerprint-missing');
	else if (before.fingerprint !== after.fingerprint) reasons.push('schema-index-fingerprint-changed');
	if (before?.indexFingerprint !== after?.indexFingerprint) reasons.push('index-fingerprint-changed');
	if ((before?.flyway || []).some((row) => row.success !== true)
		|| (after?.flyway || []).some((row) => row.success !== true)) reasons.push('flyway-history-unsuccessful');
	return { stable: reasons.length === 0, reasons };
}

export function validateSchemaSnapshot(state, expectedTables = []) {
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
	return { adoptable: reasons.length === 0, reasons };
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
