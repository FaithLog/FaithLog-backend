import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { execFileSync } from 'node:child_process';
import { readFileSync, readdirSync } from 'node:fs';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const scriptDirectory = dirname(fileURLToPath(import.meta.url));
const repositoryRoot = resolve(scriptDirectory, '../../..');
const contractPath = resolve(
	process.env.CURRENT_DEVELOP_CONTRACT_PATH
		?? join(scriptDirectory, 'current-develop-contract.json'),
);
const contract = JSON.parse(readFileSync(contractPath, 'utf8'));

function readRepositoryFile(path) {
	return readFileSync(join(repositoryRoot, path), 'utf8');
}

function sha256(path) {
	return createHash('sha256').update(readRepositoryFile(path)).digest('hex');
}

assert.equal(contract.issue, 198);
assert.match(contract.baseCommit, /^[0-9a-f]{40}$/);
execFileSync('git', ['merge-base', '--is-ancestor', contract.baseCommit, 'HEAD'], {
	cwd: repositoryRoot,
	stdio: 'ignore',
});

const migrationDirectory = join(repositoryRoot, 'src/main/resources/db/migration');
const migrationNames = readdirSync(migrationDirectory).filter((name) => name.endsWith('.sql')).sort();
assert.deepEqual(Object.keys(contract.flywayMigrations).sort(), migrationNames);
for (const [name, expectedHash] of Object.entries(contract.flywayMigrations)) {
	assert.equal(sha256(`src/main/resources/db/migration/${name}`), expectedHash, `${name} identity drifted`);
}
for (const [path, expectedHash] of Object.entries(contract.productionSources)) {
	assert.equal(sha256(path), expectedHash, `${path} identity drifted`);
}

const NotificationDeliveryWorker = readRepositoryFile(
	'src/main/java/com/faithlog/notification/service/NotificationDeliveryWorker.java',
);
const ChargeReminderService = readRepositoryFile(
	'src/main/java/com/faithlog/notification/service/ChargeReminderService.java',
);
const V11__secure_supabase_data_api = readRepositoryFile(
	'src/main/resources/db/migration/V11__secure_supabase_data_api.sql',
);
assert.match(NotificationDeliveryWorker, /findActiveSendableTokensByUserIdIn\(pendingUserIds\)/);
assert.match(NotificationDeliveryWorker, /if \(permanent\)[\s\S]*iterator\.remove\(\)/);
assert.match(ChargeReminderService, /reserveDailyRequiredNotification/);
assert.match(ChargeReminderService, /account:/);
assert.match(V11__secure_supabase_data_api, /ENABLE ROW LEVEL SECURITY/i);
assert.doesNotMatch(V11__secure_supabase_data_api, /FORCE ROW LEVEL SECURITY/i);

process.stdout.write(`${JSON.stringify({
	status: 'verified',
	baseCommit: contract.baseCommit,
	flywayMigrations: migrationNames.length,
	productionSources: Object.keys(contract.productionSources).length,
})}\n`);
