import assert from 'node:assert/strict';
import {execFileSync} from 'node:child_process';
import {createHash} from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import {fileURLToPath} from 'node:url';

const contractPath = process.argv[2];
try {
	assert.ok(contractPath, 'Current-develop contract path is required.');
	const contract = JSON.parse(fs.readFileSync(path.resolve(contractPath), 'utf8'));
	const repositoryRoot = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../../..');
	assert.equal(contract.issue, 199);
	assert.equal(contract.baseCommit, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
	const readApprovedSource = (relativePath) => execFileSync(
		'git',
		['show', `${contract.baseCommit}:${relativePath}`],
		{cwd: repositoryRoot},
	);
	for (const [relativePath, expected] of Object.entries({...contract.sourceFiles, ...Object.fromEntries(
		Object.entries(contract.flywayMigrations).map(([name, hash]) => [`src/main/resources/db/migration/${name}`, hash]),
	)})) {
		const actual = createHash('sha256').update(readApprovedSource(relativePath)).digest('hex');
		assert.equal(actual, expected, `${relativePath} identity drifted from approved current develop.`);
	}
	const controller = readApprovedSource(Object.keys(contract.sourceFiles)[0]).toString('utf8');
	const service = readApprovedSource('src/main/java/com/faithlog/admin/service/AdminDashboardQueryService.java').toString('utf8');
	assert.match(controller, /@RequestParam\(required = false\) LocalDate weekStartDate/);
	assert.doesNotMatch(controller, /Pageable|PageResponse|includeArchived/);
	assert.match(service, /MINISTER|ELDER|CAMPUS_LEADER/);
	assert.match(service, /UNPAID/);
	assert.doesNotMatch(service, /Pageable|PageResponse|includeArchived/);
	process.stdout.write(`${JSON.stringify({status: 'current-develop-contract-verified', automaticAdoption: false})}\n`);
} catch (error) {
	process.stdout.write(`${JSON.stringify({status: 'rejected', automaticAdoption: false, failures: [{name: 'currentDevelopContract', actual: error.message}]})}\n`);
	process.stderr.write(`${error.message}\n`);
	process.exitCode = 1;
}
