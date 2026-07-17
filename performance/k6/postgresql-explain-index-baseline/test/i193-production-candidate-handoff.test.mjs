import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const testRoot = path.dirname(fileURLToPath(import.meta.url));
const scenarioRoot = path.resolve(testRoot, '..');
const readJson = (file) => JSON.parse(fs.readFileSync(path.join(scenarioRoot, file), 'utf8'));
const sha256 = (file) => createHash('sha256')
	.update(fs.readFileSync(path.join(scenarioRoot, file)))
	.digest('hex');

const expectedCaseNames = [
	'my_initial_penalty_unpaid',
	'my_payment_category',
	'my_status',
	'my_user_id',
	'my_keyword',
	'my_payment_account_unknown_param_ignored',
	'my_pagination_page_0',
	'my_pagination_page_1',
	'admin_initial_penalty_unpaid',
	'admin_payment_category',
	'admin_status',
	'admin_user_id',
	'admin_keyword',
	'admin_payment_account',
	'admin_pagination_page_0',
	'admin_pagination_page_1',
];

test('#193 finding-zero production branch is an immutable exact-candidate handoff, never current production evidence', () => {
	const inventory = readJson('inventory.json');
	const handoff = readJson('i193-production-candidate-handoff.json');
	const inventoryHandoff = inventory.productionCandidateHandoffs?.find(({ issueNumber }) => issueNumber === 193);

	assert.deepEqual(inventoryHandoff, {
		issueNumber: 193,
		manifestFile: 'i193-production-candidate-handoff.json',
		status: 'exact-production-branch-candidate-not-measured',
		productionBeforeEligible: false,
	});
	assert.equal(handoff.sourceRevision, '01faf929c24746e3a300e40d11171fc7d473954d');
	assert.equal(handoff.sourceBaseRevision, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
	assert.equal(handoff.sourceReviewStatus, 'finding-zero');
	assert.equal(handoff.status, 'exact-production-branch-candidate-not-measured');
	assert.equal(handoff.automaticAdoption, false);
	assert.equal(handoff.productionBeforeEligible, false);
	assert.equal(handoff.requiresIntegrationAndRuntimeSqlCapture, true);
	assert.equal(handoff.requiresUserIndexFlywayApproval, true);
	assert.deepEqual(handoff.sourceHashes, {
		'src/main/java/com/faithlog/billing/infrastructure/repository/AdminChargeAggregationQueryRepository.java':
			'8235f4c3837a45df87e04e929048e49236cd19886fd7410856dfcc6e9d340aeb',
		'src/main/java/com/faithlog/billing/service/AdminChargeQueryService.java':
			'eacb66d9bc4c893d0297f4651e6e3489a873bbd32ba3908e5e6a76f97721f1bb',
		'src/main/java/com/faithlog/billing/service/query/AdminChargeAggregationCriteria.java':
			'd9e6b0cf21b03fc6a83c36210a06c755fa4b04a5d5aba211d34c46e52f79a564',
		'src/main/java/com/faithlog/billing/service/port/AdminChargeAggregationQueryPort.java':
			'b9bc5b083e0569622a328968403c4d53f8b79b53847dd7cfaf495251762c3c86',
	});
	assert.equal(handoff.sqlBuilder.sha256, sha256(handoff.sqlBuilder.file));
});

test('source-derived builder preserves the exact summary, member-page, and count-distinct query topology', async () => {
	const builderUrl = pathToFileURL(path.join(scenarioRoot, 'i193-production-candidate-sql.mjs')).href;
	const { buildAdminChargeAggregationSql } = await import(builderUrl);
	const criteria = {
		campusId: 1,
		userId: 2,
		keyword: 'member',
		paymentCategory: 'COFFEE',
		excludedPaymentCategory: 'MEAL',
		status: 'UNPAID',
		paymentAccountIds: [3],
		terminalCompletedAtFrom: '2026-06-16T00:00:00+09:00',
	};
	const shapes = buildAdminChargeAggregationSql(criteria, { size: 10, offset: 0, sort: 'createdAt,desc' });

	assert.deepEqual(Object.keys(shapes), ['summary', 'memberPage', 'countDistinct']);
	for (const sql of Object.values(shapes)) {
		assert.match(sql, /from charge_items charge/i);
		assert.match(sql, /join campus_members member[\s\S]*member\.status = 'ACTIVE'/i);
		assert.match(sql, /join users user_account[\s\S]*user_account\.is_active = true/i);
		assert.match(sql, /where charge\.campus_id = :campusId/i);
		assert.match(sql, /charge\.user_id = :userId/i);
		assert.match(sql, /lower\(user_account\.name\) like :keyword escape '!'/i);
		assert.match(sql, /charge\.payment_category = :paymentCategory/i);
		assert.match(sql, /charge\.payment_category <> :excludedPaymentCategory/i);
		assert.match(sql, /charge\.status = :status/i);
		assert.match(sql, /charge\.payment_account_id in \(:paymentAccountIds\)/i);
		assert.match(sql, /charge\.paid_at >= :terminalCompletedAtFrom/i);
		assert.match(sql, /charge\.updated_at >= :terminalCompletedAtFrom/i);
	}
	assert.match(shapes.summary, /select coalesce\(sum\(charge\.amount\), 0\) as total_amount/i);
	assert.match(shapes.memberPage, /group by charge\.user_id, user_account\.name, user_account\.email/i);
	assert.match(shapes.memberPage, /order by max\(charge\.created_at\) desc, charge\.user_id asc/i);
	assert.match(shapes.memberPage, /limit :limit offset :offset/i);
	assert.match(shapes.countDistinct, /^select count\(distinct charge\.user_id\)/i);

	const emptyScope = buildAdminChargeAggregationSql(
		{ ...criteria, paymentAccountIds: [] },
		{ size: 10, offset: 0, sort: 'createdAt,desc' },
	);
	assert.match(emptyScope.summary, /and 1 = 0/i);
	assert.doesNotMatch(emptyScope.summary, /payment_account_id in/i);
	assert.throws(
		() => buildAdminChargeAggregationSql(criteria, { size: 10, offset: 0, sort: 'unsupported,asc' }),
		/Unsupported admin charge member sort/,
	);
});

test('all 16 API cases compare the same three statements and keep keyword indexing unapproved', () => {
	const handoff = readJson('i193-production-candidate-handoff.json');
	assert.deepEqual(handoff.apiCaseExplainMatrix.map(({ caseName }) => caseName), expectedCaseNames);
	for (const row of handoff.apiCaseExplainMatrix) {
		assert.deepEqual(row.statements, ['summary', 'member-page', 'count-distinct']);
		assert.equal(row.pageSize, 10);
		assert.equal(row.includeArchived, false);
		assert.equal(row.productionBeforeEligible, false);
	}
	for (const keywordCase of handoff.apiCaseExplainMatrix.filter(({ caseName }) => caseName.endsWith('keyword'))) {
		assert.equal(keywordCase.leadingWildcard, true);
		assert.equal(keywordCase.indexCandidateApproved, false);
	}
	assert.deepEqual(handoff.indexCandidates.map(({ definition }) => definition), [
		'charge_items(campus_id, payment_category, status, user_id)',
		'charge_items(campus_id, payment_account_id, payment_category, status, user_id)',
		'campus_members(campus_id, status, user_id)',
	]);
	for (const candidate of handoff.indexCandidates.slice(0, 2)) {
		assert.equal(candidate.approved, false);
		assert.equal(candidate.existingIndexComparison.name, 'uk_charge_items_source');
		assert.equal(
			candidate.existingIndexComparison.definition,
			'UNIQUE(campus_id, user_id, payment_category, source_type, source_id)',
		);
		assert.deepEqual(Object.keys(candidate.statementApplicability), ['summary', 'member-page', 'count-distinct']);
		for (const assessment of Object.values(candidate.statementApplicability)) {
			assert.equal(typeof assessment, 'string');
		}
		assert.equal(typeof candidate.duplicationRisk, 'string');
		assert.equal(typeof candidate.writeCost, 'string');
		assert.equal(typeof candidate.partialIndexAssessment, 'string');
	}
	const memberCandidate = handoff.indexCandidates[2];
	assert.equal(memberCandidate.approved, false);
	assert.equal(memberCandidate.disposition, 'conditional-plan-proof-required');
	assert.deepEqual(memberCandidate.existingIndexComparison, {
		name: 'uk_campus_members_campus_user',
		definition: 'UNIQUE(campus_id, user_id)',
	});
	assert.match(memberCandidate.conditionalEvidenceRequired, /status.*actual plan.*write cost/i);
	assert.equal(handoff.leadingWildcardKeyword.approved, false);
	assert.equal(handoff.leadingWildcardKeyword.requiresSeparateExtensionOrIndexApproval, true);
});
