export const PAGE_DEFAULTS = Object.freeze({
	page: 0,
	size: 10,
	sort: 'createdAt,desc',
	includeArchived: false,
});

export const REQUEST_CASE_NAMES = Object.freeze([
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

export function buildRequestCases(expectations, campusId) {
	const adminPath = `/api/v1/admin/campuses/${campusId}/charges`;
	const myAccountsPath = `${adminPath}/my-accounts`;
	return [
		request('my_initial_penalty_unpaid', myAccountsPath, {paymentCategory: 'PENALTY', status: 'UNPAID'}),
		request('my_payment_category', myAccountsPath, {paymentCategory: 'COFFEE', status: 'UNPAID'}),
		request('my_status', myAccountsPath, {paymentCategory: 'COFFEE', status: 'PAID'}),
		request('my_user_id', myAccountsPath, {paymentCategory: 'COFFEE', userId: expectations.targetUserId}),
		request('my_keyword', myAccountsPath, {paymentCategory: 'COFFEE', keyword: expectations.targetKeyword}),
		request('my_payment_account_unknown_param_ignored', myAccountsPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.ownedCoffeeAccountId,
		}),
		request('my_pagination_page_0', myAccountsPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.ownedCoffeeAccountId,
		}),
		request('my_pagination_page_1', myAccountsPath, {
			page: 1,
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.ownedCoffeeAccountId,
		}),
		request('admin_initial_penalty_unpaid', adminPath, {paymentCategory: 'PENALTY', status: 'UNPAID'}),
		request('admin_payment_category', adminPath, {paymentCategory: 'COFFEE', status: 'UNPAID'}),
		request('admin_status', adminPath, {paymentCategory: 'COFFEE', status: 'PAID'}),
		request('admin_user_id', adminPath, {paymentCategory: 'COFFEE', userId: expectations.targetUserId}),
		request('admin_keyword', adminPath, {paymentCategory: 'COFFEE', keyword: expectations.targetKeyword}),
		request('admin_payment_account', adminPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.fixtureAccountId,
		}),
		request('admin_pagination_page_0', adminPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.fixtureAccountId,
		}),
		request('admin_pagination_page_1', adminPath, {
			page: 1,
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.fixtureAccountId,
		}),
	];
}

export function buildArchiveCorrectnessProbes(expectations, campusId) {
	const adminPath = `/api/v1/admin/campuses/${campusId}/charges`;
	const myAccountsPath = `${adminPath}/my-accounts`;
	const common = {
		paymentCategory: 'COFFEE',
		userId: expectations.archivedMemberUserId,
	};
	return [
		request('admin_archive_default', adminPath, {
			...common,
			paymentAccountId: expectations.fixtureAccountId,
		}),
		request('admin_archive_included', adminPath, {
			...common,
			paymentAccountId: expectations.fixtureAccountId,
			includeArchived: true,
		}),
		request('my_archive_default', myAccountsPath, common),
		request('my_archive_included', myAccountsPath, {...common, includeArchived: true}),
	];
}

export function buildDutyScopeProbes(expectations, campusId) {
	const adminPath = `/api/v1/admin/campuses/${campusId}/charges`;
	return [
		request('duty_owned_account_visible', adminPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.dutyOwnedCoffeeAccountId,
		}),
		request('duty_foreign_account_hidden', adminPath, {
			paymentCategory: 'COFFEE',
			paymentAccountId: expectations.foreignCoffeeAccountId,
		}),
		request(
			'duty_member_detail_owned_only',
			`/api/v1/admin/campuses/${campusId}/members/${expectations.targetUserId}/charges`,
			{paymentCategory: 'COFFEE'},
		),
	];
}

export function validateCaseResponseSemantics(body, requestCase, expectations) {
	try {
		if (body?.success !== true || body?.data?.campusId !== expectations?.campusId) {
			return false;
		}
		const expected = expectations.cases?.[requestCase?.name];
		validateAggregateExpectation(expected);
		return semanticEqual(body.data.summary, expected.summary)
			&& semanticEqual(body.data.members?.map(toComparableMemberRow), expected.memberRows)
			&& paginationMatches(body.data, expected);
	} catch (_error) {
		return false;
	}
}

export function validateArchiveProbeResponse(body, probe, archiveCases) {
	const expected = archiveCases?.[probe?.name];
	return body?.success === true && expected !== undefined && semanticSubset(body.data, expected);
}

export function validateDutyAggregateResponse(body, probe, dutyScope) {
	const expected = dutyScope?.[probe?.name];
	if (body?.success !== true || !expected) {
		return false;
	}
	return semanticEqual(body.data.summary, expected.summary)
		&& semanticEqual(body.data.members?.map(toComparableMemberRow), expected.memberRows)
		&& paginationMatches(body.data, expected);
}

export function validateDutyMemberDetailResponse(body, expected) {
	if (body?.success !== true || !expected) {
		return false;
	}
	return body.data?.userId === expected.userId
		&& semanticEqual(body.data.summary, expected.summary)
		&& semanticEqual(
			body.data.items?.map((item) => ({id: item?.id, paymentAccountId: item?.account?.paymentAccountId})),
			expected.items,
		)
		&& paginationMatches(body.data, expected);
}

export function validateExpectationsManifest(expectations, campusId) {
	if (!Number.isSafeInteger(campusId) || campusId <= 0 || expectations?.campusId !== campusId) {
		throw new Error('Case manifest campus scope does not match the measured campus.');
	}
	const actualNames = Object.keys(expectations.cases ?? {}).sort();
	if (!semanticEqual(actualNames, [...REQUEST_CASE_NAMES].sort())) {
		throw new Error('Case manifest must contain exactly all 16 measured request cases.');
	}
	for (const requestCase of buildRequestCases(expectations, campusId)) {
		const expected = expectations.cases[requestCase.name];
		validateAggregateExpectation(expected);
		if (expected.page !== requestCase.query.page || expected.size !== requestCase.query.size) {
			throw new Error(`${requestCase.name} expected pagination does not match the request.`);
		}
	}
	for (const name of ['admin_archive_default', 'admin_archive_included', 'my_archive_default', 'my_archive_included']) {
		if (!expectations.archiveCases?.[name]) {
			throw new Error(`Archive correctness expectation is missing: ${name}.`);
		}
	}
	for (const name of ['duty_owned_account_visible', 'duty_foreign_account_hidden', 'duty_member_detail_owned_only']) {
		if (!expectations.dutyScope?.[name]) {
			throw new Error(`Duty-scope correctness expectation is missing: ${name}.`);
		}
	}
	return true;
}

export function encodeQuery(values) {
	return Object.entries(values)
		.filter(([, value]) => value !== undefined && value !== null && value !== '')
		.map(([key, value]) => `${encodeURIComponent(key)}=${encodeURIComponent(String(value))}`)
		.join('&');
}

export function semanticEqual(actual, expected) {
	if (Object.is(actual, expected)) {
		return true;
	}
	if (Array.isArray(actual) || Array.isArray(expected)) {
		return Array.isArray(actual)
			&& Array.isArray(expected)
			&& actual.length === expected.length
			&& actual.every((value, index) => semanticEqual(value, expected[index]));
	}
	if (actual === null || expected === null || typeof actual !== 'object' || typeof expected !== 'object') {
		return false;
	}
	const actualKeys = Object.keys(actual).sort();
	const expectedKeys = Object.keys(expected).sort();
	return semanticEqual(actualKeys, expectedKeys)
		&& actualKeys.every((key) => semanticEqual(actual[key], expected[key]));
}

function request(name, path, overrides) {
	return {name, path, query: {...PAGE_DEFAULTS, ...overrides}};
}

function validateAggregateExpectation(expected) {
	if (!expected || !Array.isArray(expected.memberRows)) {
		throw new Error('Aggregate expectation is incomplete.');
	}
	for (const field of ['page', 'size', 'totalElements', 'totalPages']) {
		if (!Number.isSafeInteger(expected[field]) || expected[field] < 0) {
			throw new Error(`Invalid pagination metadata: ${field}.`);
		}
	}
	if (expected.size <= 0) {
		throw new Error('Pagination size must be positive.');
	}
	if (expected.memberRows.length > expected.size) {
		throw new Error('Member rows exceed the requested page size.');
	}
	for (const row of expected.memberRows) {
		const amounts = [row.totalAmount, row.unpaidAmount, row.paidAmount, row.waivedAmount, row.canceledAmount];
		if (!Number.isSafeInteger(row.userId)
			|| typeof row.name !== 'string'
			|| typeof row.email !== 'string'
			|| amounts.some((value) => !Number.isSafeInteger(value) || value < 0)
			|| row.totalAmount !== amounts.slice(1).reduce((sum, value) => sum + value, 0)) {
			throw new Error('Invalid member aggregate expectation.');
		}
	}
}

function paginationMatches(data, expected) {
	return data?.page === expected.page
		&& data?.size === expected.size
		&& data?.totalElements === expected.totalElements
		&& data?.totalPages === expected.totalPages;
}

function toComparableMemberRow(member) {
	return {
		userId: member?.userId,
		name: member?.name,
		email: member?.email,
		totalAmount: member?.totalAmount,
		unpaidAmount: member?.unpaidAmount,
		paidAmount: member?.paidAmount,
		waivedAmount: member?.waivedAmount,
		canceledAmount: member?.canceledAmount,
	};
}

function semanticSubset(actual, expected) {
	if (Array.isArray(expected)) {
		return Array.isArray(actual)
			&& actual.length === expected.length
			&& expected.every((value, index) => semanticSubset(actual[index], value));
	}
	if (expected === null || typeof expected !== 'object') {
		return Object.is(actual, expected);
	}
	return actual !== null
		&& typeof actual === 'object'
		&& Object.entries(expected).every(([key, value]) => semanticSubset(actual[key], value));
}
