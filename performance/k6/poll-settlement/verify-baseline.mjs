import { readFileSync, writeFileSync } from 'node:fs';
import { spawnSync } from 'node:child_process';
import { resolve } from 'node:path';
import { requireExactBaseUrl } from './scenario-contract.js';
import { expectedSelectedCorrectness, requireExactMode, validateSelectedCorrectness } from './single-mode-contract.mjs';

const MANIFEST_PATH = resolve(required('MANIFEST_PATH'));
const RUN_DIR = resolve(required('RUN_DIR'));
const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
const BASE_URL = requireExactBaseUrl(required('BASE_URL'));
const PERF_PASSWORD = required('PERF_PASSWORD');
const POSTGRES_CONTAINER_ID = required('POSTGRES_CONTAINER_ID');
const EXECUTION_RUN_ID = required('PERF_EXECUTION_RUN_ID');
const MODE = requireExactMode(required('MODE'));
if (BASE_URL !== target.baseUrl || POSTGRES_CONTAINER_ID !== target.containers.postgres.id) throw new Error('Verifier target contract mismatch.');
const manifest = JSON.parse(readFileSync(MANIFEST_PATH, 'utf8'));
if (manifest.selectedMode !== MODE) throw new Error('Verifier selected mode mismatch.');
const evidenceCase = { datasetId: manifest.datasetId, fixtureRunId: manifest.fixtureRunId, executionRunId: EXECUTION_RUN_ID, mode: MODE };
const pollIds = [...fixtures(manifest.coffee), ...fixtures(manifest.meal)].map((item) => Number(item.pollId));
if (pollIds.length !== 34 || new Set(pollIds).size !== 34) throw new Error('Manifest poll IDs are invalid.');
const selected = selectedFixtures(manifest, MODE);
const selectedPollIds = selected.map((item) => Number(item.pollId));
const mealAmounts = fixtures(manifest.meal).flatMap((fixture) => fixture.groups.map((group) => [Number(group.optionId), Number(group.expectedAmountPerMember)]));
if (mealAmounts.length !== 68 || new Set(mealAmounts.map(([id]) => id)).size !== 68) throw new Error('Manifest meal identity is invalid.');
const failures = [];

const invariants = JSON.parse(psql(`
	WITH run_polls AS (SELECT id,poll_type,payment_account_id,status::text FROM polls WHERE id IN (${pollIds.join(',')})),
	selected_polls AS (SELECT * FROM run_polls WHERE id IN (${selectedPollIds.join(',')})),
	meal_amounts(option_id,amount) AS (VALUES ${mealAmounts.map(([id, amount]) => `(${id},${amount})`).join(',')}),
	expected AS (
		SELECT p.campus_id,r.user_id,p.payment_account_id,'COFFEE'::text payment_category,'POLL_RESPONSE'::text source_type,r.id source_id,'UNPAID'::text status,o.price_amount amount
		FROM selected_polls rp JOIN polls p ON p.id=rp.id JOIN poll_responses r ON r.poll_id=p.id JOIN poll_response_options ro ON ro.response_id=r.id JOIN poll_options o ON o.id=ro.option_id WHERE rp.poll_type='COFFEE'
		UNION ALL
		SELECT p.campus_id,r.user_id,${manifest.expectedSettlement.meal.paymentAccountId},'MEAL','POLL_RESPONSE',r.id,'UNPAID',ma.amount
		FROM selected_polls rp JOIN polls p ON p.id=rp.id JOIN poll_responses r ON r.poll_id=p.id JOIN poll_response_options ro ON ro.response_id=r.id JOIN meal_amounts ma ON ma.option_id=ro.option_id WHERE rp.poll_type='MEAL'
	), actual AS (
		SELECT campus_id,user_id,payment_account_id,payment_category::text,source_type::text,source_id,status::text,amount FROM charge_items WHERE campus_id=${manifest.campusId}
	), missing AS (
		SELECT e.* FROM expected e LEFT JOIN actual a ON (a.campus_id,a.user_id,a.payment_account_id,a.payment_category,a.source_type,a.source_id,a.status,a.amount)=(e.campus_id,e.user_id,e.payment_account_id,e.payment_category,e.source_type,e.source_id,e.status,e.amount) WHERE a.source_id IS NULL
	), unexpected AS (
		SELECT a.* FROM actual a LEFT JOIN expected e ON (a.campus_id,a.user_id,a.payment_account_id,a.payment_category,a.source_type,a.source_id,a.status,a.amount)=(e.campus_id,e.user_id,e.payment_account_id,e.payment_category,e.source_type,e.source_id,e.status,e.amount) WHERE e.source_id IS NULL
	), run_charges AS (SELECT ci.* FROM charge_items ci JOIN poll_responses r ON r.id=ci.source_id JOIN run_polls p ON p.id=r.poll_id)
	SELECT json_build_object(
		'activeMembers',(SELECT count(*) FROM campus_members WHERE campus_id=${manifest.campusId} AND status='ACTIVE'),
		'polls',(SELECT count(*) FROM run_polls),'pollResponses',(SELECT count(*) FROM poll_responses r JOIN run_polls p ON p.id=r.poll_id),
		'pollResponseOptions',(SELECT count(*) FROM poll_response_options ro JOIN poll_responses r ON r.id=ro.response_id JOIN run_polls p ON p.id=r.poll_id),
		'selectedPolls',(SELECT count(*) FROM selected_polls),
		'coffeeClosed',(SELECT count(*) FROM run_polls WHERE poll_type='COFFEE' AND status='CLOSED'),'coffeeOpen',(SELECT count(*) FROM run_polls WHERE poll_type='COFFEE' AND status='OPEN'),
		'mealClosed',(SELECT count(*) FROM run_polls WHERE poll_type='MEAL' AND status='CLOSED'),'mealOpen',(SELECT count(*) FROM run_polls WHERE poll_type='MEAL' AND status='OPEN'),
		'coffeeCharges',(SELECT count(*) FROM actual WHERE payment_category='COFFEE'),'mealCharges',(SELECT count(*) FROM actual WHERE payment_category='MEAL'),
		'nonselectedChargeCount',(SELECT count(*) FROM actual a JOIN poll_responses r ON r.id=a.source_id WHERE r.poll_id NOT IN (SELECT id FROM selected_polls)),
		'nonselectedPollStateDrift',(SELECT count(*) FROM run_polls WHERE (poll_type='COFFEE' AND ((id IN (SELECT id FROM selected_polls) AND status<>'${MODE.startsWith('coffee-') ? 'CLOSED' : 'OPEN'}') OR (id NOT IN (SELECT id FROM selected_polls) AND status<>'OPEN'))) OR (poll_type='MEAL' AND status<>'CLOSED')),
		'missingExpectedIdentity',(SELECT count(*) FROM missing),'unexpectedIdentity',(SELECT count(*) FROM unexpected),
		'terminalStatusCharges',(SELECT count(*) FROM actual WHERE status<>'UNPAID'),
		'notificationSideEffects',(SELECT count(*) FROM notification_logs WHERE campus_id=${manifest.campusId}),
		'sourceUniqueDuplicates',(SELECT count(*) FROM (SELECT campus_id,user_id,payment_category,source_type,source_id FROM actual GROUP BY campus_id,user_id,payment_category,source_type,source_id HAVING count(*)>1) d),
		'mealSettlements',(SELECT count(*) FROM meal_poll_settlements s JOIN run_polls p ON p.id=s.poll_id),
		'mealGroups',(SELECT count(*) FROM meal_poll_charge_groups g JOIN run_polls p ON p.id=g.poll_id),
		'groupTotalViolations',(SELECT count(*) FROM meal_poll_charge_groups g JOIN run_polls p ON p.id=g.poll_id WHERE g.calculation_type='GROUP_TOTAL' AND NOT (g.amount_per_member=(g.entered_amount+g.response_count_snapshot-1)/g.response_count_snapshot AND g.actual_total_amount=g.amount_per_member::bigint*g.response_count_snapshot AND g.rounding_adjustment=g.actual_total_amount-g.requested_total_amount))
	);
`));
const numericInvariants = Object.fromEntries(Object.entries(invariants).map(([key, value]) => [key, Number(value)]));
const expected = expectedSelectedCorrectness(MODE);
try { validateSelectedCorrectness(numericInvariants, MODE); } catch (error) { failures.push(error.message); }

const token = await login();
const archiveApi = {};
for (const category of ['COFFEE', 'MEAL']) {
	const path = `/api/v1/campuses/${manifest.campusId}/charges/me?paymentCategory=${category}&status=UNPAID&includeArchived=false&page=0&size=10&sort=createdAt,desc`;
	const response = await fetch(`${BASE_URL}${path}`, { headers: { Authorization: `Bearer ${token}` } });
	const body = await response.json(); const data = body.data || {};
	const expectedItems = expectedPage(category);
	const actualItems = (data.items || []).map((item) => ({ id: item.id, paymentCategory: item.paymentCategory, amount: item.amount, status: item.status, paymentAccountId: item.account?.paymentAccountId, sourceType: item.source?.sourceType, sourceId: item.source?.sourceId }));
	archiveApi[category] = { status: response.status, campusId: data.campusId, page: data.page, size: data.size, totalElements: data.totalElements, totalPages: data.totalPages, items: actualItems };
	const expectedTotal = category === 'COFFEE' ? numericInvariants.coffeeCharges / 1000 : numericInvariants.mealCharges / 1000;
	if (response.status !== 200 || data.campusId !== manifest.campusId || data.page !== 0 || data.size !== 10 || data.totalElements !== expectedTotal || data.totalPages !== Math.ceil(expectedTotal / 10) || expectedItems.some((item) => item.campusId !== manifest.campusId || item.userId !== manifest.actorUserId) || JSON.stringify(actualItems) !== JSON.stringify(expectedItems.map(({ campusId, userId, ...item }) => item))) {
		failures.push(`${category} #201 archive/page identity mismatch`);
	}
}

const report = { case: evidenceCase, verifiedAt: new Date().toISOString(), invariants, expected, archiveApi, passed: failures.length === 0, failures };
writeFileSync(resolve(RUN_DIR, 'verification-report.json'), `${JSON.stringify(report, null, 2)}\n`);
if (failures.length) throw new Error(`Baseline verification failed:\n${failures.join('\n')}`);
process.stdout.write('Baseline DB/API exact identity invariants passed.\n');

function expectedPage(category) { return JSON.parse(psql(`SELECT coalesce(json_agg(json_build_object('id',id,'campusId',campus_id,'userId',user_id,'paymentCategory',payment_category,'amount',amount,'status',status,'paymentAccountId',payment_account_id,'sourceType',source_type,'sourceId',source_id) ORDER BY created_at DESC),'[]'::json) FROM (SELECT * FROM charge_items WHERE campus_id=${manifest.campusId} AND user_id=${manifest.actorUserId} AND payment_category='${category}' AND status='UNPAID' ORDER BY created_at DESC LIMIT 10) q;`)); }
function fixtures(target) { return [...target.sequentialWarmup, ...target.sequentialMeasured, ...target.concurrentWarmup, ...target.concurrentMeasured]; }
function selectedFixtures(value, mode) { const target = mode.startsWith('coffee-') ? value.coffee : value.meal; return mode.endsWith('-sequential') ? [...target.sequentialWarmup, ...target.sequentialMeasured] : [...target.concurrentWarmup, ...target.concurrentMeasured]; }
async function login() { const response = await fetch(`${BASE_URL}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: manifest.actorEmail, password: PERF_PASSWORD }) }); const body = await response.json(); if (response.status !== 200 || !body.data?.accessToken) throw new Error(`Verifier login failed: ${response.status}`); return body.data.accessToken; }
function psql(sql) { const result = spawnSync('docker', ['exec', '-i', POSTGRES_CONTAINER_ID, 'psql', '-U', 'faithlog', '-d', 'faithlog', '-X', '-q', '-v', 'ON_ERROR_STOP=1', '-A', '-t'], { input: sql, encoding: 'utf8' }); if (result.status !== 0) throw new Error(`psql failed: ${result.stderr.trim()}`); return result.stdout.trim(); }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
