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

function aggregateExpectation(page = 0) {
	const totalElements = page === 0 ? 1 : 11;
	return {
		summary: {totalAmount: 100, unpaidAmount: 100, paidAmount: 0, waivedAmount: 0, canceledAmount: 0},
		memberRows: [{
			userId: 401, name: 'MEMBER', email: 'member@example.com', totalAmount: 100,
			unpaidAmount: 100, paidAmount: 0, waivedAmount: 0, canceledAmount: 0,
		}],
		page,
		size: 10,
		totalElements,
		totalPages: Math.ceil(totalElements / 10),
	};
}

function archiveExpectation(prefix, included) {
	const base = prefix === 'admin' ? 6000 : 3000;
	const summary = {
		unpaidAmount: base + 11,
		paidAmount: base + 12 + (included ? base + 15 : 0),
		waivedAmount: base + 13 + (included ? base + 16 : 0),
		canceledAmount: base + 14 + (included ? base + 17 : 0),
	};
	summary.totalAmount = summary.unpaidAmount + summary.paidAmount + summary.waivedAmount + summary.canceledAmount;
	return {
		summary,
		memberRows: [{
			userId: 401, name: 'MEMBER', email: 'member@example.com',
			...summary,
		}],
		page: 0,
		size: 10,
		totalElements: 1,
		totalPages: 1,
	};
}

async function validManifest() {
	const {buildRequestCases} = await definition();
	const manifest = {
		datasetId: 'RUN_A',
		fixtureRunId: 'FIXTURE_A',
		campusId: 301,
		campusName: 'PERF_ISSUE_193:RUN_A',
		region: 'PERF_REGION',
		activeMemberCount: 1000,
		fixtureAccountCount: 5,
		fixtureChargeCount: 35000,
		crossCampusId: 302,
		requesterUserId: 501,
		dutyUserId: 502,
		targetUserId: 401,
		targetKeyword: 'member@example.com',
		ownedCoffeeAccountId: 601,
		fixtureAccountId: 602,
		dutyOwnedCoffeeAccountId: 603,
		dutyHistoricalCoffeeAccountId: 605,
		foreignCoffeeAccountId: 602,
		crossCampusAccountId: 604,
		archivedMemberUserId: 401,
		sourceDuplicateCount: 0,
		cases: {},
		archiveCases: {},
		dutyScope: {},
	};
	for (const requestCase of buildRequestCases(manifest, manifest.campusId)) {
		manifest.cases[requestCase.name] = aggregateExpectation(requestCase.query.page);
	}
	for (const name of ['admin_archive_default', 'admin_archive_included', 'my_archive_default', 'my_archive_included']) {
		manifest.archiveCases[name] = {
			campusId: manifest.campusId,
			campusName: manifest.campusName,
			region: manifest.region,
			...archiveExpectation(name.startsWith('admin_') ? 'admin' : 'my', name.endsWith('_included')),
		};
	}
	const dutyAggregate = {
		status: 200,
		campusId: manifest.campusId,
		campusName: manifest.campusName,
		region: manifest.region,
		...aggregateExpectation(),
	};
	manifest.dutyScope.duty_owned_accounts_visible = structuredClone(dutyAggregate);
	manifest.dutyScope.duty_owned_account_filter_visible = structuredClone(dutyAggregate);
	manifest.dutyScope.duty_foreign_account_hidden = {status: 403};
	manifest.dutyScope.duty_member_detail_owned_only = {
		status: 200,
		campusId: 301,
		campusName: 'PERF_ISSUE_193:RUN_A',
		region: 'PERF_REGION',
		userId: 401,
		name: 'MEMBER',
		email: 'member@example.com',
		summary: aggregateExpectation().summary,
		items: [
			{
				id: 702,
				paymentCategory: 'COFFEE',
				title: 'RECENT',
				reason: 'PERF_ISSUE_193:FIXTURE_A:RECENT',
				amount: 100,
				status: 'PAID',
				dueDate: null,
				paidAt: '2026-07-10T00:00:00.000Z',
				account: {paymentAccountId: 603, bankName: 'BANK', accountNumber: '123', accountHolder: 'OWNER'},
				source: {sourceType: 'POLL_RESPONSE', sourceId: 802},
				_sortCreatedAt: '2026-07-10T00:00:00.000Z',
			},
			{
				id: 701,
				paymentCategory: 'COFFEE',
				title: 'OLD',
				reason: 'PERF_ISSUE_193:FIXTURE_A:OLD',
				amount: 50,
				status: 'UNPAID',
				dueDate: null,
				paidAt: null,
				account: {paymentAccountId: 605, bankName: 'BANK', accountNumber: '456', accountHolder: 'OWNER'},
				source: {sourceType: 'POLL_RESPONSE', sourceId: 801},
				_sortCreatedAt: '2026-07-09T00:00:00.000Z',
			},
		],
		page: 0,
		size: 10,
		totalElements: 2,
		totalPages: 1,
	};
	return manifest;
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
	const expected = {
		campusId: 301,
		campusName: 'PERF_ISSUE_193:RUN_A',
		region: 'PERF_REGION',
		summary: {totalAmount: 100, unpaidAmount: 100, paidAmount: 0, waivedAmount: 0, canceledAmount: 0},
		memberRows: [{
			userId: 401, name: 'ARCHIVED', email: 'archived@example.com', totalAmount: 100,
			unpaidAmount: 100, paidAmount: 0, waivedAmount: 0, canceledAmount: 0,
		}],
		page: 0,
		size: 10,
		totalElements: 1,
		totalPages: 1,
	};
	const body = {
		success: true,
		data: {
			campusId: expected.campusId,
			campusName: expected.campusName,
			region: expected.region,
			summary: expected.summary,
			members: expected.memberRows,
			page: 0,
			size: 10,
			totalElements: 1,
			totalPages: 1,
		},
	};
	assert.equal(validateArchiveProbeResponse(body, probes[0], {admin_archive_default: expected}), true);
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
		'duty_owned_accounts_visible',
		'duty_owned_account_filter_visible',
		'duty_foreign_account_hidden',
		'duty_member_detail_owned_only',
	]);
	assert.equal(Object.hasOwn(probes[0].query, 'paymentAccountId'), false);
	assert.equal(probes[1].query.paymentAccountId, 601);
	assert.match(probes[3].path, /\/members\/101\/charges$/);
	assert.equal(probes[3].query.paymentCategory, 'COFFEE');
});

test('fixture models recent terminal, archived terminal, and old unpaid rows without deleting data', async () => {
	const fixture = await read('prepare-fixture.sql');
	assert.match(fixture, /INTERVAL '1 month'/i);
	assert.match(fixture, /ARCHIVED_TERMINAL/);
	assert.match(fixture, /RECENT_TERMINAL/);
	assert.match(fixture, /OLD_UNPAID/);
	assert.match(fixture, /LEFT JOIN users[\s\S]*u\.is_active IS DISTINCT FROM TRUE[\s\S]*active_members_have_active_users/i);
	assert.match(fixture, /PERF_ISSUE_193:[\s\S]*:DUTY_HISTORY[\s\S]*FALSE[\s\S]*CURRENT_TIMESTAMP/i);
	assert.match(fixture, /'PENALTY'::text AS category, 'DEVOTION_RECORD'::text AS source_type/);
	assert.match(fixture, /&\s*4503599627370495/);
	assert.doesNotMatch(fixture, /&\s*4611686018427387903/);
	assert.match(fixture, /SET TIME ZONE 'Asia\/Seoul'/);
	assert.match(fixture, /WHERE name = 'PERF_ISSUE_193:' \|\| :'dataset_id'/);
	assert.doesNotMatch(fixture, /WHERE nickname LIKE 'PERF_ISSUE_193:%'/);
	assert.doesNotMatch(fixture, /WHERE reason LIKE 'PERF_ISSUE_193:%'/);
	assert.doesNotMatch(fixture, /\b(?:UPDATE|DELETE|TRUNCATE|DROP)\b/i);
});

test('terminal fixture and archive expectations follow the production one-month cutoff fields', async () => {
	const fixture = await read('prepare-fixture.sql');
	assert.match(fixture, /CASE WHEN s\.status = 'PAID' THEN s\.completed_at ELSE NULL END/);
	assert.match(fixture, /s\.completed_at\s*\nFROM fixture_members/i);
	assert.doesNotMatch(fixture, /CURRENT_TIMESTAMP\s*\nFROM fixture_members/i);

	const cutoff = Date.parse('2026-06-15T00:00:00.000Z');
	const rows = [
		{status: 'UNPAID', amount: 100, paidAt: null, updatedAt: cutoff - 1},
		{status: 'PAID', amount: 200, paidAt: cutoff + 1, updatedAt: cutoff + 1},
		{status: 'WAIVED', amount: 300, paidAt: null, updatedAt: cutoff + 1},
		{status: 'CANCELED', amount: 400, paidAt: null, updatedAt: cutoff + 1},
		{status: 'PAID', amount: 500, paidAt: cutoff - 1, updatedAt: cutoff - 1},
		{status: 'WAIVED', amount: 600, paidAt: null, updatedAt: cutoff - 1},
		{status: 'CANCELED', amount: 700, paidAt: null, updatedAt: cutoff - 1},
	];
	const visible = (row) => row.status === 'UNPAID'
		|| (row.status === 'PAID' && row.paidAt >= cutoff)
		|| (['WAIVED', 'CANCELED'].includes(row.status) && row.updatedAt >= cutoff);
	const summarize = (selected) => Object.fromEntries(
		['UNPAID', 'PAID', 'WAIVED', 'CANCELED'].map((status) => [
			status,
			selected.filter((row) => row.status === status).reduce((sum, row) => sum + row.amount, 0),
		]),
	);
	assert.deepEqual(summarize(rows.filter(visible)), {UNPAID: 100, PAID: 200, WAIVED: 300, CANCELED: 400});
	assert.deepEqual(summarize(rows), {UNPAID: 100, PAID: 700, WAIVED: 900, CANCELED: 1100});
});

test('fixture creates a namespace-fresh exact 1,000-member dataset instead of reusing a matching campus', async () => {
	const fixture = await read('prepare-fixture.sql');
	assert.match(fixture, /dataset_namespace_is_fresh/i);
	assert.match(fixture, /INSERT INTO campuses/i);
	assert.match(fixture, /requester_user_id[\s\S]*duty_requester_user_id[\s\S]*LIMIT 998/i);
	assert.match(fixture, /COUNT\(\*\)\s*=\s*1000/i);
	assert.match(fixture, /name\s*=\s*'PERF_ISSUE_193:'\s*\|\|\s*:'dataset_id'/i);
	assert.doesNotMatch(fixture, /name LIKE '%'\s*\|\|\s*:'dataset_id'/i);
});

test('synthetic before and after namespaces stay isolated with identical shape', async () => {
	const fixture = await read('prepare-fixture.sql');
	assert.match(fixture, /:'dataset_id'/);
	assert.match(fixture, /:'fixture_run_id'/);
	assert.doesNotMatch(fixture, /nickname LIKE 'PERF_ISSUE_193:%'/);
	assert.doesNotMatch(fixture, /reason LIKE 'PERF_ISSUE_193:%'/);

	const shape = (datasetId, fixtureRunId) => ({
		campusName: `PERF_ISSUE_193:${datasetId}`,
		marker: `PERF_ISSUE_193:${datasetId}:${fixtureRunId}`,
		activeMembers: 1000,
		accounts: 5,
		charges: 35000,
	});
	const before = shape('BEFORE', 'FIXTURE_BEFORE');
	const after = shape('AFTER', 'FIXTURE_AFTER');
	assert.notEqual(before.campusName, after.campusName);
	assert.notEqual(before.marker, after.marker);
	assert.deepEqual(
		{activeMembers: before.activeMembers, accounts: before.accounts, charges: before.charges},
		{activeMembers: after.activeMembers, accounts: after.accounts, charges: after.charges},
	);
});

test('fixture expectations apply the one-month terminal cutoff and page size 10 to all cases', async () => {
	const expectations = await read('fixture-expectations.sql');
	assert.match(expectations, /paid_at\s*>=\s*CURRENT_TIMESTAMP\s*-\s*INTERVAL '1 month'/i);
	assert.match(expectations, /updated_at\s*>=\s*CURRENT_TIMESTAMP\s*-\s*INTERVAL '1 month'/i);
	assert.match(expectations, /SET TIME ZONE 'Asia\/Seoul'/);
	assert.match(expectations, /duty_active_accounts[\s\S]*is_active = TRUE[\s\S]*deleted_at IS NULL/i);
	assert.match(expectations, /duty_all_accounts[\s\S]*owner_user_id = :'duty_requester_user_id'/i);
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

test('archive and duty validators reject wrong campus identity and mutated public item fields', async () => {
	const {
		buildArchiveCorrectnessProbes,
		buildDutyScopeProbes,
		validateArchiveProbeResponse,
		validateDutyAggregateResponse,
		validateDutyMemberDetailResponse,
	} = await definition();
	const manifest = await validManifest();
	const aggregateBody = {
		success: true,
		data: {
			campusId: manifest.campusId,
			campusName: manifest.campusName,
			region: manifest.region,
			summary: aggregateExpectation().summary,
			members: aggregateExpectation().memberRows,
			page: 0,
			size: 10,
			totalElements: 1,
			totalPages: 1,
		},
	};
	const archiveProbe = buildArchiveCorrectnessProbes(manifest, manifest.campusId)[0];
	const archiveExpected = manifest.archiveCases[archiveProbe.name];
	const archiveBody = {
		success: true,
		data: {
			campusId: archiveExpected.campusId,
			campusName: archiveExpected.campusName,
			region: archiveExpected.region,
			summary: archiveExpected.summary,
			members: archiveExpected.memberRows,
			page: archiveExpected.page,
			size: archiveExpected.size,
			totalElements: archiveExpected.totalElements,
			totalPages: archiveExpected.totalPages,
		},
	};
	assert.equal(validateArchiveProbeResponse(archiveBody, archiveProbe, manifest.archiveCases), true);
	const wrongArchiveCampus = structuredClone(archiveBody);
	wrongArchiveCampus.data.campusId += 1;
	assert.equal(validateArchiveProbeResponse(wrongArchiveCampus, archiveProbe, manifest.archiveCases), false);

	const dutyProbe = buildDutyScopeProbes(manifest, manifest.campusId)[0];
	assert.equal(validateDutyAggregateResponse(aggregateBody, dutyProbe, manifest.dutyScope), true);
	const wrongDutyCampus = structuredClone(aggregateBody);
	wrongDutyCampus.data.campusName = 'OTHER';
	assert.equal(validateDutyAggregateResponse(wrongDutyCampus, dutyProbe, manifest.dutyScope), false);

	const expectedDetail = manifest.dutyScope.duty_member_detail_owned_only;
	const detailBody = {
		success: true,
		data: {
			campusId: expectedDetail.campusId,
			campusName: expectedDetail.campusName,
			region: expectedDetail.region,
			userId: expectedDetail.userId,
			name: expectedDetail.name,
			email: expectedDetail.email,
			summary: expectedDetail.summary,
			items: expectedDetail.items.map(({_sortCreatedAt, ...item}) => item),
			page: expectedDetail.page,
			size: expectedDetail.size,
			totalElements: expectedDetail.totalElements,
			totalPages: expectedDetail.totalPages,
		},
	};
	assert.equal(validateDutyMemberDetailResponse(detailBody, expectedDetail), true);
	for (const mutate of [
		(body) => { body.data.campusId += 1; },
		(body) => { body.data.name = 'OTHER'; },
		(body) => { body.data.email = 'other@example.com'; },
		(body) => { body.data.items[0].amount += 1; },
		(body) => { body.data.items[0].status = 'UNPAID'; },
		(body) => { body.data.items[0].source.sourceId += 1; },
	]) {
		const mutated = structuredClone(detailBody);
		mutate(mutated);
		assert.equal(validateDutyMemberDetailResponse(mutated, expectedDetail), false);
	}
});

test('manifest validation fails closed for exact archive and duty schemas', async () => {
	const {validateExpectationsManifest} = await definition();
	const valid = await validManifest();
	assert.equal(validateExpectationsManifest(valid, valid.campusId), true);

	const mutations = [
		(manifest) => { delete manifest.dutyScope.duty_owned_accounts_visible.status; },
		(manifest) => { manifest.dutyScope.duty_owned_accounts_visible.status = '200'; },
		(manifest) => { delete manifest.dutyScope.duty_owned_accounts_visible.summary.totalAmount; },
		(manifest) => { manifest.dutyScope.duty_owned_accounts_visible.extra = true; },
		(manifest) => { manifest.dutyScope.duty_member_detail_owned_only.items[1].id = 702; },
		(manifest) => { manifest.dutyScope.duty_member_detail_owned_only.items.reverse(); },
		(manifest) => { manifest.archiveCases.extra = aggregateExpectation(); },
		(manifest) => { manifest.activeMemberCount = 999; },
		(manifest) => { manifest.activeMemberCount = 1001; },
		(manifest) => { manifest.campusName = 'FOREIGN-PERF_ISSUE_193:RUN_A'; },
		(manifest) => { manifest.foreignCoffeeAccountId = '602'; },
	];
	for (const mutate of mutations) {
		const malformed = structuredClone(valid);
		mutate(malformed);
		assert.throws(() => validateExpectationsManifest(malformed, malformed.campusId));
	}
});

test('member-detail ordering preserves RFC3339 nanoseconds and uses id only for exact instant ties', async () => {
	const {validateExpectationsManifest} = await definition();
	const distinctInstants = await validManifest();
	const distinctItems = distinctInstants.dutyScope.duty_member_detail_owned_only.items;
	distinctItems[0].id = 701;
	distinctItems[0]._sortCreatedAt = '2026-07-16T12:34:37.542110123Z';
	distinctItems[1].id = 702;
	distinctItems[1]._sortCreatedAt = '2026-07-16T21:34:37.542109999+09:00';
	assert.equal(validateExpectationsManifest(distinctInstants, distinctInstants.campusId), true);

	const exactTie = await validManifest();
	const tieItems = exactTie.dutyScope.duty_member_detail_owned_only.items;
	tieItems[0].id = 702;
	tieItems[0]._sortCreatedAt = '2026-07-16T12:34:37.542110123Z';
	tieItems[1].id = 701;
	tieItems[1]._sortCreatedAt = '2026-07-16T21:34:37.542110123+09:00';
	assert.equal(validateExpectationsManifest(exactTie, exactTie.campusId), true);
	tieItems.reverse();
	assert.throws(() => validateExpectationsManifest(exactTie, exactTie.campusId));

	for (const invalid of [
		'2026-07-16',
		'2026-07-16T12:34:37Zjunk',
		'2026-02-30T12:34:37Z',
		'2026-07-16T24:00:00Z',
		'2026-07-16T12:34:37.1234567890Z',
	]) {
		const malformed = await validManifest();
		malformed.dutyScope.duty_member_detail_owned_only.items[0]._sortCreatedAt = invalid;
		assert.throws(() => validateExpectationsManifest(malformed, malformed.campusId), invalid);
	}
});

test('manifest rejects archive false-greens without exact terminal status deltas', async () => {
	const {validateExpectationsManifest} = await definition();
	for (const prefix of ['admin', 'my']) {
		const malformed = await validManifest();
		malformed.archiveCases[`${prefix}_archive_included`] = structuredClone(
			malformed.archiveCases[`${prefix}_archive_default`],
		);
		assert.throws(() => validateExpectationsManifest(malformed, malformed.campusId));
	}
});

test('documents scenario-only status, immutable baseline server, and the measurement approval gate', async () => {
	const readme = await read('README.md');
	assert.match(readme, /scenario and runner\/evidence contract-ready, fake\/static verified, not measured/i);
	for (const immutableIdentity of [
		'6796ed146244d8f3f5b5dd7048ebe16865084a97',
		'a7df78b330f457a7fd60a9531362d0f1f063ae7aa6cae5f2d996eb8cb51fe79d',
		'sha256:8e0f8d85d697a7d34aabf3703ddb27b4f1af326dec4f7c35556986303b0b816c',
		'2026-07-16T04:23:10.082407837Z',
		'/private/tmp/FaithLog-perf-206-deploy',
		'81aa74ca1b491b45eb691b3d65de9e42eb47ef64a6bcb961d0b627b030139ae9',
		'sha256:48d29282d2b43c402465c28f8572021b59aaf43574056faaad2fd7bb85ffdd4e',
		'4109f6525948d12d1e5377fb6160c8955f6c3fcd7816e02786b2dd8031e23de9',
		'sha256:80dd823f4d2bf93dd5e418a0ae2817319a1ba279953e234082e54a5a18306223',
	]) {
		assert.match(readme, new RegExp(immutableIdentity.replace(/[.*+?^${}()|[\]\\]/g, '\\$&')));
	}
	assert.match(readme, /#206[\s\S]*createdAt,desc[\s\S]*id,desc/);
	assert.match(readme, /k6\/fixture\/validator\/test\/docs.*별도 사용자 승인 없이.*PM 리뷰/s);
	assert.match(readme, /src\/main.*production backend.*Flyway 변경 직전.*사용자 승인/s);
	assert.match(readme, /실제 수집.*개발 세션에서 실행하지 않/s);
	assert.match(readme, /Docker build.*restart.*prune.*금지/s);
});
