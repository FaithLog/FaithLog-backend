import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import { readFileSync, readdirSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { test } from 'node:test';

const REPOSITORY_ROOT = new URL('../../', import.meta.url);
const SCENARIO_ROOT = new URL('./notification-batch/', import.meta.url);
const CURRENT_DEVELOP_BASE = '6796ed146244d8f3f5b5dd7048ebe16865084a97';

function readRepositoryFile(path) {
	return readFileSync(new URL(path, REPOSITORY_ROOT), 'utf8');
}

function readScenarioFile(path) {
	return readFileSync(new URL(path, SCENARIO_ROOT), 'utf8');
}

function sha256(path) {
	return createHash('sha256').update(readRepositoryFile(path)).digest('hex');
}

test('Issue #198 scenario pins the current develop Flyway and notification runtime identity', () => {
	const contract = JSON.parse(readScenarioFile('current-develop-contract.json'));
	const migrationDirectory = new URL('../../src/main/resources/db/migration/', import.meta.url);
	const migrationNames = readdirSync(fileURLToPath(migrationDirectory)).sort();

	assert.equal(contract.issue, 198);
	assert.equal(contract.baseCommit, CURRENT_DEVELOP_BASE);
	assert.deepEqual(Object.keys(contract.flywayMigrations).sort(), migrationNames);
	for (const [name, expectedHash] of Object.entries(contract.flywayMigrations)) {
		assert.equal(sha256(`src/main/resources/db/migration/${name}`), expectedHash, `${name} identity drifted`);
	}
	assert.deepEqual(contract.notificationFlow, {
		creationTokenLookup: 'per-target-user',
		deliveryTokenSnapshot: 'request-wide-bulk',
		permanentFailurePolicy: 'deactivate-and-remove-from-later-request-logs',
		staleTokenCutoffDays: 90,
		requestLogOrdering: 'id-asc',
	});
	assert.deepEqual(contract.rlsJdbcBoundary, {
		dataApiRoles: 'deny-all',
		applicationPath: 'direct-owner-jdbc',
		forceRowLevelSecurity: false,
	});
});

test('current production sources still match the characterized #200 notification flow', () => {
	const requestService = readRepositoryFile(
		'src/main/java/com/faithlog/notification/service/NotificationRequestCommandService.java',
	);
	const deliveryWorker = readRepositoryFile(
		'src/main/java/com/faithlog/notification/service/NotificationDeliveryWorker.java',
	);
	const tokenRepository = readRepositoryFile(
		'src/main/java/com/faithlog/notification/infrastructure/repository/UserFcmTokenRepository.java',
	);
	const reminderService = readRepositoryFile(
		'src/main/java/com/faithlog/notification/service/ChargeReminderService.java',
	);
	const rlsMigration = readRepositoryFile(
		'src/main/resources/db/migration/V11__secure_supabase_data_api.sql',
	);

	assert.match(requestService, /requestAutomaticNotification/);
	assert.match(requestService, /findActiveSendableTokens\(targetUserId\)/);
	assert.match(deliveryWorker, /findActiveSendableTokensByUserIdIn\(pendingUserIds\)/);
	assert.match(deliveryWorker, /findByRequestIdAndSendStatusOrderByIdAsc/);
	assert.match(deliveryWorker, /if \(permanent\)[\s\S]*iterator\.remove\(\)/);
	assert.match(tokenRepository, /Duration\.ofDays\(90\)/);
	assert.match(tokenRepository, /order by token\.id asc/);
	assert.match(reminderService, /requestCoffeeReminders/);
	assert.match(reminderService, /requestMealReminders/);
	assert.match(reminderService, /reserveDailyRequiredNotification/);
	assert.match(reminderService, /account:/);
	assert.match(rlsMigration, /ENABLE ROW LEVEL SECURITY/i);
	assert.doesNotMatch(rlsMigration, /FORCE ROW LEVEL SECURITY/i);
});

test('runner fails closed on source or migration drift before fixture or workload execution', () => {
	const runner = readScenarioFile('run-before.sh');
	const fixturePreparation = readScenarioFile('prepare-fixtures.sh');
	const contractVerifier = readScenarioFile('verify-current-develop-contract.mjs');
	const identityCapture = readScenarioFile('capture-runtime-identity.sh');
	const continuityVerifier = readScenarioFile('assert-runtime-continuity.mjs');

	for (const script of [runner, fixturePreparation]) {
		assert.match(script, /verify-current-develop-contract\.mjs/);
		assert.match(script, /CURRENT_DEVELOP_CONTRACT_PATH/);
		assert.doesNotMatch(script, /\$\{CURRENT_DEVELOP_CONTRACT_PATH:-/,
			'measurement entrypoints must not accept an override contract path');
		assert.match(script, /CURRENT_DEVELOP_CONTRACT_PATH="\$\{SCRIPT_DIR\}\/current-develop-contract\.json"/);
	}
	assert.match(runner, /PERF_EXPECTED_POSTGRES_ROLE/);
	assert.match(contractVerifier, /baseCommit/);
	assert.match(contractVerifier, /flywayMigrations/);
	assert.match(contractVerifier, /NotificationDeliveryWorker/);
	assert.match(contractVerifier, /ChargeReminderService/);
	assert.match(contractVerifier, /V11__secure_supabase_data_api/);
	assert.match(identityCapture, /PERF_EXPECTED_POSTGRES_ROLE/);
	assert.match(identityCapture, /currentUser/);
	assert.match(continuityVerifier, /currentUser/);
});

test('scenario docs delimit #200, pagination/archive, RLS, and stable-ordering relevance', () => {
	const readme = readScenarioFile('README.md');

	assert.match(readme, /#200/);
	assert.match(readme, /request-wide bulk token snapshot/i);
	assert.match(readme, /stale duty/i);
	assert.match(readme, /pagination|archive/i);
	assert.match(readme, /#202/);
	assert.match(readme, /direct owner JDBC/i);
	assert.match(readme, /#206/);
	assert.match(readme, /stable ordering/i);
	assert.match(readme, /parallel test-code/i);
	assert.match(readme, /sequential actual-load/i);
	assert.match(readme, /scenario-ready/i);
	assert.match(readme, /not-measured/i);
});
