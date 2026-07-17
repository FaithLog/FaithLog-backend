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
		request('duty_owned_accounts_visible', adminPath, {
			paymentCategory: 'COFFEE',
		}),
		request('duty_owned_account_filter_visible', adminPath, {
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
		validateAggregateExpectation(expected, {exactKeys: true});
		return semanticEqual(body.data.summary, expected.summary)
			&& semanticEqual(body.data.members?.map(toComparableMemberRow), expected.memberRows)
			&& paginationMatches(body.data, expected);
	} catch (_error) {
		return false;
	}
}

export function validateArchiveProbeResponse(body, probe, archiveCases) {
	try {
		const expected = archiveCases?.[probe?.name];
		validateAggregateExpectation(expected, {identityRequired: true, exactKeys: true});
		return body?.success === true
			&& identityMatches(body.data, expected)
			&& semanticEqual(body.data?.summary, expected.summary)
			&& semanticEqual(body.data?.members?.map(toComparableMemberRow), expected.memberRows)
			&& paginationMatches(body.data, expected);
	} catch (_error) {
		return false;
	}
}

export function validateDutyAggregateResponse(body, probe, dutyScope) {
	try {
		const expected = dutyScope?.[probe?.name];
		validateAggregateExpectation(expected, {identityRequired: true, statusRequired: true, exactKeys: true});
		return body?.success === true
			&& identityMatches(body.data, expected)
			&& semanticEqual(body.data?.summary, expected.summary)
			&& semanticEqual(body.data?.members?.map(toComparableMemberRow), expected.memberRows)
			&& paginationMatches(body.data, expected);
	} catch (_error) {
		return false;
	}
}

export function validateDutyMemberDetailResponse(body, expected) {
	try {
		validateMemberDetailExpectation(expected);
		return body?.success === true
			&& identityMatches(body.data, expected)
			&& body.data?.userId === expected.userId
			&& body.data?.name === expected.name
			&& body.data?.email === expected.email
			&& semanticEqual(body.data?.summary, expected.summary)
			&& semanticEqual(body.data?.items?.map(toComparableChargeItem), expected.items.map(toExpectedChargeItem))
			&& paginationMatches(body.data, expected);
	} catch (_error) {
		return false;
	}
}

export function validateExpectationsManifest(expectations, campusId) {
	if (!Number.isSafeInteger(campusId) || campusId <= 0 || expectations?.campusId !== campusId) {
		throw new Error('Case manifest campus scope does not match the measured campus.');
	}
	if (typeof expectations.datasetId !== 'string'
		|| !/^[A-Za-z0-9_-]{1,32}$/.test(expectations.datasetId)
		|| typeof expectations.fixtureRunId !== 'string'
		|| !/^[A-Za-z0-9_-]{1,32}$/.test(expectations.fixtureRunId)
		|| expectations.campusName !== `PERF_ISSUE_193:${expectations.datasetId}`
		|| typeof expectations.region !== 'string') {
		throw new Error('Case manifest dataset identity is invalid.');
	}
	if (expectations.activeMemberCount !== 1000
		|| expectations.fixtureAccountCount !== 5
		|| expectations.fixtureChargeCount !== 35000
		|| expectations.sourceDuplicateCount !== 0) {
		throw new Error('Case manifest dataset shape is not exact.');
	}
	for (const field of [
		'crossCampusId',
		'requesterUserId',
		'dutyUserId',
		'targetUserId',
		'archivedMemberUserId',
		'fixtureAccountId',
		'foreignCoffeeAccountId',
		'crossCampusAccountId',
		'ownedCoffeeAccountId',
		'dutyOwnedCoffeeAccountId',
		'dutyHistoricalCoffeeAccountId',
	]) {
		if (!Number.isSafeInteger(expectations[field]) || expectations[field] <= 0) {
			throw new Error(`Case manifest ID is invalid: ${field}.`);
		}
	}
	if (expectations.requesterUserId === expectations.dutyUserId
		|| expectations.archivedMemberUserId !== expectations.targetUserId
		|| typeof expectations.targetKeyword !== 'string'
		|| expectations.targetKeyword.length === 0) {
		throw new Error('Case manifest requester or target identity is invalid.');
	}
	const actualNames = Object.keys(expectations.cases ?? {}).sort();
	if (!semanticEqual(actualNames, [...REQUEST_CASE_NAMES].sort())) {
		throw new Error('Case manifest must contain exactly all 16 measured request cases.');
	}
	for (const requestCase of buildRequestCases(expectations, campusId)) {
		const expected = expectations.cases[requestCase.name];
		validateAggregateExpectation(expected, {exactKeys: true});
		if (expected.page !== requestCase.query.page || expected.size !== requestCase.query.size) {
			throw new Error(`${requestCase.name} expected pagination does not match the request.`);
		}
	}
	const archiveNames = ['admin_archive_default', 'admin_archive_included', 'my_archive_default', 'my_archive_included'];
	assertExactKeys(expectations.archiveCases, archiveNames, 'archive cases');
	for (const name of archiveNames) {
		validateAggregateExpectation(expectations.archiveCases[name], {identityRequired: true, exactKeys: true});
		validateExpectedIdentity(expectations.archiveCases[name], expectations);
	}
	validateArchiveTerminalDelta(expectations.archiveCases.admin_archive_default,
		expectations.archiveCases.admin_archive_included, {
			unpaidAmount: 6011, paidAmount: 6012, waivedAmount: 6013, canceledAmount: 6014,
		}, {
			paidAmount: 6015, waivedAmount: 6016, canceledAmount: 6017,
		}, 'admin archive');
	validateArchiveTerminalDelta(expectations.archiveCases.my_archive_default,
		expectations.archiveCases.my_archive_included, {
			unpaidAmount: 3011, paidAmount: 3012, waivedAmount: 3013, canceledAmount: 3014,
		}, {
			paidAmount: 3015, waivedAmount: 3016, canceledAmount: 3017,
		}, 'my archive');
	const dutyNames = [
		'duty_owned_accounts_visible',
		'duty_owned_account_filter_visible',
		'duty_foreign_account_hidden',
		'duty_member_detail_owned_only',
	];
	assertExactKeys(expectations.dutyScope, dutyNames, 'duty scope');
	for (const name of dutyNames.slice(0, 2)) {
		const expected = expectations.dutyScope[name];
		validateAggregateExpectation(expected, {identityRequired: true, statusRequired: true, exactKeys: true});
		validateExpectedIdentity(expected, expectations);
		if (expected.status !== 200) {
			throw new Error(`${name} must expect HTTP 200.`);
		}
	}
	assertExactKeys(expectations.dutyScope.duty_foreign_account_hidden, ['status'], 'foreign duty scope');
	if (expectations.dutyScope.duty_foreign_account_hidden.status !== 403) {
		throw new Error('Foreign duty account must expect HTTP 403.');
	}
	validateMemberDetailExpectation(expectations.dutyScope.duty_member_detail_owned_only);
	validateExpectedIdentity(expectations.dutyScope.duty_member_detail_owned_only, expectations);
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

function validateAggregateExpectation(
	expected,
	{identityRequired = false, statusRequired = false, exactKeys = false} = {},
) {
	if (!expected || !Array.isArray(expected.memberRows)) {
		throw new Error('Aggregate expectation is incomplete.');
	}
	const keys = ['summary', 'memberRows', 'page', 'size', 'totalElements', 'totalPages'];
	if (identityRequired) {
		keys.push('campusId', 'campusName', 'region');
	}
	if (statusRequired) {
		keys.push('status');
	}
	if (exactKeys) {
		assertExactKeys(expected, keys, 'aggregate expectation');
	}
	validateSummary(expected.summary);
	if (identityRequired) {
		validateIdentity(expected);
	}
	if (statusRequired && (!Number.isSafeInteger(expected.status) || expected.status < 100 || expected.status > 599)) {
		throw new Error('Aggregate HTTP status is invalid.');
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
	const expectedPageSize = Math.min(expected.size, Math.max(expected.totalElements - expected.page * expected.size, 0));
	if (expected.memberRows.length !== expectedPageSize
		|| expected.totalPages !== Math.ceil(expected.totalElements / expected.size)) {
		throw new Error('Aggregate pagination cardinality is inconsistent.');
	}
	const userIds = new Set();
	for (const row of expected.memberRows) {
		assertExactKeys(row, [
			'userId', 'name', 'email', 'totalAmount', 'unpaidAmount', 'paidAmount', 'waivedAmount', 'canceledAmount',
		], 'member aggregate row');
		const amounts = [row.totalAmount, row.unpaidAmount, row.paidAmount, row.waivedAmount, row.canceledAmount];
		if (!Number.isSafeInteger(row.userId)
			|| row.userId <= 0
			|| userIds.has(row.userId)
			|| typeof row.name !== 'string'
			|| typeof row.email !== 'string'
			|| amounts.some((value) => !Number.isSafeInteger(value) || value < 0)
			|| row.totalAmount !== amounts.slice(1).reduce((sum, value) => sum + value, 0)) {
			throw new Error('Invalid member aggregate expectation.');
		}
		userIds.add(row.userId);
	}
}

function validateMemberDetailExpectation(expected) {
	assertExactKeys(expected, [
		'status', 'campusId', 'campusName', 'region', 'userId', 'name', 'email', 'summary', 'items',
		'page', 'size', 'totalElements', 'totalPages',
	], 'member-detail expectation');
	validateIdentity(expected);
	if (expected.status !== 200
		|| !Number.isSafeInteger(expected.userId)
		|| expected.userId <= 0
		|| typeof expected.name !== 'string'
		|| typeof expected.email !== 'string'
		|| !Array.isArray(expected.items)) {
		throw new Error('Member-detail identity is invalid.');
	}
	validateSummary(expected.summary);
	for (const field of ['page', 'size', 'totalElements', 'totalPages']) {
		if (!Number.isSafeInteger(expected[field]) || expected[field] < 0) {
			throw new Error(`Invalid member-detail pagination metadata: ${field}.`);
		}
	}
	if (expected.size <= 0
		|| expected.totalPages !== Math.ceil(expected.totalElements / expected.size)
		|| expected.items.length !== Math.min(expected.size, Math.max(expected.totalElements - expected.page * expected.size, 0))) {
		throw new Error('Member-detail pagination cardinality is inconsistent.');
	}
	const itemIds = new Set();
	let previous = null;
	for (const item of expected.items) {
		validateChargeItemExpectation(item);
		if (itemIds.has(item.id)) {
			throw new Error('Member-detail item IDs must be unique.');
		}
		itemIds.add(item.id);
		const sortInstant = canonicalInstant(item._sortCreatedAt, false);
		if (previous && (sortInstant > previous.sortInstant
			|| (sortInstant === previous.sortInstant && item.id >= previous.id))) {
			throw new Error('Member-detail items are not in createdAt desc, id desc order.');
		}
		previous = {sortInstant, id: item.id};
	}
}

function validateChargeItemExpectation(item) {
	assertExactKeys(item, [
		'id', 'paymentCategory', 'title', 'reason', 'amount', 'status', 'dueDate', 'paidAt', 'account', 'source',
		'_sortCreatedAt',
	], 'member-detail item');
	assertExactKeys(item.account, ['paymentAccountId', 'bankName', 'accountNumber', 'accountHolder'], 'charge account');
	assertExactKeys(item.source, ['sourceType', 'sourceId'], 'charge source');
	if (!Number.isSafeInteger(item.id) || item.id <= 0
		|| item.paymentCategory !== 'COFFEE'
		|| typeof item.title !== 'string'
		|| (item.reason !== null && typeof item.reason !== 'string')
		|| !Number.isSafeInteger(item.amount) || item.amount < 0
		|| !['UNPAID', 'PAID', 'WAIVED', 'CANCELED'].includes(item.status)
		|| (item.dueDate !== null && !/^\d{4}-\d{2}-\d{2}$/.test(item.dueDate))
		|| !Number.isSafeInteger(item.account.paymentAccountId) || item.account.paymentAccountId <= 0
		|| [item.account.bankName, item.account.accountNumber, item.account.accountHolder]
			.some((value) => typeof value !== 'string')
		|| item.source.sourceType !== 'POLL_RESPONSE'
		|| !Number.isSafeInteger(item.source.sourceId) || item.source.sourceId <= 0) {
		throw new Error('Member-detail item schema is invalid.');
	}
	canonicalInstant(item.paidAt, true);
	canonicalInstant(item._sortCreatedAt, false);
}

function validateSummary(summary) {
	assertExactKeys(summary, ['totalAmount', 'unpaidAmount', 'paidAmount', 'waivedAmount', 'canceledAmount'], 'amount summary');
	const amounts = [summary.totalAmount, summary.unpaidAmount, summary.paidAmount, summary.waivedAmount, summary.canceledAmount];
	if (amounts.some((value) => !Number.isSafeInteger(value) || value < 0)
		|| summary.totalAmount !== amounts.slice(1).reduce((sum, value) => sum + value, 0)) {
		throw new Error('Amount summary is invalid.');
	}
}

function validateIdentity(value) {
	if (!Number.isSafeInteger(value.campusId) || value.campusId <= 0
		|| typeof value.campusName !== 'string'
		|| typeof value.region !== 'string') {
		throw new Error('Campus identity is invalid.');
	}
}

function validateExpectedIdentity(expected, manifest) {
	if (expected.campusId !== manifest.campusId
		|| expected.campusName !== manifest.campusName
		|| expected.region !== manifest.region) {
		throw new Error('Expected campus identity does not match the dataset manifest.');
	}
}

function validateArchiveTerminalDelta(defaultCase, includedCase, defaultAmounts, archivedAmounts, label) {
	const expectedDefault = {
		totalAmount: Object.values(defaultAmounts).reduce((sum, amount) => sum + amount, 0),
		...defaultAmounts,
	};
	const expectedIncluded = {
		totalAmount: expectedDefault.totalAmount + Object.values(archivedAmounts).reduce((sum, amount) => sum + amount, 0),
		unpaidAmount: defaultAmounts.unpaidAmount,
		paidAmount: defaultAmounts.paidAmount + archivedAmounts.paidAmount,
		waivedAmount: defaultAmounts.waivedAmount + archivedAmounts.waivedAmount,
		canceledAmount: defaultAmounts.canceledAmount + archivedAmounts.canceledAmount,
	};
	if (!semanticEqual(defaultCase.summary, expectedDefault)
		|| !semanticEqual(includedCase.summary, expectedIncluded)
		|| defaultCase.totalElements !== 1
		|| includedCase.totalElements !== 1
		|| defaultCase.memberRows.length !== 1
		|| includedCase.memberRows.length !== 1
		|| !semanticEqual(defaultCase.memberRows[0], {
			userId: defaultCase.memberRows[0].userId,
			name: defaultCase.memberRows[0].name,
			email: defaultCase.memberRows[0].email,
			...expectedDefault,
		})
		|| !semanticEqual(includedCase.memberRows[0], {
			userId: defaultCase.memberRows[0].userId,
			name: defaultCase.memberRows[0].name,
			email: defaultCase.memberRows[0].email,
			...expectedIncluded,
		})) {
		throw new Error(`${label} terminal cutoff expectation is invalid.`);
	}
}

function identityMatches(actual, expected) {
	return actual?.campusId === expected.campusId
		&& actual?.campusName === expected.campusName
		&& actual?.region === expected.region;
}

function assertExactKeys(value, keys, label) {
	if (value === null || typeof value !== 'object' || Array.isArray(value)
		|| !semanticEqual(Object.keys(value).sort(), [...keys].sort())) {
		throw new Error(`${label} keys are invalid.`);
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

function toComparableChargeItem(item) {
	return {
		id: item?.id,
		paymentCategory: item?.paymentCategory,
		title: item?.title,
		reason: item?.reason ?? null,
		amount: item?.amount,
		status: item?.status,
		dueDate: item?.dueDate ?? null,
		paidAt: canonicalInstant(item?.paidAt ?? null, true),
		account: {
			paymentAccountId: item?.account?.paymentAccountId,
			bankName: item?.account?.bankName,
			accountNumber: item?.account?.accountNumber,
			accountHolder: item?.account?.accountHolder,
		},
		source: {
			sourceType: item?.source?.sourceType,
			sourceId: item?.source?.sourceId,
		},
	};
}

function toExpectedChargeItem(item) {
	const {_sortCreatedAt, ...publicItem} = item;
	return {...publicItem, paidAt: canonicalInstant(publicItem.paidAt, true)};
}

function canonicalInstant(value, nullable) {
	if (value === null && nullable) {
		return null;
	}
	if (typeof value !== 'string') {
		throw new Error('Instant must be an ISO-8601 string or allowed null.');
	}
	const match = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.(\d{1,9}))?(Z|([+-])(\d{2}):(\d{2}))$/.exec(value);
	if (!match) {
		throw new Error('Instant is invalid.');
	}
	const year = Number(match[1]);
	const month = Number(match[2]);
	const day = Number(match[3]);
	const hour = Number(match[4]);
	const minute = Number(match[5]);
	const second = Number(match[6]);
	const offsetHour = match[10] === undefined ? 0 : Number(match[10]);
	const offsetMinute = match[11] === undefined ? 0 : Number(match[11]);
	if (month < 1 || month > 12
		|| day < 1 || day > daysInMonth(year, month)
		|| hour > 23
		|| minute > 59
		|| second > 59
		|| offsetHour > 23
		|| offsetMinute > 59) {
		throw new Error('Instant is invalid.');
	}
	const offsetSign = match[9] === '-' ? -1 : 1;
	const offsetSeconds = offsetSign * (offsetHour * 3600 + offsetMinute * 60);
	const localSeconds = BigInt(daysFromCivil(year, month, day)) * 86400n
		+ BigInt(hour * 3600 + minute * 60 + second);
	const fractionNanos = BigInt((match[7] ?? '').padEnd(9, '0'));
	return (localSeconds - BigInt(offsetSeconds)) * 1000000000n + fractionNanos;
}

function daysInMonth(year, month) {
	if (month === 2) {
		return year % 4 === 0 && (year % 100 !== 0 || year % 400 === 0) ? 29 : 28;
	}
	return [4, 6, 9, 11].includes(month) ? 30 : 31;
}

function daysFromCivil(year, month, day) {
	const adjustedYear = year - (month <= 2 ? 1 : 0);
	const era = Math.floor(adjustedYear / 400);
	const yearOfEra = adjustedYear - era * 400;
	const adjustedMonth = month + (month > 2 ? -3 : 9);
	const dayOfYear = Math.floor((153 * adjustedMonth + 2) / 5) + day - 1;
	const dayOfEra = yearOfEra * 365 + Math.floor(yearOfEra / 4) - Math.floor(yearOfEra / 100) + dayOfYear;
	return era * 146097 + dayOfEra - 719468;
}
