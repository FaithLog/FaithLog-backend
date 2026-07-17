const evidenceClasses = new Set([
	'exact-current-production',
	'reconstructed-current-query',
	'synthetic-candidate',
]);

export function validateEvidenceInventory(inventory, {
	productionBeforeEvidenceEnabled = false,
	sourceRoot,
} = {}) {
	if (!inventory || !Array.isArray(inventory.queries)) {
		throw new Error('Inventory queries are required.');
	}
	if (typeof sourceRoot !== 'string' || sourceRoot.length === 0) {
		throw new Error('Evidence validation requires a repository sourceRoot.');
	}
	for (const query of inventory.queries) {
		if (!evidenceClasses.has(query.evidenceClass)) {
			throw new Error(`${query.id} has an invalid evidenceClass.`);
		}
		if (!Array.isArray(query.productionSourceRefs) || query.productionSourceRefs.length === 0) {
			throw new Error(`${query.id} must link productionSourceRefs.`);
		}
		for (const sourceRef of query.productionSourceRefs) {
			if (typeof sourceRef !== 'string' || !fs.existsSync(path.join(sourceRoot, sourceRef))) {
				throw new Error(`${query.id} has a missing production source reference: ${sourceRef}`);
			}
		}
		if (typeof query.productionBeforeEligible !== 'boolean') {
			throw new Error(`${query.id} must declare productionBeforeEligible.`);
		}
		if (query.productionBeforeEligible && query.evidenceClass !== 'exact-current-production') {
			throw new Error(`${query.id} cannot use reconstructed or synthetic SQL as production before evidence.`);
		}
		if (query.productionBeforeEligible && productionBeforeEvidenceEnabled !== true) {
			throw new Error(`${query.id} production before evidence is not approved by the current report contract.`);
		}
		if (query.evidenceClass === 'exact-current-production') {
			if (!query.productionSqlIdentity
				|| query.productionSqlIdentity.captureType !== 'hibernate-sql'
				|| typeof query.productionSqlIdentity.fingerprint !== 'string'
				|| !/^[a-f0-9]{64}$/i.test(query.productionSqlIdentity.fingerprint)) {
				throw new Error(`${query.id} exact production SQL requires a SHA-256 captured Hibernate SQL fingerprint.`);
			}
		}
	}
}

export function groupEvidenceQueries(queries) {
	const groups = {
		exactCurrentProduction: [],
		reconstructedCurrentQuery: [],
		syntheticCandidate: [],
	};
	for (const query of queries) {
		if (query.evidenceClass === 'exact-current-production') {
			groups.exactCurrentProduction.push(query.id);
		} else if (query.evidenceClass === 'reconstructed-current-query') {
			groups.reconstructedCurrentQuery.push(query.id);
		} else if (query.evidenceClass === 'synthetic-candidate') {
			groups.syntheticCandidate.push(query.id);
		}
	}
	return groups;
}
import fs from 'node:fs';
import path from 'node:path';
