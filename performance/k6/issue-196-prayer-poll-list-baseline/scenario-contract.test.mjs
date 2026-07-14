import assert from 'node:assert/strict';
import { existsSync, readFileSync } from 'node:fs';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const ROOT = dirname(fileURLToPath(import.meta.url));
const REQUIRED_FILES = [
	'fixture-contract.mjs',
	'seed-fixture.mjs',
	'shape-fixture.sh',
	'scenario.js',
	'run-baseline.sh',
	'db-table-stats.sql',
	'summarize-run.mjs',
	'README.md',
];

function read(name) {
	const path = join(ROOT, name);
	return existsSync(path) ? readFileSync(path, 'utf8') : '';
}

test('issue #196 keeps every scenario artifact in one independent directory', () => {
	for (const name of REQUIRED_FILES) {
		assert.equal(existsSync(join(ROOT, name)), true, `missing ${name}`);
	}
});

test('fixture contract separates stable dataset identity from one immutable fixture run', async () => {
	const contractPath = join(ROOT, 'fixture-contract.mjs');
	assert.equal(existsSync(contractPath), true, 'missing fixture-contract.mjs');
	const { FIXTURE_CONTRACT } = await import(`${pathToFileURL(contractPath).href}?test=${Date.now()}`);

	assert.equal(FIXTURE_CONTRACT.datasetId, 'issue-196-prayer-poll-list-v1');
	assert.equal(FIXTURE_CONTRACT.fixtureRunIdRequired, true);
	assert.equal(FIXTURE_CONTRACT.primaryCampus.activeMemberCount, 1000);
	assert.equal(FIXTURE_CONTRACT.isolationCampus.activeMemberCount, 50);
	assert.deepEqual(FIXTURE_CONTRACT.prayer, {
		groupCount: 40,
		membersPerGroup: 25,
		submissionCount: 800,
		unsubmittedCount: 200,
	});
	assert.equal(FIXTURE_CONTRACT.polls.optionCount, 5);
	assert.equal(FIXTURE_CONTRACT.polls.responseCount, 800);
	assert.equal(FIXTURE_CONTRACT.polls.missingMemberCount, 200);
	assert.equal(FIXTURE_CONTRACT.polls.commentCount, 200);
	assert.equal(FIXTURE_CONTRACT.polls.templateCount, 40);
	assert.equal(FIXTURE_CONTRACT.polls.optionsPerTemplate, 8);
	assert.deepEqual(FIXTURE_CONTRACT.polls.visibilityCases.map(({ key }) => key), [
		'open',
		'closed_member_visible',
		'closed_admin_only',
		'closed_expired',
		'scheduled_future',
	]);
	assert.equal(FIXTURE_CONTRACT.existingRowsMayBeUpdatedOrDeleted, false);
});

test('k6 scenario exposes exact Prayer and member/admin Poll read modes', () => {
	const scenario = read('scenario.js');
	for (const mode of ['prayer', 'poll-member', 'poll-admin']) {
		assert.match(scenario, new RegExp(`['\"]${mode}['\"]`));
	}
	for (const endpoint of [
		'/api/v1/admin/campuses/${campusId}/prayer-seasons/current',
		'/api/v1/admin/prayer-seasons/${seasonId}/groups',
		'/api/v1/admin/prayer-seasons/${seasonId}/members/assignable',
		'/api/v1/campuses/${campusId}/prayers/weeks/${weekStartDate}',
		'/api/v1/campuses/${campusId}/polls',
		'/api/v1/campuses/${campusId}/polls/${pollId}',
		'/api/v1/campuses/${campusId}/polls/${pollId}/results',
		'/api/v1/campuses/${campusId}/polls/${pollId}/comments',
		'/api/v1/admin/campuses/${campusId}/polls/${pollId}/missing-members',
		'/api/v1/admin/campuses/${campusId}/poll-templates',
		'/api/v1/admin/campuses/${campusId}/poll-templates/${templateId}',
	]) {
		assert.ok(scenario.includes(endpoint), `missing endpoint ${endpoint}`);
	}
	assert.match(scenario, /summaryTrendStats:\s*\[[^\]]*'p\(50\)'[^\]]*'p\(95\)'[^\]]*'p\(99\)'[^\]]*'max'/s);
	assert.match(scenario, /new Trend\(/);
	assert.match(scenario, /new Counter\(/);
	assert.match(scenario, /new Rate\(/);
});

test('correctness checks lock count, ordering, editable, myGroup, isolation, and poll windows', () => {
	const scenario = read('scenario.js');
	for (const marker of [
		'assertAscending',
		'assertDescending',
		'targetMemberCount',
		'submittedCount',
		'myGroupId',
		'editable',
		'isolation',
		'closed_admin_only',
		'closed_expired',
		'scheduled_future',
		'notRespondedCount',
		'responseCount',
		'respondents',
	]) {
		assert.ok(scenario.includes(marker), `missing correctness marker ${marker}`);
	}
});

test('runner serializes endpoint phases and records runtime evidence without Docker lifecycle mutations', () => {
	const runner = read('run-baseline.sh');
	assert.match(runner, /faithlog-performance-global\.lock/);
	assert.match(runner, /prayer[\s\S]*poll-member[\s\S]*poll-admin/);
	assert.match(runner, /ENDPOINT=/);
	assert.match(runner, /com\.docker\.compose\.project/);
	assert.match(runner, /com\.docker\.compose\.service/);
	assert.match(runner, /EXPECTED_APP_IMAGE/);
	assert.match(runner, /build\/reports\/k6\/issue-196/);
	assert.doesNotMatch(runner, /docker\s+compose\s+(?:up|down|build)|docker\s+(?:system|builder|image|volume)\s+prune/);
	assert.doesNotMatch(runner, /(?:^|\s)(?:source|\.)\s+\.env(?:\s|$)/m);
});

test('DB and log evidence contract is endpoint-scoped and read-only', () => {
	const runner = read('run-baseline.sh');
	const sql = read('db-table-stats.sql');
	const summarizer = read('summarize-run.mjs');

	assert.match(sql, /pg_stat_user_tables/);
	assert.match(sql, /seq_scan/);
	assert.match(sql, /idx_scan/);
	assert.doesNotMatch(sql, /\b(?:insert|update|delete|truncate|alter|drop|create)\b/i);
	assert.match(runner, /org\.hibernate\.SQL/);
	assert.match(runner, /docker\s+logs/);
	assert.match(summarizer, /queriesPerRequest/);
	assert.match(summarizer, /repeatedSql/);
	assert.match(summarizer, /tableCounterDelta/);
	assert.match(summarizer, /scenario-ready/);
});

test('fixture preparation is create-only outside rows owned by the current fixtureRunId', () => {
	const seed = read('seed-fixture.mjs');
	const shaper = read('shape-fixture.sh');

	assert.match(seed, /FIXTURE_RUN_ID/);
	assert.match(seed, /DATASET_ID/);
	assert.match(seed, /manifest/);
	assert.doesNotMatch(seed, /method:\s*['\"]DELETE['\"]/);
	assert.doesNotMatch(seed, /\bdelete\s+from\b/i);
	assert.match(shaper, /fixture_run_id/);
	assert.match(shaper, /created fixture rows only/);
	assert.doesNotMatch(shaper, /\bdelete\s+from\b/i);
	assert.doesNotMatch(shaper, /\btruncate\b/i);
	assert.doesNotMatch(seed, /(?:password|token)\s*:\s*['\"][^'\"]+['\"]/i);
});
