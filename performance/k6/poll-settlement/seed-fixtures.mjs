import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';
import { requireExactBaseUrl } from './scenario-contract.js';
import { requireExactMode } from './single-mode-contract.mjs';

const PERF_MEMBER_COUNT = 1000;
const PERF_SEED = 192000;
const MODE = requireExactMode(required('MODE'));
const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
const BASE_URL = requireExactBaseUrl(required('BASE_URL'));
const POSTGRES_CONTAINER = target.containers.postgres.id;
const POSTGRES_USER = target.database.user;
const POSTGRES_DB = target.database.name;
const DATASET_ID = process.env.PERF_DATASET_ID;
const FIXTURE_RUN_ID = process.env.PERF_FIXTURE_RUN_ID;
const PERF_PASSWORD = process.env.PERF_PASSWORD;
const REPORT_ROOT = resolve(required('REPORT_ROOT'));
const MANIFEST_PATH = resolve(`${REPORT_ROOT}/fixtures/${FIXTURE_RUN_ID}/manifest.json`);
const SEED_REPORT_PATH = resolve(dirname(MANIFEST_PATH), 'seed-report.json');
const ANALYZE_REPORT_PATH = resolve(dirname(MANIFEST_PATH), 'analyze-report.json');
const SCRIPT_DIR = dirname(fileURLToPath(import.meta.url));
const actorEmail = `${DATASET_ID?.toLowerCase()}-actor@example.invalid`;
const inviteCode = `PERF192_${DATASET_ID?.slice(-32)}`;

if (!DATASET_ID || !/^PERFORMANCE_[A-Z0-9_-]{4,48}$/.test(DATASET_ID)) {
	throw new Error('PERF_DATASET_ID must match PERFORMANCE_[A-Z0-9_-]{4,48}.');
}
if (!FIXTURE_RUN_ID || !/^[A-Z0-9][A-Z0-9_-]{3,39}$/.test(FIXTURE_RUN_ID)) {
	throw new Error('PERF_FIXTURE_RUN_ID must be a fresh uppercase identifier.');
}
if ([MANIFEST_PATH, SEED_REPORT_PATH, ANALYZE_REPORT_PATH].some(existsSync)) {
	throw new Error('This fixtureRunId already has a report; reports and fixtures are never overwritten.');
}
if (BASE_URL !== target.baseUrl) throw new Error('Unapproved BASE_URL.');

const beforeCounts = globalCounts();
const baseState = datasetState();
if (Object.values(baseState).some((value) => Number(value) !== 0)) {
	throw new Error('Existing dataset state is forbidden; use a fresh PERF_DATASET_ID.');
}
if (!PERF_PASSWORD) throw new Error('PERF_PASSWORD is required to create the base actor.');
await signupActor();

runSeedSql();
const analyzeReport = runAnalyze();
const manifest = buildManifest();
const afterCounts = globalCounts();
const fixtureCounts = fixtureRunCounts(manifest.campusId);

assertEqual(afterCounts.users - beforeCounts.users, PERF_MEMBER_COUNT, 'users delta');
assertEqual(afterCounts.campuses - beforeCounts.campuses, 1, 'campuses delta');
assertEqual(afterCounts.polls - beforeCounts.polls, 34, 'polls delta');
assertEqual(afterCounts.responses - beforeCounts.responses, 34000, 'responses delta');
assertEqual(afterCounts.charges - beforeCounts.charges, 0, 'charges delta');
for (const [name, expected] of Object.entries({
	activeMembers: 1000,
	coffeeDuties: 2,
	mealDuties: 1,
	creatorOwnedCoffeePolls: 17,
	accountNeutralTemplates: 0,
	schedulerCreatedCoffeePolls: 0,
	polls: 34,
	options: 136,
	responses: 34000,
	responseOptions: 34000,
	notificationLogs: 0,
})) assertEqual(fixtureCounts[name], expected, name);

mkdirSync(dirname(MANIFEST_PATH), { recursive: true });
writeFileSync(ANALYZE_REPORT_PATH, `${JSON.stringify(analyzeReport, null, 2)}\n`, { flag: 'wx' });
writeFileSync(MANIFEST_PATH, `${JSON.stringify(manifest, null, 2)}\n`, { flag: 'wx' });
writeFileSync(SEED_REPORT_PATH, `${JSON.stringify({
	datasetId: DATASET_ID,
	fixtureRunId: FIXTURE_RUN_ID,
	seed: PERF_SEED,
	createdAt: new Date().toISOString(),
	beforeCounts,
	afterCounts,
	fixtureCounts,
}, null, 2)}\n`, { flag: 'wx' });
process.stdout.write(`Seeded ${DATASET_ID}/${FIXTURE_RUN_ID}: ${fixtureCounts.responses} responses.\n`);

function psql(sql, variables = {}) {
	const args = ['exec', '-i', POSTGRES_CONTAINER, 'psql', '-U', POSTGRES_USER, '-d', POSTGRES_DB,
		'-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t'];
	for (const [key, value] of Object.entries(variables)) args.push('-v', `${key}=${value}`);
	const result = spawnSync('docker', args, { input: sql, encoding: 'utf8' });
	if (result.status !== 0) throw new Error(`psql failed: ${result.stderr.trim()}`);
	return result.stdout.trim();
}

function runSeedSql() {
	psql(readFileSync(resolve(SCRIPT_DIR, 'seed-fixtures.sql'), 'utf8'), {
		dataset_id: DATASET_ID,
		fixture_run_id: FIXTURE_RUN_ID,
		actor_email: actorEmail,
		invite_code: inviteCode,
		member_count: PERF_MEMBER_COUNT,
		seed: PERF_SEED,
	});
}

function runAnalyze() {
	const startedAt = new Date().toISOString();
	psql(readFileSync(resolve(SCRIPT_DIR, 'analyze-fixtures.sql'), 'utf8'));
	const completedAt = new Date().toISOString();
	return { datasetId: DATASET_ID, fixtureRunId: FIXTURE_RUN_ID, startedAt, completedAt };
}

function datasetState() {
	return JSON.parse(psql(`
		SELECT json_build_object(
			'campuses', (SELECT count(*) FROM campuses WHERE name = '${literal(DATASET_ID)}'),
			'actors', (SELECT count(*) FROM users WHERE email = '${literal(actorEmail)}'),
			'activeMembers', (SELECT count(*) FROM campus_members cm JOIN campuses c ON c.id=cm.campus_id WHERE c.name='${literal(DATASET_ID)}' AND cm.status='ACTIVE'),
			'coffeeDuties', (SELECT count(*) FROM campus_duty_assignments d JOIN campuses c ON c.id=d.campus_id WHERE c.name='${literal(DATASET_ID)}' AND d.duty_type='COFFEE' AND d.is_active),
			'mealDuties', (SELECT count(*) FROM campus_duty_assignments d JOIN campuses c ON c.id=d.campus_id WHERE c.name='${literal(DATASET_ID)}' AND d.duty_type='MEAL' AND d.is_active),
			'coffeeAccounts', (SELECT count(*) FROM payment_accounts a JOIN campuses c ON c.id=a.campus_id JOIN users u ON u.id=a.owner_user_id WHERE c.name='${literal(DATASET_ID)}' AND u.email='${literal(actorEmail)}' AND a.account_type='COFFEE' AND a.is_active),
			'mealAccounts', (SELECT count(*) FROM payment_accounts a JOIN campuses c ON c.id=a.campus_id JOIN users u ON u.id=a.owner_user_id WHERE c.name='${literal(DATASET_ID)}' AND u.email='${literal(actorEmail)}' AND a.account_type='MEAL' AND a.is_active)
		);
	`));
}

async function signupActor() {
	const response = await fetch(`${BASE_URL}/api/v1/auth/signup`, {
		method: 'POST', headers: { 'Content-Type': 'application/json' },
		body: JSON.stringify({ name: `${DATASET_ID}_ACTOR`, email: actorEmail, password: PERF_PASSWORD }),
	});
	if (response.status !== 201) throw new Error(`Actor signup failed: HTTP ${response.status}.`);
}

function buildManifest() {
	const campusId = Number(psql(`SELECT id FROM campuses WHERE name='${literal(DATASET_ID)}';`));
	const actorUserId = Number(psql(`SELECT id FROM users WHERE email='${literal(actorEmail)}';`));
	const coffeeAccountId = Number(psql(`SELECT a.id FROM payment_accounts a JOIN users u ON u.id=a.owner_user_id WHERE a.campus_id=${campusId} AND a.account_type='COFFEE' AND a.is_active AND u.email='${literal(actorEmail)}';`));
	const mealAccountId = Number(psql(`SELECT a.id FROM payment_accounts a JOIN users u ON u.id=a.owner_user_id WHERE a.campus_id=${campusId} AND a.account_type='MEAL' AND a.is_active AND u.email='${literal(actorEmail)}';`));
	const prefix = `${DATASET_ID}|${FIXTURE_RUN_ID}|`;
	const rows = JSON.parse(psql(`
		SELECT json_agg(row_data ORDER BY poll_type, fixture_group, ordinal) FROM (
			SELECT p.poll_type, split_part(p.title,'|',4) fixture_group,
				split_part(p.title,'|',5)::integer ordinal, p.id poll_id,
				CASE WHEN p.poll_type='MEAL' THEN (
					SELECT json_agg(json_build_object('optionId',o.id,'calculationType',CASE WHEN o.sort_order<=2 THEN 'PER_MEMBER' ELSE 'GROUP_TOTAL' END,'enteredAmount',CASE o.sort_order WHEN 1 THEN 5000 WHEN 2 THEN 6000 WHEN 3 THEN 10001 ELSE 20003 END) ORDER BY o.sort_order)
					FROM poll_options o WHERE o.poll_id=p.id
				) ELSE '[]'::json END groups
			FROM polls p WHERE p.campus_id=${campusId} AND p.title LIKE '${literal(prefix)}%'
		) row_data;
	`));
	const manifest = {
		datasetId: DATASET_ID, fixtureRunId: FIXTURE_RUN_ID, selectedMode: MODE, seed: PERF_SEED,
		baseUrl: BASE_URL, memberCount: PERF_MEMBER_COUNT, campusId, actorEmail, actorUserId,
		fixtureCounts: { sequentialWarmup: 1, sequentialMeasured: 10, concurrentWarmup: 1, concurrentMeasured: 5 },
		coffee: groups(), meal: { paymentAccountId: mealAccountId, ...groups() },
		expectedSettlement: {
			sourceType: 'POLL_RESPONSE', status: 'UNPAID', expectedPerCategory: 17000,
			coffee: { paymentCategory: 'COFFEE', paymentAccountId: coffeeAccountId },
			meal: { paymentCategory: 'MEAL', paymentAccountId: mealAccountId },
		},
	};
	for (const row of rows) {
		const target = row.poll_type === 'COFFEE' ? manifest.coffee : manifest.meal;
		const expectedGroups = row.groups.map((group) => ({
			...group,
			expectedResponseCount: 250,
			expectedAmountPerMember: group.calculationType === 'PER_MEMBER' ? group.enteredAmount : Math.ceil(group.enteredAmount / 250),
		}));
		target[row.fixture_group].push({ pollId: row.poll_id, groups: expectedGroups });
	}
	return manifest;
}

function groups() {
	return { sequentialWarmup: [], sequentialMeasured: [], concurrentWarmup: [], concurrentMeasured: [] };
}

function globalCounts() {
	return JSON.parse(psql(`SELECT json_build_object('users',(SELECT count(*) FROM users),'campuses',(SELECT count(*) FROM campuses),'polls',(SELECT count(*) FROM polls),'responses',(SELECT count(*) FROM poll_responses),'charges',(SELECT count(*) FROM charge_items));`));
}

function fixtureRunCounts(campusId) {
	const prefix = `${DATASET_ID}|${FIXTURE_RUN_ID}|`;
	return JSON.parse(psql(`
		WITH run_polls AS (SELECT id,poll_type,created_by,payment_account_id,template_id FROM polls WHERE campus_id=${campusId} AND title LIKE '${literal(prefix)}%')
		SELECT json_build_object(
			'activeMembers',(SELECT count(*) FROM campus_members WHERE campus_id=${campusId} AND status='ACTIVE'),
			'coffeeDuties',(SELECT count(*) FROM campus_duty_assignments WHERE campus_id=${campusId} AND duty_type='COFFEE' AND is_active),
			'mealDuties',(SELECT count(*) FROM campus_duty_assignments WHERE campus_id=${campusId} AND duty_type='MEAL' AND is_active),
			'creatorOwnedCoffeePolls',(SELECT count(*) FROM run_polls p JOIN payment_accounts a ON a.id=p.payment_account_id WHERE p.poll_type='COFFEE' AND p.created_by=a.owner_user_id),
			'accountNeutralTemplates',(SELECT count(*) FROM poll_templates WHERE campus_id=${campusId} AND payment_account_id IS NOT NULL),
			'schedulerCreatedCoffeePolls',(SELECT count(*) FROM run_polls WHERE poll_type='COFFEE' AND template_id IS NOT NULL),
			'polls',(SELECT count(*) FROM run_polls),
			'options',(SELECT count(*) FROM poll_options o JOIN run_polls p ON p.id=o.poll_id),
			'responses',(SELECT count(*) FROM poll_responses r JOIN run_polls p ON p.id=r.poll_id),
			'responseOptions',(SELECT count(*) FROM poll_response_options ro JOIN poll_responses r ON r.id=ro.response_id JOIN run_polls p ON p.id=r.poll_id),
			'notificationLogs',(SELECT count(*) FROM notification_logs WHERE campus_id=${campusId})
		);
	`));
}

function literal(value) { return value.replaceAll("'", "''"); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
function assertEqual(actual, expected, label) {
	if (Number(actual) !== Number(expected)) throw new Error(`${label}: expected ${expected}, got ${actual}`);
}
