import http from 'k6/http';
import { check, fail } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

const BASE_URL = requiredEnv('BASE_URL').replace(/\/$/, '');
const PHASE = requiredEnv('PHASE');
const MODE = __ENV.MODE;
const ENDPOINT = __ENV.ENDPOINT;
const VUS = Number(__ENV.VUS);
const DURATION = __ENV.DURATION;
const FIXTURE_MANIFEST = requiredEnv('FIXTURE_MANIFEST');
requiredEnv('CREDENTIALS_FILE');
const RUNTIME_CREDENTIALS = JSON.parse(open(__ENV.CREDENTIALS_FILE));
const EXPECTED_SOURCE_REVISION = requiredEnv('EXPECTED_SOURCE_REVISION');
const EXPECTED_APP_SERVICE = requiredEnv('EXPECTED_APP_SERVICE');
const EXPECTED_DB_SERVICE = requiredEnv('EXPECTED_DB_SERVICE');
const EXPECTED_REDIS_SERVICE = requiredEnv('EXPECTED_REDIS_SERVICE');
const EXPECTED_APP_IMAGE = requiredEnv('EXPECTED_APP_IMAGE');
const EXPECTED_APP_IMAGE_ID = requiredEnv('EXPECTED_APP_IMAGE_ID');
const EXPECTED_DB_IMAGE = requiredEnv('EXPECTED_DB_IMAGE');
const EXPECTED_DB_IMAGE_ID = requiredEnv('EXPECTED_DB_IMAGE_ID');
const EXPECTED_REDIS_IMAGE = requiredEnv('EXPECTED_REDIS_IMAGE');
const EXPECTED_REDIS_IMAGE_ID = requiredEnv('EXPECTED_REDIS_IMAGE_ID');

function requiredEnv(name) {
	const value = __ENV[name];
	if (!value) throw new Error(`${name} is required at runtime.`);
	return value;
}

if (!FIXTURE_MANIFEST) {
	fail('FIXTURE_MANIFEST is required.');
}
const manifest = JSON.parse(open(FIXTURE_MANIFEST));
if (!manifest.shapedAt) {
	fail('Fixture manifest is not shaped. Run shape-fixture.sh before k6.');
}
if (!Number.isInteger(VUS) || VUS < 1 || !DURATION) {
	fail('VUS and DURATION must be explicitly supplied by the approved measurement run.');
}
if (!['warmup', 'measured'].includes(PHASE)) {
	fail('PHASE must be warmup or measured.');
}
validateRuntimeCredentials();

const metricName = ENDPOINT ? ENDPOINT.replace(/-/g, '_') : 'invalid_endpoint';
const endpointDuration = new Trend(`endpoint_${metricName}_duration`, true);
const endpointRequests = new Counter(`endpoint_${metricName}_requests`);
const endpointFailures = new Rate(`endpoint_${metricName}_failures`);

export const options = {
	scenarios: {
		endpoint_baseline: {
			executor: 'constant-vus',
			vus: VUS,
			duration: DURATION,
		},
	},
	summaryTrendStats: ['avg', 'min', 'med', 'p(50)', 'p(90)', 'p(95)', 'p(99)', 'max'],
	thresholds: {
		[`endpoint_${metricName}_failures`]: ['rate==0'],
	},
};

export function setup() {
	guardTarget();
	guardRuntimeManifest();
	const config = endpointConfig();
	if (config.mode !== MODE) {
		fail(`ENDPOINT=${ENDPOINT} belongs to ${config.mode}, not MODE=${MODE}.`);
	}
	return { fixtureRunId: manifest.fixtureRunId, phase: PHASE, mode: MODE, endpoint: ENDPOINT };
}

function guardRuntimeManifest() {
	const runtime = manifest.composeRuntime;
	if (!runtime || runtime.sourceRevision !== EXPECTED_SOURCE_REVISION
		|| runtime.appService !== EXPECTED_APP_SERVICE || runtime.dbService !== EXPECTED_DB_SERVICE
		|| runtime.redisService !== EXPECTED_REDIS_SERVICE || runtime.appImage !== EXPECTED_APP_IMAGE
		|| runtime.appImageId !== EXPECTED_APP_IMAGE_ID || runtime.dbImage !== EXPECTED_DB_IMAGE
		|| runtime.dbImageId !== EXPECTED_DB_IMAGE_ID || runtime.redisImage !== EXPECTED_REDIS_IMAGE
		|| runtime.redisImageId !== EXPECTED_REDIS_IMAGE_ID) {
		fail('Direct k6 entrypoint runtime identity does not match the immutable fixture manifest.');
	}
}

export default function () {
	const config = endpointConfig();
	const token = RUNTIME_CREDENTIALS.tokens[config.actor];
	if (!token) fail(`Missing token for actor=${config.actor}.`);
	const response = http.get(`${BASE_URL}${config.path}`, {
		headers: { Authorization: `Bearer ${token}` },
		tags: { name: ENDPOINT, mode: MODE, fixtureRunId: manifest.fixtureRunId },
	});
	endpointRequests.add(1);
	endpointDuration.add(response.timings.duration);
	const payload = parseJson(response);
	let valid = false;
	try {
		valid = config.validate(response.status, payload) === true;
	} catch {
		valid = false;
	}
	endpointFailures.add(!valid);
	check(response, {
		[`${ENDPOINT} status and correctness contract`]: () => valid,
	});
}

function validateRuntimeCredentials() {
	const rootKeys = ['fixtureRunId', 'phase', 'schemaVersion', 'tokens'];
	const tokenKeys = ['admin', 'coffeeCreator', 'mealDuty', 'member', 'otherCoffeeDuty'];
	if (!RUNTIME_CREDENTIALS || typeof RUNTIME_CREDENTIALS !== 'object' || Array.isArray(RUNTIME_CREDENTIALS)
		|| Object.keys(RUNTIME_CREDENTIALS).sort().join(',') !== rootKeys.join(',')
		|| RUNTIME_CREDENTIALS.schemaVersion !== 1
		|| RUNTIME_CREDENTIALS.fixtureRunId !== manifest.fixtureRunId
		|| RUNTIME_CREDENTIALS.phase !== PHASE
		|| !RUNTIME_CREDENTIALS.tokens || typeof RUNTIME_CREDENTIALS.tokens !== 'object' || Array.isArray(RUNTIME_CREDENTIALS.tokens)
		|| Object.keys(RUNTIME_CREDENTIALS.tokens).sort().join(',') !== tokenKeys.join(',')
		|| !tokenKeys.every((key) => typeof RUNTIME_CREDENTIALS.tokens[key] === 'string'
			&& RUNTIME_CREDENTIALS.tokens[key].length > 0)) {
		fail('CREDENTIALS_FILE must contain the exact five-token runtime schema for this fixture and phase.');
	}
}

function endpointConfig() {
	const campusId = manifest.primaryCampus.campusId;
	const seasonId = manifest.prayer.seasonId;
	const weekStartDate = manifest.weekStartDate;
	const memberPollId = manifest.polls.byKey.open.id;
	const adminPollId = manifest.polls.byKey.closed_admin_only.id;
	const isolationPollId = manifest.polls.isolationPollId;
	const templateId = manifest.polls.templateDetailId;
	const configs = {
		prayer_current_season: {
			mode: 'prayer', actor: 'admin',
			path: `/api/v1/admin/campuses/${campusId}/prayer-seasons/current`,
			validate: validatePrayerCurrentSeason,
		},
		prayer_groups: {
			mode: 'prayer', actor: 'admin',
			path: `/api/v1/admin/prayer-seasons/${seasonId}/groups`,
			validate: validatePrayerGroups,
		},
		prayer_assignable: {
			mode: 'prayer', actor: 'admin',
			path: `/api/v1/admin/prayer-seasons/${seasonId}/members/assignable`,
			validate: validatePrayerAssignable,
		},
		prayer_weekly_board_admin: {
			mode: 'prayer', actor: 'admin',
			path: `/api/v1/campuses/${campusId}/prayers/weeks/${weekStartDate}`,
			validate: (status, payload) => validatePrayerBoard(status, payload, true),
		},
		prayer_weekly_board_member: {
			mode: 'prayer', actor: 'member',
			path: `/api/v1/campuses/${campusId}/prayers/weeks/${weekStartDate}`,
			validate: (status, payload) => validatePrayerBoard(status, payload, false),
		},
		poll_member_list: {
			mode: 'poll-member', actor: 'member',
			path: `/api/v1/campuses/${campusId}/polls`,
			validate: (status, payload) => validatePollList(status, payload, 'member'),
		},
		poll_member_detail: {
			mode: 'poll-member', actor: 'member',
			path: pollDetailPath(campusId, memberPollId),
			validate: (status, payload) => validatePollDetail(status, payload, 'open', true, 'member'),
		},
		poll_member_results: {
			mode: 'poll-member', actor: 'member',
			path: pollResultsPath(campusId, memberPollId),
			validate: (status, payload) => validatePollResults(status, payload, 'open', false),
		},
		poll_member_comments: {
			mode: 'poll-member', actor: 'member',
			path: pollCommentsPath(campusId, memberPollId),
			validate: validatePollComments,
		},
		poll_member_cross_campus_detail: {
			mode: 'poll-member', actor: 'member',
			path: pollDetailPath(campusId, isolationPollId),
			validate: validatePollIsolation,
		},
		poll_member_isolation_campus_detail: {
			mode: 'poll-member', actor: 'member',
			path: pollDetailPath(manifest.isolationCampus.campusId, isolationPollId),
			validate: validatePollIsolationMembership,
		},
		poll_admin_list: {
			mode: 'poll-admin', actor: 'admin',
			path: `/api/v1/campuses/${campusId}/polls`,
			validate: (status, payload) => validatePollList(status, payload, 'admin'),
		},
		poll_admin_detail: {
			mode: 'poll-admin', actor: 'admin',
			path: pollDetailPath(campusId, adminPollId),
			validate: (status, payload) => validatePollDetail(status, payload, 'closed_admin_only', false, 'admin'),
		},
		poll_admin_results: {
			mode: 'poll-admin', actor: 'admin',
			path: pollResultsPath(campusId, adminPollId),
			validate: (status, payload) => validatePollResults(status, payload, 'closed_admin_only', true),
		},
		poll_admin_comments: {
			mode: 'poll-admin', actor: 'admin',
			path: pollCommentsPath(campusId, memberPollId),
			validate: validatePollComments,
		},
		poll_admin_missing_members: {
			mode: 'poll-admin', actor: 'admin',
			path: pollMissingMembersPath(campusId, adminPollId),
			validate: validatePollMissingMembers,
		},
		poll_admin_template_list: {
			mode: 'poll-admin', actor: 'admin',
			path: `/api/v1/admin/campuses/${campusId}/poll-templates`,
			validate: validatePollTemplateList,
		},
		poll_admin_template_detail: {
			mode: 'poll-admin', actor: 'admin',
			path: pollTemplateDetailPath(campusId, templateId),
			validate: validatePollTemplateDetail,
		},
		poll_admin_cross_campus_detail: {
			mode: 'poll-admin', actor: 'admin',
			path: pollDetailPath(campusId, isolationPollId),
			validate: validatePollIsolation,
		},
		poll_coffee_creator_list: {
			mode: 'poll-duty', actor: 'coffeeCreator', path: `/api/v1/campuses/${campusId}/polls`,
			validate: (status, payload) => validatePollList(status, payload, 'coffeeCreator'),
		},
		poll_other_coffee_duty_list: {
			mode: 'poll-duty', actor: 'otherCoffeeDuty', path: `/api/v1/campuses/${campusId}/polls`,
			validate: (status, payload) => validatePollList(status, payload, 'otherCoffeeDuty'),
		},
		poll_meal_duty_list: {
			mode: 'poll-duty', actor: 'mealDuty', path: `/api/v1/campuses/${campusId}/polls`,
			validate: (status, payload) => validatePollList(status, payload, 'mealDuty'),
		},
		poll_coffee_creator_detail: {
			mode: 'poll-duty', actor: 'coffeeCreator', path: pollDetailPath(campusId, manifest.polls.duty.coffee.id),
			validate: (status, payload) => validateDutyPollDetail(status, payload, 'coffee', 'coffeeCreator'),
		},
		poll_meal_duty_detail: {
			mode: 'poll-duty', actor: 'mealDuty', path: pollDetailPath(campusId, manifest.polls.duty.mealOpen.id),
			validate: (status, payload) => validateDutyPollDetail(status, payload, 'mealOpen', 'mealDuty'),
		},
		poll_meal_management_default: {
			mode: 'poll-duty', actor: 'mealDuty',
			path: `/api/v1/campuses/${campusId}/meal/polls?includeArchived=false&page=0&size=100&sort=id%2Cdesc`,
			validate: (status, payload) => validateMealManagement(status, payload, false),
		},
		poll_meal_management_archive: {
			mode: 'poll-duty', actor: 'mealDuty',
			path: `/api/v1/campuses/${campusId}/meal/polls?includeArchived=true&page=0&size=100&sort=id%2Cdesc`,
			validate: (status, payload) => validateMealManagement(status, payload, true),
		},
		poll_meal_management_forbidden: {
			mode: 'poll-duty', actor: 'member',
			path: `/api/v1/campuses/${campusId}/meal/polls?includeArchived=false&page=0&size=100&sort=id%2Cdesc`,
			validate: validateMealManagementForbidden,
		},
	};
	const config = configs[ENDPOINT];
	if (!config) {
		fail(`Unknown ENDPOINT=${ENDPOINT}.`);
	}
	return config;
}

function pollDetailPath(campusId, pollId) {
	return `/api/v1/campuses/${campusId}/polls/${pollId}`;
}

function pollResultsPath(campusId, pollId) {
	return `/api/v1/campuses/${campusId}/polls/${pollId}/results`;
}

function pollCommentsPath(campusId, pollId) {
	return `/api/v1/campuses/${campusId}/polls/${pollId}/comments`;
}

function pollMissingMembersPath(campusId, pollId) {
	return `/api/v1/admin/campuses/${campusId}/polls/${pollId}/missing-members`;
}

function pollTemplateDetailPath(campusId, templateId) {
	return `/api/v1/admin/campuses/${campusId}/poll-templates/${templateId}`;
}

function validatePrayerCurrentSeason(status, payload) {
	const data = successData(status, payload);
	return Boolean(data)
		&& data.seasonId === manifest.prayer.seasonId
		&& data.campusId === manifest.primaryCampus.campusId
		&& data.status === 'ACTIVE'
		&& data.endDate === null;
}

function validatePrayerGroups(status, payload) {
	const data = successData(status, payload);
	if (!Array.isArray(data) || data.length !== 40 || !assertAscending(data, (group) => [group.sortOrder, group.groupId])) {
		return false;
	}
	const isolation = new Set(manifest.prayer.isolationGroupIds);
	return data.every((group, index) => group.groupId === manifest.prayer.groups[index].groupId
		&& group.seasonId === manifest.prayer.seasonId
		&& group.members.length === 25
		&& sameIds(group.members.map((member) => member.userId), manifest.prayer.groups[index].memberUserIds)
		&& !isolation.has(group.groupId));
}

function validatePrayerAssignable(status, payload) {
	const data = successData(status, payload);
	const expectedIds = manifest.primaryCampus.members.map((member) => member.userId);
	const isolation = new Set(manifest.isolationCampus.members.map((member) => member.userId));
	return Array.isArray(data)
		&& data.length === 1000
		&& sameIds(data.map((member) => member.userId), expectedIds)
		&& data.every((member) => member.assignable === false && member.assignedGroupId !== null && !isolation.has(member.userId));
}

function validatePrayerBoard(status, payload, admin) {
	const data = successData(status, payload);
	if (!data || data.campusId !== manifest.primaryCampus.campusId
		|| data.currentSeason?.seasonId !== manifest.prayer.seasonId
		|| data.targetMemberCount !== 1000
		|| data.submittedCount !== 800
		|| data.groups.length !== 40
		|| !assertAscending(data.groups, (group) => [group.sortOrder, group.groupId])) {
		return false;
	}
	const members = data.groups.flatMap((group) => group.members);
	if (members.length !== 1000) {
		return false;
	}
	const submitted = new Set(manifest.prayer.submissionUserIds);
	const isolationUsers = new Set(manifest.isolationCampus.members.map((member) => member.userId));
	const exactGroups = data.groups.every((group, index) => (
		group.groupId === manifest.prayer.groups[index].groupId
		&& sameIds(group.members.map((member) => member.userId), manifest.prayer.groups[index].memberUserIds)
	));
	if (!exactGroups || members.some((member) => isolationUsers.has(member.userId) || member.submitted !== submitted.has(member.userId))) {
		return false;
	}
	if (admin) {
		return data.myGroupId === manifest.prayer.groups[0].groupId
			&& members.every((member) => member.editable === true);
	}
	const actorId = manifest.primaryCampus.memberActor.userId;
	const editableIds = members.filter((member) => member.editable).map((member) => member.userId);
	return data.myGroupId === manifest.prayer.memberActorGroupId
		&& sameIds(editableIds, [actorId]);
}

function validatePollList(status, payload, actor) {
	const data = successData(status, payload);
	if (!Array.isArray(data)) {
		return false;
	}
	const keys = actor === 'admin' ? manifest.polls.expectedAdminVisibleKeys : manifest.polls.expectedMemberVisibleKeys;
	const dutyPolls = [manifest.polls.duty.coffee, manifest.polls.duty.mealOpen];
	const expectedIds = [...keys.map((key) => manifest.polls.byKey[key].id), ...dutyPolls.map((poll) => poll.id)]
		.sort((left, right) => right - left);
	const actualIds = data.map((poll) => poll.id);
	const isolation = manifest.polls.isolationPollId;
	const hiddenIds = ['closed_expired', 'scheduled_future'].map((key) => manifest.polls.byKey[key].id);
	if (!sameIds(actualIds, expectedIds) || !assertDescending(actualIds)
		|| data.some((poll) => poll.campusId !== manifest.primaryCampus.campusId || poll.id === isolation || hiddenIds.includes(poll.id))) {
		return false;
	}
	return data.every((poll) => {
		if (Object.prototype.hasOwnProperty.call(poll, 'createdBy')) return false;
		const dutyKey = poll.id === manifest.polls.duty.coffee.id ? 'coffee'
			: poll.id === manifest.polls.duty.mealOpen.id ? 'meal' : null;
		if (dutyKey) {
			return poll.status === 'OPEN' && poll.responded === false
				&& poll.pollType === dutyKey.toUpperCase()
				&& poll.manageableByMe === manifest.polls.manageableByMe[dutyKey][actor];
		}
		const key = keys.find((candidate) => manifest.polls.byKey[candidate].id === poll.id);
		const expectedStatus = key === 'open' ? 'OPEN' : 'CLOSED';
		const expectedResponded = actor !== 'admin' && key === 'open';
		const expected = manifest.polls.byKey[key];
		return poll.status === expectedStatus
			&& poll.responded === expectedResponded
			&& poll.pollType === 'CUSTOM'
			&& poll.manageableByMe === manifest.polls.manageableByMe.custom[actor]
			&& sameInstant(poll.startsAt, expected.startsAt)
			&& sameInstant(poll.endsAt, expected.endsAt);
	});
}

function validatePollDetail(status, payload, key, expectMyResponse, actor) {
	const data = successData(status, payload);
	const expected = manifest.polls.byKey[key];
	return Boolean(data)
		&& data.id === expected.id
		&& data.campusId === manifest.primaryCampus.campusId
		&& data.status === (key === 'open' ? 'OPEN' : 'CLOSED')
		&& !Object.prototype.hasOwnProperty.call(data, 'createdBy')
		&& data.manageableByMe === manifest.polls.manageableByMe.custom[actor]
		&& sameInstant(data.startsAt, expected.startsAt)
		&& sameInstant(data.endsAt, expected.endsAt)
		&& sameIds(data.options.map((option) => option.id), expected.optionIds)
		&& assertAscending(data.options, (option) => [option.sortOrder, option.id])
		&& (expectMyResponse
			? sameIds(data.myResponse?.optionIds || [], [expected.optionIds[0]])
			: data.myResponse === null);
}

function validateDutyPollDetail(status, payload, key, actor) {
	const data = successData(status, payload);
	const expected = manifest.polls.duty[key];
	const typeKey = key === 'coffee' ? 'coffee' : 'meal';
	return Boolean(data) && data.id === expected.id && data.campusId === manifest.primaryCampus.campusId
		&& data.pollType === typeKey.toUpperCase() && data.status === 'OPEN'
		&& !Object.prototype.hasOwnProperty.call(data, 'createdBy')
		&& data.manageableByMe === manifest.polls.manageableByMe[typeKey][actor]
		&& sameIds(data.options.map((option) => option.id), expected.optionIds)
		&& assertAscending(data.options, (option) => [option.sortOrder, option.id]);
}

function validateMealManagement(status, payload, includeArchived) {
	const data = successData(status, payload);
	const expectedIds = includeArchived
		? manifest.polls.duty.mealManagementArchiveIds
		: manifest.polls.duty.mealManagementDefaultIds;
	if (!data || !Array.isArray(data.content) || data.page !== 0 || data.size !== 100
		|| data.totalElements !== expectedIds.length || data.totalPages !== (expectedIds.length === 0 ? 0 : 1)) {
		return false;
	}
	const actualIds = data.content.map((poll) => poll.id);
	return sameIds(actualIds, expectedIds) && assertDescending(actualIds)
		&& data.content.every((poll) => poll.status === (poll.id === manifest.polls.duty.mealOpen.id ? 'OPEN' : 'CLOSED'));
}

function validateMealManagementForbidden(status, payload) {
	return status === 403 && payload.success === false && payload.code === 'MEAL_DUTY_REQUIRED';
}

function validatePollResults(status, payload, key, anonymous) {
	const data = successData(status, payload);
	const expected = manifest.polls.byKey[key];
	if (!data || data.pollId !== expected.id
		|| data.campusId !== manifest.primaryCampus.campusId
		|| data.status !== (key === 'open' ? 'OPEN' : 'CLOSED')
		|| !sameInstant(data.startsAt, expected.startsAt)
		|| !sameInstant(data.endsAt, expected.endsAt)
		|| data.targetMemberCount !== 1000
		|| data.respondedCount !== 800
		|| data.notRespondedCount !== 200
		|| data.anonymous !== anonymous
		|| data.optionResults.length !== 5
		|| !sameIds(data.optionResults.map((option) => option.id), expected.optionIds)
		|| !assertAscending(data.optionResults, (option) => [option.sortOrder, option.id])) {
		return false;
	}
	const responseCount = data.optionResults.reduce((sum, option) => sum + option.responseCount, 0);
	const respondents = data.optionResults.flatMap((option) => option.respondents);
	const exactOptionResults = data.optionResults.every((option, optionIndex) => {
		if (option.responseCount !== 160) return false;
		if (anonymous) return option.respondents.length === 0;
		const expectedUserIds = manifest.polls.responderUserIds
			.filter((_, responderIndex) => responderIndex % 5 === optionIndex);
		return sameIds(option.respondents.map((respondent) => respondent.userId), expectedUserIds);
	});
	return responseCount === 800
		&& exactOptionResults
		&& (anonymous ? respondents.length === 0 : respondents.length === 800);
}

function validatePollComments(status, payload) {
	const data = successData(status, payload);
	return Array.isArray(data)
		&& data.length === 200
		&& sameIds(data.map((comment) => comment.commentId), manifest.polls.commentIds)
		&& assertAscending(data, (comment) => [comment.commentId])
		&& data.every((comment) => comment.pollId === manifest.polls.byKey.open.id && comment.deleted === false);
}

function validatePollMissingMembers(status, payload) {
	const data = successData(status, payload);
	return Array.isArray(data)
		&& data.length === 200
		&& sameIds(data.map((member) => member.userId), manifest.polls.missingUserIds);
}

function validatePollTemplateList(status, payload) {
	const data = successData(status, payload);
	return Array.isArray(data)
		&& data.length === 40
		&& sameIds(data.map((template) => template.id), manifest.polls.templateIds)
		&& assertAscending(data, (template) => [template.id])
		&& data.every((template, index) => template.options.length === 8
			&& sameIds(template.options.map((option) => option.id), manifest.polls.templates[index].optionIds)
			&& assertAscending(template.options, (option) => [option.sortOrder, option.id]));
}

function validatePollTemplateDetail(status, payload) {
	const data = successData(status, payload);
	return Boolean(data)
		&& data.id === manifest.polls.templateDetailId
		&& data.campusId === manifest.primaryCampus.campusId
		&& data.options.length === 8
		&& sameIds(data.options.map((option) => option.id), manifest.polls.templates[0].optionIds)
		&& assertAscending(data.options, (option) => [option.sortOrder, option.id]);
}

function validatePollIsolation(status, payload) {
	return status === 404
		&& payload.success === false
		&& payload.code === 'POLL_NOT_FOUND';
}

function validatePollIsolationMembership(status, payload) {
	return status === 403
		&& payload.success === false
		&& payload.code === 'POLL_ACCESS_FORBIDDEN';
}

function successData(status, payload) {
	return status === 200 && payload.success === true ? payload.data : null;
}

function assertAscending(items, keySelector) {
	for (let index = 1; index < items.length; index += 1) {
		if (compareKeys(keySelector(items[index - 1]), keySelector(items[index])) > 0) {
			return false;
		}
	}
	return true;
}

function assertDescending(values) {
	for (let index = 1; index < values.length; index += 1) {
		if (values[index - 1] < values[index]) {
			return false;
		}
	}
	return true;
}

function compareKeys(left, right) {
	for (let index = 0; index < Math.max(left.length, right.length); index += 1) {
		if (left[index] < right[index]) return -1;
		if (left[index] > right[index]) return 1;
	}
	return 0;
}

function sameIds(actual, expected) {
	return actual.length === expected.length && actual.every((value, index) => value === expected[index]);
}

function sameInstant(actual, expected) {
	return Number.isFinite(Date.parse(actual))
		&& Number.isFinite(Date.parse(expected))
		&& Date.parse(actual) === Date.parse(expected);
}

function parseJson(response) {
	try {
		return response.json();
	} catch {
		return {};
	}
}

function guardTarget() {
	const local = /^http:\/\/(127\.0\.0\.1|\[::1\])(?::\d+)?$/.test(BASE_URL);
	if (!local) {
		fail('Issue #196 baseline requires an explicit numeric loopback HTTP target; remote or implicit host resolution is blocked.');
	}
}
