import assert from 'node:assert/strict';
import {readFile} from 'node:fs/promises';
import path from 'node:path';
import test from 'node:test';
import {pathToFileURL} from 'node:url';

const scenarioDir = path.dirname(new URL(import.meta.url).pathname);

async function read(name) {
	return readFile(path.join(scenarioDir, name), 'utf8');
}

async function definition() {
	return import(pathToFileURL(path.join(scenarioDir, 'scenario-definition.mjs')).href);
}

test('defines exactly 16 measured admin-charge cases in frontend order with explicit size 10', async () => {
	const {REQUEST_CASE_NAMES, buildRequestCases} = await definition();
	assert.equal(REQUEST_CASE_NAMES.length, 16);
	const expectations = {
		targetUserId: 101,
		targetKeyword: 'perf-member@example.com',
		ownedCoffeeAccountId: 201,
		fixtureAccountId: 202,
	};
	const cases = buildRequestCases(expectations, 301);
	assert.deepEqual(cases.map(({name}) => name), [...REQUEST_CASE_NAMES]);
	for (const requestCase of cases) {
		assert.equal(requestCase.query.page >= 0, true);
		assert.equal(requestCase.query.size, 10);
		assert.equal(requestCase.query.sort, 'createdAt,desc');
		assert.equal(requestCase.query.includeArchived, false);
	}
	assert.deepEqual(cases.map(({name}) => name), [
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
	]);
});

test('validates only the current response pagination metadata exactly', async () => {
	const {validateCaseResponseSemantics} = await definition();
	const requestCase = {name: 'admin_status', query: {page: 0, size: 10}};
	const expected = {
		campusId: 301,
		cases: {
			admin_status: {
				summary: {totalAmount: 100, unpaidAmount: 0, paidAmount: 100, waivedAmount: 0, canceledAmount: 0},
				memberRows: [{
					userId: 101, name: 'PERF', email: 'perf@example.com', totalAmount: 100,
					unpaidAmount: 0, paidAmount: 100, waivedAmount: 0, canceledAmount: 0,
				}],
				page: 0,
				size: 10,
				totalElements: 1,
				totalPages: 1,
			},
		},
	};
	const body = {
		success: true,
		data: {
			campusId: 301,
			summary: expected.cases.admin_status.summary,
			members: expected.cases.admin_status.memberRows,
			page: 0,
			size: 10,
			totalElements: 1,
			totalPages: 1,
		},
	};
	assert.equal(validateCaseResponseSemantics(body, requestCase, expected), true);
	for (const field of ['page', 'size', 'totalElements', 'totalPages']) {
		const drifted = structuredClone(body);
		drifted.data[field] += 1;
		assert.equal(validateCaseResponseSemantics(drifted, requestCase, expected), false, field);
	}
	for (const nonexistent of ['number', 'first', 'last']) {
		assert.equal(Object.hasOwn(expected.cases.admin_status, nonexistent), false);
	}
});

test('defines separate archive correctness probes for default and includeArchived true', async () => {
	const {buildArchiveCorrectnessProbes, validateArchiveProbeResponse} = await definition();
	const probes = buildArchiveCorrectnessProbes({archivedMemberUserId: 401}, 301);
	assert.deepEqual(probes.map(({name}) => name), [
		'admin_archive_default',
		'admin_archive_included',
		'my_archive_default',
		'my_archive_included',
	]);
	assert.deepEqual(probes.map(({query}) => query.includeArchived), [false, true, false, true]);
	assert.equal(validateArchiveProbeResponse({success: true, data: {totalElements: 1}}, probes[0], {
		admin_archive_default: {totalElements: 1},
	}), true);
	assert.equal(validateArchiveProbeResponse({success: true, data: {totalElements: 2}}, probes[0], {
		admin_archive_default: {totalElements: 1},
	}), false);
});

test('defines separate COFFEE duty owned-account and member-detail HTTP preflight probes', async () => {
	const {buildDutyScopeProbes} = await definition();
	const probes = buildDutyScopeProbes({
		dutyUserId: 501,
		targetUserId: 101,
		dutyOwnedCoffeeAccountId: 601,
		foreignCoffeeAccountId: 602,
	}, 301);
	assert.deepEqual(probes.map(({name}) => name), [
		'duty_owned_account_visible',
		'duty_foreign_account_hidden',
		'duty_member_detail_owned_only',
	]);
	assert.match(probes[2].path, /\/members\/101\/charges$/);
	assert.equal(probes[2].query.paymentCategory, 'COFFEE');
});

test('fixture models recent terminal, archived terminal, and old unpaid rows without deleting data', async () => {
	const fixture = await read('prepare-fixture.sql');
	assert.match(fixture, /INTERVAL '1 month'/i);
	assert.match(fixture, /ARCHIVED_TERMINAL/);
	assert.match(fixture, /RECENT_TERMINAL/);
	assert.match(fixture, /OLD_UNPAID/);
	assert.doesNotMatch(fixture, /\b(?:UPDATE|DELETE|TRUNCATE|DROP)\b/i);
});

test('fixture expectations apply the one-month terminal cutoff and page size 10 to all cases', async () => {
	const expectations = await read('fixture-expectations.sql');
	assert.match(expectations, /paid_at\s*>=\s*CURRENT_TIMESTAMP\s*-\s*INTERVAL '1 month'/i);
	assert.match(expectations, /updated_at\s*>=\s*CURRENT_TIMESTAMP\s*-\s*INTERVAL '1 month'/i);
	assert.match(expectations, /row_number\s*>\s*\(cs\.page\s*\*\s*10\)/i);
	assert.match(expectations, /'page'/);
	assert.match(expectations, /'size'/);
	assert.match(expectations, /'totalElements'/);
	assert.match(expectations, /'totalPages'/);
	assert.doesNotMatch(expectations, /'number'|'first'|'last'/);
});

test('preflight requires distinct admin and duty identities and verifies all correctness gates', async () => {
	const preflight = await read('preflight.mjs');
	assert.match(preflight, /PERF_ADMIN_ACCESS_TOKEN/);
	assert.match(preflight, /PERF_DUTY_ACCESS_TOKEN/);
	assert.match(preflight, /buildRequestCases/);
	assert.match(preflight, /buildArchiveCorrectnessProbes/);
	assert.match(preflight, /buildDutyScopeProbes/);
	assert.match(preflight, /cross-campus/i);
	assert.match(preflight, /source duplicate/i);
});

test('documents scenario-only status, immutable baseline server, and the measurement approval gate', async () => {
	const readme = await read('README.md');
	assert.match(readme, /scenario-ready, not measured/i);
	assert.match(readme, /355f79df5b2e47636b7d1a17dea029da6c93c62d/);
	assert.match(readme, /901dbab3949fc669e7902e6c1471f4d60ffc80b049efa0f9a5203343710a7868/);
	assert.match(readme, /사용자 승인.*before 측정.*production 최적화.*금지/s);
	assert.match(readme, /Docker build.*restart.*prune.*금지/s);
});
