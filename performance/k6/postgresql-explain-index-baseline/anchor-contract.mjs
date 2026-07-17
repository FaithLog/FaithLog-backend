import { parsePageSize } from './runtime-contract.mjs';

const INTEGER_FIELDS = [
	'campus_id', 'poll_id', 'meal_poll_id', 'member_user_id', 'payment_account_id',
	'prayer_season_id', 'prayer_week_id',
];
const RFC3339 = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2}):(\d{2})(?:\.\d+)?(Z|([+-])(\d{2}):(\d{2}))$/;

export function validateAnchors(anchors) {
	if (!anchors || typeof anchors !== 'object' || Array.isArray(anchors)) {
		throw new Error('CROSS_ISSUE_REPORT must include anchors.');
	}
	const variables = {};
	for (const field of INTEGER_FIELDS) {
		const value = Number(anchors[field]);
		if (!Number.isSafeInteger(value) || value <= 0) {
			throw new Error(`anchors.${field} must be a positive safe integer.`);
		}
		variables[field] = String(value);
	}
	const pageOffset = Number(anchors.page_offset);
	if (!Number.isSafeInteger(pageOffset) || pageOffset < 0) {
		throw new Error('anchors.page_offset must be a non-negative safe integer.');
	}
	variables.page_offset = String(pageOffset);
	variables.page_size = String(parsePageSize(anchors.page_size));
	validateMonday(anchors.week_start_date);
	variables.week_start_date = anchors.week_start_date;
	for (const field of ['archive_cutoff', 'stale_before', 'range_start', 'range_end']) {
		if (typeof anchors[field] !== 'string' || !isStrictRfc3339(anchors[field])) {
			throw new Error(`anchors.${field} must be an explicit Z/offset RFC3339 instant.`);
		}
		variables[field] = anchors[field];
	}
	if (Date.parse(anchors.range_start) >= Date.parse(anchors.range_end)) {
		throw new Error('anchors.range_start must be earlier than anchors.range_end.');
	}
	if (typeof anchors.keyword_pattern !== 'string' || anchors.keyword_pattern.length > 200) {
		throw new Error('anchors.keyword_pattern must be a string of at most 200 characters.');
	}
	variables.keyword_pattern = anchors.keyword_pattern;
	validateExpectedState(anchors.expected_state);
	return variables;
}

function isStrictRfc3339(value) {
	const match = RFC3339.exec(value);
	if (!match) return false;
	const [, yearText, monthText, dayText, hourText, minuteText, secondText, , , offsetHourText, offsetMinuteText] = match;
	const [year, month, day, hour, minute, second] = [yearText, monthText, dayText, hourText, minuteText, secondText].map(Number);
	if (month < 1 || month > 12 || hour > 23 || minute > 59 || second > 59) return false;
	const daysInMonth = new Date(Date.UTC(year, month, 0)).getUTCDate();
	if (day < 1 || day > daysInMonth) return false;
	if (offsetHourText !== undefined
		&& (Number(offsetHourText) > 23 || Number(offsetMinuteText) > 59)) return false;
	return Number.isFinite(Date.parse(value));
}

export function validateAnchorsAgainstArtifacts(anchors, artifacts) {
	for (const entry of artifacts) {
		if (stableStringify(entry.artifact?.expectedAnchors) !== stableStringify(anchors)) {
			throw new Error(`Issue ${entry.issueNumber} expected anchor identity does not exactly match CROSS_ISSUE_REPORT anchors.`);
		}
	}
}

export function validateAnchorPreflight(result, expectedState) {
	const requiredTrue = [
		'campusExists', 'memberInCampus', 'pollInCampus', 'mealPollInCampus',
		'paymentAccountInCampus', 'prayerSeasonInCampus', 'prayerWeekInSeason',
		'pollCreatorActiveMember', 'pollCreatorActiveCoffeeDuty',
		'pollPaymentAccountOwnedActiveCoffee', 'pollConfigurationConsistent',
	];
	const exactCounts = {
		memberCount: 1000, pollCount: 1, mealPollCount: 1, paymentAccountCount: 1,
		prayerSeasonCount: 1, prayerWeekCount: 1,
		coffeeTemplateAccountNeutralityViolationCount: 0,
		coffeeTemplateInconsistentActiveCount: 0,
	};
	const reasons = [];
	for (const field of requiredTrue) {
		if (result?.[field] !== true) reasons.push(`anchor-relation-mismatch:${field}`);
	}
	for (const [field, expected] of Object.entries(exactCounts)) {
		if (result?.[field] !== expected) reasons.push(`anchor-cardinality-mismatch:${field}:expected-${expected}`);
	}
	const stateFields = {
		member_status: 'memberStatus', poll_status: 'pollStatus', poll_title: 'pollTitle',
		meal_poll_status: 'mealPollStatus', meal_poll_title: 'mealPollTitle',
		payment_account_is_active: 'paymentAccountIsActive',
		payment_account_owner_user_id: 'paymentAccountOwnerUserId',
		payment_account_nickname: 'paymentAccountNickname', prayer_season_status: 'prayerSeasonStatus',
		prayer_season_name: 'prayerSeasonName', prayer_week_status: 'prayerWeekStatus',
	};
	for (const [expectedField, actualField] of Object.entries(stateFields)) {
		if (result?.[actualField] !== expectedState?.[expectedField]) {
			reasons.push(`anchor-state-mismatch:${expectedField}`);
		}
	}
	return { adoptable: reasons.length === 0, reasons };
}

function validateExpectedState(state) {
	const stringFields = [
		'member_status', 'poll_status', 'poll_title', 'meal_poll_status', 'meal_poll_title',
		'payment_account_nickname', 'prayer_season_status', 'prayer_season_name', 'prayer_week_status',
	];
	if (!state || typeof state !== 'object' || Array.isArray(state)
		|| stringFields.some((field) => typeof state[field] !== 'string' || state[field].length === 0)
		|| state.payment_account_is_active !== true
		|| !(state.payment_account_owner_user_id === null
			|| (Number.isSafeInteger(state.payment_account_owner_user_id) && state.payment_account_owner_user_id > 0))) {
		throw new Error('anchors.expected_state must contain accepted fixture owner/status/title/name identity.');
	}
}

function validateMonday(value) {
	if (typeof value !== 'string' || !/^\d{4}-\d{2}-\d{2}$/.test(value)) {
		throw new Error('anchors.week_start_date must be a valid Monday in YYYY-MM-DD form.');
	}
	const [year, month, day] = value.split('-').map(Number);
	const date = new Date(Date.UTC(year, month - 1, day));
	if (date.getUTCFullYear() !== year || date.getUTCMonth() !== month - 1
		|| date.getUTCDate() !== day || date.getUTCDay() !== 1) {
		throw new Error('anchors.week_start_date must be a real calendar Monday.');
	}
}

function stableStringify(value) {
	if (Array.isArray(value)) return `[${value.map(stableStringify).join(',')}]`;
	if (value && typeof value === 'object') {
		return `{${Object.keys(value).sort().map((key) => `${JSON.stringify(key)}:${stableStringify(value[key])}`).join(',')}}`;
	}
	return JSON.stringify(value);
}
