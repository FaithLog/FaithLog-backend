const AVAILABLE_KEYS = ['available', 'statements'].sort();
const UNAVAILABLE_KEYS = ['available', 'reason', 'statements'].sort();
const STATEMENT_KEYS = ['calls', 'query', 'rows', 'sharedBlksHit', 'sharedBlksRead', 'totalExecTime'].sort();

export function validatePgStatStatementsSnapshot(snapshot, label, failures, { requireInventory = false } = {}) {
	const failureCount = failures.length;
	if (!snapshot || typeof snapshot !== 'object' || Array.isArray(snapshot)) {
		failures.push({ name: `${label}.schema`, expected: 'object', actual: snapshot });
		return invalidResult();
	}
	if (typeof snapshot.available !== 'boolean') {
		failures.push({ name: `${label}.available schema`, expected: 'boolean', actual: snapshot.available });
		return invalidResult();
	}
	const expectedKeys = snapshot.available ? AVAILABLE_KEYS : UNAVAILABLE_KEYS;
	const actualKeys = Object.keys(snapshot).sort();
	if (actualKeys.join(',') !== expectedKeys.join(',')) {
		failures.push({ name: `${label}.keys`, expected: expectedKeys, actual: actualKeys });
	}
	if (!Array.isArray(snapshot.statements)) {
		failures.push({ name: `${label}.statements schema`, expected: 'array', actual: snapshot.statements });
		return { valid: false, available: snapshot.available };
	}
	if (!snapshot.available) {
		if (typeof snapshot.reason !== 'string' || snapshot.reason.trim().length === 0) {
			failures.push({ name: `${label}.reason schema`, expected: 'non-empty string', actual: snapshot.reason });
		}
		if (snapshot.statements.length !== 0) {
			failures.push({ name: `${label}.statements`, expected: [], actual: snapshot.statements });
		}
		return { valid: failures.length === failureCount, available: false };
	}
	if (requireInventory && snapshot.statements.length === 0) {
		failures.push({ name: `${label}.statement inventory`, expected: 'at least one normalized query', actual: [] });
	}
	for (const [index, statement] of snapshot.statements.entries()) {
		validateStatement(statement, `${label}.statements[${index}]`, failures);
	}
	return { valid: failures.length === failureCount, available: true };
}

function validateStatement(statement, label, failures) {
	if (!statement || typeof statement !== 'object' || Array.isArray(statement)) {
		failures.push({ name: `${label}.schema`, expected: 'object', actual: statement });
		return;
	}
	const actualKeys = Object.keys(statement).sort();
	if (actualKeys.join(',') !== STATEMENT_KEYS.join(',')) {
		failures.push({ name: `${label}.keys`, expected: STATEMENT_KEYS, actual: actualKeys });
	}
	if (typeof statement.query !== 'string' || statement.query.length === 0) {
		failures.push({ name: `${label}.query`, expected: 'non-empty string', actual: statement.query });
	}
	for (const field of ['calls', 'rows', 'sharedBlksHit', 'sharedBlksRead']) {
		if (typeof statement[field] !== 'string' || !/^(0|[1-9][0-9]*)$/.test(statement[field])) {
			failures.push({ name: `${label}.${field}`, expected: 'lossless non-negative decimal string', actual: statement[field] });
		}
	}
	if (typeof statement.totalExecTime !== 'number' || !Number.isFinite(statement.totalExecTime) || statement.totalExecTime < 0) {
		failures.push({ name: `${label}.totalExecTime`, expected: 'finite non-negative number', actual: statement.totalExecTime });
	}
}

function invalidResult() {
	return { valid: false, available: null };
}
