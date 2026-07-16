export const FIXTURE_CONTRACT = Object.freeze({
	datasetId: 'issue-196-prayer-poll-list-v2',
	fixtureRunIdRequired: true,
	existingRowsMayBeUpdatedOrDeleted: false,
	primaryCampus: Object.freeze({
		activeMemberCount: 1000,
	}),
	isolationCampus: Object.freeze({
		activeMemberCount: 50,
	}),
	currentDevelop: Object.freeze({
		sourceRevision: '6796ed146244d8f3f5b5dd7048ebe16865084a97',
		flywayVersion: '11',
		publicApplicationTableCount: 27,
		genericPollListPaginated: false,
		mealManagementMaxPageSize: 100,
		mealArchiveDays: 90,
		deterministicMealSort: 'id,desc',
	}),
	prayer: Object.freeze({
		groupCount: 40,
		membersPerGroup: 25,
		submissionCount: 800,
		unsubmittedCount: 200,
	}),
	polls: Object.freeze({
		optionCount: 5,
		responseCount: 800,
		missingMemberCount: 200,
		commentCount: 200,
		templateCount: 40,
		optionsPerTemplate: 8,
		manageableByMe: Object.freeze({
			custom: Object.freeze({ admin: true, member: false, coffeeCreator: false, otherCoffeeDuty: false, mealDuty: false }),
			coffee: Object.freeze({ admin: false, member: false, coffeeCreator: true, otherCoffeeDuty: false, mealDuty: false }),
			meal: Object.freeze({ admin: false, member: false, coffeeCreator: false, otherCoffeeDuty: false, mealDuty: true }),
		}),
		visibilityCases: Object.freeze([
			Object.freeze({ key: 'open', status: 'OPEN', memberVisible: true, adminVisible: true }),
			Object.freeze({ key: 'closed_member_visible', status: 'CLOSED', memberVisible: true, adminVisible: true }),
			Object.freeze({ key: 'closed_admin_only', status: 'CLOSED', memberVisible: false, adminVisible: true }),
			Object.freeze({ key: 'closed_expired', status: 'CLOSED', memberVisible: false, adminVisible: false }),
			Object.freeze({ key: 'scheduled_future', status: 'SCHEDULED', memberVisible: false, adminVisible: false }),
		]),
	}),
});

export const MODE_ENDPOINTS = Object.freeze({
	prayer: Object.freeze([
		'prayer_current_season',
		'prayer_groups',
		'prayer_assignable',
		'prayer_weekly_board_admin',
		'prayer_weekly_board_member',
	]),
	'poll-member': Object.freeze([
		'poll_member_list',
		'poll_member_detail',
		'poll_member_results',
		'poll_member_comments',
		'poll_member_cross_campus_detail',
		'poll_member_isolation_campus_detail',
	]),
	'poll-admin': Object.freeze([
		'poll_admin_list',
		'poll_admin_detail',
		'poll_admin_results',
		'poll_admin_comments',
		'poll_admin_missing_members',
		'poll_admin_template_list',
		'poll_admin_template_detail',
		'poll_admin_cross_campus_detail',
	]),
	'poll-duty': Object.freeze([
		'poll_coffee_creator_list',
		'poll_other_coffee_duty_list',
		'poll_meal_duty_list',
		'poll_coffee_creator_detail',
		'poll_meal_duty_detail',
		'poll_meal_management_default',
		'poll_meal_management_archive',
		'poll_meal_management_forbidden',
	]),
});

export function validateFixtureRunId(value) {
	if (typeof value !== 'string' || !/^[a-z0-9][a-z0-9_-]{7,31}$/.test(value)) {
		throw new Error('FIXTURE_RUN_ID must be 8-32 lowercase characters using letters, numbers, underscore, or hyphen.');
	}
	return value;
}

export function currentMonday(now = new Date()) {
	const parts = Object.fromEntries(new Intl.DateTimeFormat('en', {
		timeZone: 'Asia/Seoul',
		year: 'numeric',
		month: '2-digit',
		day: '2-digit',
	}).formatToParts(now)
		.filter((part) => part.type !== 'literal')
		.map((part) => [part.type, part.value]));
	const date = new Date(Date.UTC(Number(parts.year), Number(parts.month) - 1, Number(parts.day)));
	const day = date.getUTCDay() || 7;
	date.setUTCDate(date.getUTCDate() - day + 1);
	return date.toISOString().slice(0, 10);
}
