import assert from 'node:assert/strict';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

import { validateNumericLoopbackHost, validatePublishedTarget } from '../lib/runtime-contract.mjs';

const ISSUE_DIR = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const REPOSITORY_ROOT = path.resolve(ISSUE_DIR, '..', '..', '..');

const readIssueFile = (relativePath) => fs.readFileSync(path.join(ISSUE_DIR, relativePath), 'utf8');
const readRepositoryFile = (relativePath) => fs.readFileSync(path.join(REPOSITORY_ROOT, relativePath), 'utf8');

test('current develop keeps one-month archive visibility separate from annual terminal retention', async () => {
	const contractPath = path.join(ISSUE_DIR, 'lib/current-develop-contract.mjs');
	assert.equal(fs.existsSync(contractPath), true, 'current-develop fake correctness contract must exist');
	const { isDefaultArchiveVisible, isAnnualTerminalRetentionCandidate } = await import(pathToFileURL(contractPath));
	const cutoff = '2026-06-16T00:00:00.000Z';
	const previousYear = { startInclusive: '2025-01-01T00:00:00.000Z', endExclusive: '2026-01-01T00:00:00.000Z' };

	const oldPaidRecently = {
		status: 'PAID', createdAt: '2025-03-01T00:00:00.000Z', paidAt: '2026-07-01T00:00:00.000Z', updatedAt: '2026-07-01T00:00:00.000Z',
	};
	assert.equal(isDefaultArchiveVisible(oldPaidRecently, cutoff), true);
	assert.equal(isAnnualTerminalRetentionCandidate(oldPaidRecently, previousYear), true);

	const oldPaidLongAgo = { ...oldPaidRecently, paidAt: '2026-05-01T00:00:00.000Z', updatedAt: '2026-05-01T00:00:00.000Z' };
	assert.equal(isDefaultArchiveVisible(oldPaidLongAgo, cutoff), false);
	assert.equal(isAnnualTerminalRetentionCandidate(oldPaidLongAgo, previousYear), true);

	const recentPaid = { ...oldPaidRecently, createdAt: '2026-07-01T00:00:00.000Z' };
	assert.equal(isDefaultArchiveVisible(recentPaid, cutoff), true);
	assert.equal(isAnnualTerminalRetentionCandidate(recentPaid, previousYear), false);
});

test('stale duty and soft-deleted-account UNPAID rows never become annual retention candidates', async () => {
	const contractPath = path.join(ISSUE_DIR, 'lib/current-develop-contract.mjs');
	assert.equal(fs.existsSync(contractPath), true, 'current-develop fake correctness contract must exist');
	const { isDefaultArchiveVisible, isAnnualTerminalRetentionCandidate } = await import(pathToFileURL(contractPath));
	const staleUnpaid = {
		status: 'UNPAID',
		createdAt: '2025-02-01T00:00:00.000Z',
		paidAt: null,
		updatedAt: '2025-02-01T00:00:00.000Z',
		staleDuty: true,
		paymentAccountDeletedAt: '2026-01-01T00:00:00.000Z',
	};
	assert.equal(isDefaultArchiveVisible(staleUnpaid, '2026-06-16T00:00:00.000Z'), true);
	assert.equal(isAnnualTerminalRetentionCandidate(staleUnpaid, {
		startInclusive: '2025-01-01T00:00:00.000Z', endExclusive: '2026-01-01T00:00:00.000Z',
	}), false);
});

test('retention SQL uses annual created_at terminal candidates without one-month visibility or account state coupling', () => {
	const sql = readIssueFile('retention-dry-verify.sql');
	assert.match(sql, /charge\.created_at\s*>=\s*annual\.start_date\s+AT TIME ZONE 'Asia\/Seoul'/i);
	assert.match(sql, /charge\.status\s+IN\s*\(\s*'PAID',\s*'WAIVED',\s*'CANCELED'\s*\)/i);
	assert.doesNotMatch(sql, /paid_at|updated_at|interval\s+'1 month'|includeArchived/i);
	assert.doesNotMatch(sql, /'UNPAID'/i);
	assert.doesNotMatch(sql, /payment_accounts|campus_duty_assignments/i);
});

test('BASE_URL accepts only a numeric loopback target discovered from the published port', () => {
	assert.deepEqual(validatePublishedTarget('http://127.0.0.1:28080/', '28080'), { host: '127.0.0.1', port: 28080 });
	assert.equal(validateNumericLoopbackHost('127.0.0.1', 'DB_HOST'), '127.0.0.1');
	assert.equal(validateNumericLoopbackHost('::1', 'REDIS_HOST'), '::1');
	assert.throws(
		() => validatePublishedTarget('http://localhost:28080/', '28080'),
		/numeric loopback/i,
	);
	assert.throws(() => validateNumericLoopbackHost('localhost', 'DB_HOST'), /numeric loopback/i);
});

test('both runners require and continuously attest exact app revision, image, binary, and API contract identity', () => {
	const devotionRunner = readIssueFile('run-devotion-baseline.sh');
	const retentionRunner = readIssueFile('run-retention-dry-verify.sh');
	const identityValidator = readIssueFile('lib/validate-runtime-identity.mjs');
	for (const source of [devotionRunner, retentionRunner]) {
		for (const variable of ['EXPECTED_APP_REVISION', 'EXPECTED_APP_IMAGE_ID', 'EXPECTED_APP_JAR_SHA256', 'EXPECTED_API_CONTRACT_SHA256']) {
			assert.match(source, new RegExp(`\\b${variable}\\b`));
		}
		assert.match(source, /require_env "\$name"/);
		assert.match(source, /org\.opencontainers\.image\.revision/);
		assert.match(source, /org\.opencontainers\.image\.api-contract-sha256/);
		assert.match(source, /sha256sum \/app\/app\.jar/);
	}
	for (const field of ['revision', 'jarSha256', 'apiContractSha256']) {
		assert.match(identityValidator, new RegExp(field));
	}
});

test('current develop drift anchors #202 direct-JDBC independence and #206 stable pagination', () => {
	const v11 = readRepositoryFile('src/main/resources/db/migration/V11__secure_supabase_data_api.sql');
	const billingPageRequests = readRepositoryFile('src/main/java/com/faithlog/billing/controller/BillingPageRequests.java');
	assert.match(v11, /ENABLE ROW LEVEL SECURITY/);
	assert.doesNotMatch(v11, /FORCE ROW LEVEL SECURITY/);
	assert.match(billingPageRequests, /primary\.getSort\(\)\.and\(Sort\.by\(primaryOrder\.getDirection\(\), "id"\)\)/);
	for (const runner of [readIssueFile('run-devotion-baseline.sh'), readIssueFile('run-retention-dry-verify.sh')]) {
		assert.doesNotMatch(runner, /anon|authenticated|service_role|postgrest|supabase[^-]/i);
		assert.match(runner, /docker exec .*psql/);
	}
});

test('README records scenario-ready/not-measured and the two-session one-load policy', () => {
	const readme = readIssueFile('README.md');
	assert.match(readme, /scenario-ready/i);
	assert.match(readme, /not-measured/i);
	assert.match(readme, /two-session/i);
	assert.match(readme, /one-load/i);
});
