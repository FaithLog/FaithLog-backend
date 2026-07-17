import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export function validateRejectionEvidencePath(filePath) {
	assert.equal(typeof filePath, 'string', 'REJECTION_EVIDENCE_FILE must be a string');
	const resolved = path.resolve(filePath);
	const reportSegment = `${path.sep}build${path.sep}reports${path.sep}k6${path.sep}issue-197${path.sep}`;
	assert.ok(resolved.includes(reportSegment) || resolved.startsWith(`${os.tmpdir()}${path.sep}`),
		'REJECTION_EVIDENCE_FILE must be under build/reports/k6/issue-197 or the OS temp directory');
	return resolved;
}

export function writeFirstRejection(filePath, rejection, rejectedAt = new Date().toISOString()) {
	const resolved = validateRejectionEvidencePath(filePath);
	assert.ok(['devotion', 'retention'].includes(rejection.scenario), 'rejection scenario must be devotion or retention');
	assert.equal(typeof rejection.stage, 'string', 'rejection stage must be a string');
	assert.ok(rejection.stage.length > 0, 'rejection stage must not be empty');
	assert.ok(Number.isInteger(rejection.exitCode) && rejection.exitCode > 0, 'rejection exitCode must be a positive integer');
	assert.equal(new Date(rejectedAt).toISOString(), rejectedAt, 'rejectedAt must be a canonical ISO timestamp');
	const evidence = {
		schemaVersion: 1,
		scenario: rejection.scenario,
		status: 'rejected',
		automaticAdoption: false,
		stage: rejection.stage,
		exitCode: rejection.exitCode,
		rejectedAt,
	};
	fs.mkdirSync(path.dirname(resolved), { recursive: true });
	try {
		fs.writeFileSync(resolved, `${JSON.stringify(evidence, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
		return evidence;
	} catch (error) {
		if (error.code === 'EEXIST') return JSON.parse(fs.readFileSync(resolved, 'utf8'));
		throw error;
	}
}

export function prepareRejectionEvidence(filePath) {
	const resolved = validateRejectionEvidencePath(filePath);
	assert.equal(fs.existsSync(resolved), false, 'REJECTION_EVIDENCE_FILE must be fresh for this fixture run');
	return resolved;
}

async function main() {
	const [command, filePath, scenario, stage, exitCode] = process.argv.slice(2);
	if (command === 'prepare') {
		process.stdout.write(`${prepareRejectionEvidence(filePath)}\n`);
		return;
	}
	if (command === 'write-first') {
		process.stdout.write(`${JSON.stringify(writeFirstRejection(filePath, { scenario, stage, exitCode: Number(exitCode) }))}\n`);
		return;
	}
	throw new Error('unsupported rejection-contract command');
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
