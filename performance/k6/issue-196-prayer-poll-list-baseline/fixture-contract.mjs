export const FIXTURE_CONTRACT = Object.freeze({
	datasetId: 'issue-196-prayer-poll-list-v1',
	fixtureRunIdRequired: true,
	existingRowsMayBeUpdatedOrDeleted: false,
	primaryCampus: Object.freeze({
		activeMemberCount: 1000,
	}),
	isolationCampus: Object.freeze({
		activeMemberCount: 50,
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
});

export function validateFixtureRunId(value) {
	if (typeof value !== 'string' || !/^[a-z0-9][a-z0-9_-]{7,31}$/i.test(value)) {
		throw new Error('FIXTURE_RUN_ID must be 8-32 characters using letters, numbers, underscore, or hyphen.');
	}
	return value;
}

export function currentMonday(now = new Date()) {
	const date = new Date(Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate()));
	const day = date.getUTCDay() || 7;
	date.setUTCDate(date.getUTCDate() - day + 1);
	return date.toISOString().slice(0, 10);
}
