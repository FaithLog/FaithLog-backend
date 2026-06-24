import fs from 'node:fs';
import path from 'node:path';

const BASE_URL = (process.env.BASE_URL || '').replace(/\/$/, '');
const ADMIN_EMAIL = process.env.PERF_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.PERF_ADMIN_PASSWORD;
const MEMBER_PASSWORD = process.env.PERF_MEMBER_PASSWORD;
const DATASET_ID = process.env.PERF_DATASET_ID || 'PERF_20260624_CLOUDRUN_A';
const MEMBER_COUNT = Number(process.env.PERF_MEMBER_COUNT || 30);
const WEEK_START_DATE = process.env.PERF_WEEK_START_DATE || '2026-06-22';
const YEAR = Number(process.env.PERF_YEAR || 2026);
const MONTH = Number(process.env.PERF_MONTH || 6);

if (!BASE_URL || !ADMIN_EMAIL || !ADMIN_PASSWORD || !MEMBER_PASSWORD) {
	throw new Error('BASE_URL, PERF_ADMIN_EMAIL, PERF_ADMIN_PASSWORD, and PERF_MEMBER_PASSWORD are required.');
}

if (!DATASET_ID.startsWith('PERF_')) {
	throw new Error('PERF_DATASET_ID must start with PERF_.');
}

if (!Number.isInteger(MEMBER_COUNT) || MEMBER_COUNT < 1 || MEMBER_COUNT > 50) {
	throw new Error('PERF_MEMBER_COUNT must be an integer between 1 and 50.');
}

guardTarget();

const manifest = {
	datasetId: DATASET_ID,
	baseUrl: BASE_URL,
	startedAt: new Date().toISOString(),
	campus: null,
	adminUser: null,
	members: [],
	paymentAccounts: [],
	penaltyRules: [],
	devotionSubmissions: [],
	polls: [],
	prayer: {},
	readCheck: {},
};

const admin = await login(ADMIN_EMAIL, ADMIN_PASSWORD);
manifest.adminUser = await getMe(admin.token);

const campus = await ensureCampus(admin.token);
manifest.campus = campus;

await ensurePaymentAccounts(admin.token, campus.campusId);
await ensurePenaltyRules(admin.token, campus.campusId);

const members = [];
for (let index = 1; index <= MEMBER_COUNT; index += 1) {
	const member = await ensureMember(admin.token, campus.campusId, index);
	members.push(member);
	manifest.members.push({ userId: member.userId, email: member.email, membershipId: member.membershipId });
}

await ensureCoffeeDuty(admin.token, campus.campusId, members[0].userId);

const devotionUsers = [manifest.adminUser, ...members.slice(0, Math.min(12, members.length))];
for (let index = 0; index < devotionUsers.length; index += 1) {
	const user = devotionUsers[index];
	const token = user.email === ADMIN_EMAIL ? admin.token : (await login(user.email, MEMBER_PASSWORD)).token;
	const response = await put(token, `/api/v1/campuses/${campus.campusId}/devotions/me/weeks/${WEEK_START_DATE}`, weeklyDevotionBody(index));
	manifest.devotionSubmissions.push({ userId: user.userId, weeklyRecordId: response.data.weeklyRecordId, submittedAt: response.data.submittedAt });
}

const openPoll = await createPoll(admin.token, campus.campusId, {
	title: `${DATASET_ID} OPEN READ POLL`,
	pollType: 'CUSTOM',
	selectionType: 'SINGLE',
	isAnonymous: false,
	chargeGenerationType: 'NONE',
	startsAt: new Date(Date.now() - 60_000).toISOString(),
	endsAt: new Date(Date.now() + 86_400_000).toISOString(),
	options: [
		{ content: `${DATASET_ID} OPTION A`, priceAmount: 0, sortOrder: 1 },
		{ content: `${DATASET_ID} OPTION B`, priceAmount: 0, sortOrder: 2 },
		{ content: `${DATASET_ID} OPTION C`, priceAmount: 0, sortOrder: 3 },
	],
});
manifest.polls.push({ id: openPoll.id, title: openPoll.title, status: openPoll.status, optionIds: openPoll.options.map((option) => option.id) });

const closedPoll = await createPoll(admin.token, campus.campusId, {
	title: `${DATASET_ID} CLOSED READ POLL`,
	pollType: 'CUSTOM',
	selectionType: 'SINGLE',
	isAnonymous: true,
	chargeGenerationType: 'NONE',
	startsAt: new Date(Date.now() - 172_800_000).toISOString(),
	endsAt: new Date(Date.now() - 86_400_000).toISOString(),
	options: [
		{ content: `${DATASET_ID} CLOSED A`, priceAmount: 0, sortOrder: 1 },
		{ content: `${DATASET_ID} CLOSED B`, priceAmount: 0, sortOrder: 2 },
	],
});
manifest.polls.push({ id: closedPoll.id, title: closedPoll.title, status: closedPoll.status, optionIds: closedPoll.options.map((option) => option.id) });

for (let index = 0; index < Math.min(20, members.length); index += 1) {
	const member = members[index];
	const token = (await login(member.email, MEMBER_PASSWORD)).token;
	const optionId = openPoll.options[index % openPoll.options.length].id;
	await put(token, `/api/v1/campuses/${campus.campusId}/polls/${openPoll.id}/responses/me`, {
		optionIds: [optionId],
		memo: `${DATASET_ID} response ${index + 1}`,
	});
}

const season = await post(admin.token, `/api/v1/admin/campuses/${campus.campusId}/prayer-seasons`, {
	name: `${DATASET_ID} Prayer Season`,
	startDate: WEEK_START_DATE,
});
const groups = [];
for (let groupIndex = 0; groupIndex < 3; groupIndex += 1) {
	const group = await post(admin.token, `/api/v1/admin/prayer-seasons/${season.data.seasonId}/groups`, {
		name: `${DATASET_ID} Prayer Group ${groupIndex + 1}`,
		sortOrder: groupIndex + 1,
	});
	const groupMembers = members.slice(groupIndex * 10, (groupIndex + 1) * 10).map((member) => member.userId);
	const replaced = await put(admin.token, `/api/v1/admin/prayer-groups/${group.data.groupId}/members`, {
		userIds: groupMembers,
	});
	groups.push({ groupId: replaced.data.groupId, userIds: groupMembers });
}
manifest.prayer = { seasonId: season.data.seasonId, groups };

const board = await get(admin.token, `/api/v1/campuses/${campus.campusId}/prayers/weeks/${WEEK_START_DATE}`);
const submissions = board.data.groups
	.flatMap((group) => group.members)
	.slice(0, Math.min(18, MEMBER_COUNT))
	.map((member, index) => ({
		userId: member.userId,
		content: `${DATASET_ID} prayer request ${index + 1}`,
		version: member.version,
	}));
await put(admin.token, `/api/v1/campuses/${campus.campusId}/prayers/weeks/${WEEK_START_DATE}/submissions`, { submissions });

manifest.readCheck = {
	campusDetail: (await get(admin.token, `/api/v1/campuses/${campus.campusId}`)).data,
	adminDashboard: (await get(admin.token, `/api/v1/admin/campuses/${campus.campusId}/dashboard/summary?weekStartDate=${WEEK_START_DATE}`)).data,
	myWeeklyDevotion: (await get(admin.token, `/api/v1/campuses/${campus.campusId}/devotions/me/weeks/${WEEK_START_DATE}`)).data,
	myChargeSummary: (await get(admin.token, `/api/v1/campuses/${campus.campusId}/charges/me/summary?year=${YEAR}&month=${MONTH}`)).data,
	pollListCount: (await get(admin.token, `/api/v1/campuses/${campus.campusId}/polls`)).data.length,
	prayerGroups: (await get(admin.token, `/api/v1/campuses/${campus.campusId}/prayers/weeks/${WEEK_START_DATE}`)).data.groups.length,
};

manifest.finishedAt = new Date().toISOString();
const outputDir = path.join('build', 'reports', 'perf-data');
fs.mkdirSync(outputDir, { recursive: true });
const manifestPath = path.join(outputDir, `${DATASET_ID}.json`);
fs.writeFileSync(manifestPath, `${JSON.stringify(manifest, null, 2)}\n`);

console.log(JSON.stringify({
	manifestPath,
	datasetId: DATASET_ID,
	campusId: campus.campusId,
	memberCount: manifest.members.length,
	devotionSubmissionCount: manifest.devotionSubmissions.length,
	pollIds: manifest.polls.map((poll) => poll.id),
	prayerSeasonId: manifest.prayer.seasonId,
}, null, 2));

async function ensureCampus(token) {
	const existing = await get(token, `/api/v1/admin/campuses?name=${encodeURIComponent(DATASET_ID)}&page=0&size=20&sort=createdAt,desc`);
	const found = existing.data.content.find((item) => item.name === DATASET_ID || item.name === `${DATASET_ID} Campus`);
	if (found) {
		return { campusId: found.campusId, name: found.name };
	}
	const created = await post(token, '/api/v1/campuses', {
		name: `${DATASET_ID} Campus`,
		region: `${DATASET_ID} Region`,
		description: `${DATASET_ID} read performance dataset`,
	});
	return created.data;
}

async function ensurePaymentAccounts(token, campusId) {
	const existing = await get(token, `/api/v1/campuses/${campusId}/payment-accounts`);
	for (const accountType of ['PENALTY', 'COFFEE']) {
		const account = existing.data.find((item) => item.accountType === accountType);
		if (account) {
			manifest.paymentAccounts.push({ id: account.id, accountType });
			continue;
		}
		const created = await post(token, `/api/v1/admin/campuses/${campusId}/payment-accounts`, {
			accountType,
			nickname: `${DATASET_ID} ${accountType}`,
			bankName: 'PERF Bank',
			accountNumber: `000-${accountType === 'PENALTY' ? '111' : '222'}-${DATASET_ID.replaceAll('_', '')}`,
			accountHolder: `${DATASET_ID} Holder`,
			ownerUserId: manifest.adminUser.userId,
		});
		manifest.paymentAccounts.push({ id: created.data.id, accountType });
	}
}

async function ensurePenaltyRules(token, campusId) {
	const existing = await get(token, `/api/v1/campuses/${campusId}/penalty-rules`);
	const active = new Set(existing.data.filter((rule) => rule.isActive).map((rule) => rule.ruleType));
	const rules = [
		{ ruleType: 'QUIET_TIME', calculationType: 'MISSING_COUNT', requiredCount: 5, baseAmount: 0, amountPerUnit: 500 },
		{ ruleType: 'PRAYER', calculationType: 'MISSING_COUNT', requiredCount: 5, baseAmount: 0, amountPerUnit: 500 },
		{ ruleType: 'BIBLE_READING', calculationType: 'MISSING_COUNT', requiredCount: 5, baseAmount: 0, amountPerUnit: 300 },
		{ ruleType: 'SATURDAY_LATE', calculationType: 'LATE_MINUTE', requiredCount: 0, baseAmount: 1000, amountPerUnit: 100 },
	];
	for (const rule of rules) {
		if (active.has(rule.ruleType)) {
			continue;
		}
		const created = await post(token, `/api/v1/admin/campuses/${campusId}/penalty-rules`, rule);
		manifest.penaltyRules.push({ id: created.data.id, ruleType: created.data.ruleType });
	}
}

async function ensureMember(adminToken, campusId, index) {
	const suffix = String(index).padStart(2, '0');
	const email = `${DATASET_ID.toLowerCase()}_${suffix}@example.com`;
	let user = await findUserByEmail(adminToken, email);
	if (!user) {
		const created = await post(adminToken, '/api/v1/auth/signup', {
			name: `${DATASET_ID} Member ${suffix}`,
			email,
			password: MEMBER_PASSWORD,
		});
		user = { userId: created.data.id, email: created.data.email, name: created.data.name };
	}
	const members = await get(adminToken, `/api/v1/admin/campuses/${campusId}/members`);
	const existing = members.data.find((member) => member.userId === user.userId && member.status === 'ACTIVE');
	if (existing) {
		return { ...user, membershipId: existing.membershipId };
	}
	const added = await post(adminToken, `/api/v1/admin/campuses/${campusId}/members`, { userId: user.userId });
	return { ...user, membershipId: added.data.membershipId };
}

async function findUserByEmail(token, email) {
	const response = await get(token, `/api/v1/admin/users?email=${encodeURIComponent(email)}&page=0&size=1&sort=createdAt,desc`);
	const user = response.data.content.find((item) => item.email === email);
	return user ? { userId: user.userId, email: user.email, name: user.name } : null;
}

async function ensureCoffeeDuty(token, campusId, userId) {
	const existing = await get(token, `/api/v1/admin/campuses/${campusId}/duty-assignments`);
	if (existing.data.some((item) => item.dutyType === 'COFFEE' && item.isActive && item.userId === userId)) {
		return;
	}
	await put(token, `/api/v1/admin/campuses/${campusId}/duty-assignments/coffee`, { userId });
}

async function createPoll(token, campusId, body) {
	const created = await post(token, `/api/v1/admin/campuses/${campusId}/polls`, body);
	return created.data;
}

function weeklyDevotionBody(index) {
	const dates = ['2026-06-22', '2026-06-23', '2026-06-24', '2026-06-25', '2026-06-26', '2026-06-27', '2026-06-28'];
	return {
		submit: true,
		saturdayLateMinutes: index % 4 === 0 ? 5 : 0,
		dailyChecks: dates.map((recordDate, dayIndex) => ({
			recordDate,
			quietTimeChecked: (dayIndex + index) % 5 !== 0,
			prayerChecked: (dayIndex + index) % 4 !== 0,
			bibleReadingChecked: (dayIndex + index) % 3 !== 0,
		})),
	};
}

async function login(email, password) {
	const response = await post(null, '/api/v1/auth/login', { email, password });
	return {
		token: response.data.accessToken,
		refreshToken: response.data.refreshToken,
	};
}

async function getMe(token) {
	const response = await get(token, '/api/v1/users/me');
	return {
		userId: response.data.id,
		name: response.data.name,
		role: response.data.role,
	};
}

async function get(token, pathName) {
	return request('GET', pathName, token);
}

async function post(token, pathName, body) {
	return request('POST', pathName, token, body);
}

async function put(token, pathName, body) {
	return request('PUT', pathName, token, body);
}

async function request(method, pathName, token, body) {
	const headers = { 'Content-Type': 'application/json' };
	if (token) {
		headers.Authorization = `Bearer ${token}`;
	}
	const response = await fetch(`${BASE_URL}${pathName}`, {
		method,
		headers,
		body: body === undefined ? undefined : JSON.stringify(body),
	});
	const text = await response.text();
	const parsed = text ? JSON.parse(text) : null;
	if (!response.ok || parsed?.success === false) {
		throw new Error(`${method} ${pathName} failed: status=${response.status} body=${text}`);
	}
	return parsed;
}

function guardTarget() {
	const localTarget = /^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(?::\d+)?($|\/)/.test(BASE_URL);
	if (!localTarget && process.env.ALLOW_REMOTE_LOAD !== 'true') {
		throw new Error('Remote PERF dataset seeding is blocked. Set ALLOW_REMOTE_LOAD=true only after PM approval.');
	}
}
