import assert from 'node:assert/strict';
import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import test from 'node:test';
import { fileURLToPath, pathToFileURL } from 'node:url';

const testRoot = path.dirname(fileURLToPath(import.meta.url));
const scenarioRoot = path.resolve(testRoot, '..');
const repositoryRoot = path.resolve(scenarioRoot, '../../..');
const moduleUrl = (file) => pathToFileURL(path.join(scenarioRoot, file)).href;
const readScenario = (file) => fs.readFileSync(path.join(scenarioRoot, file), 'utf8');
const sha256 = (file) => createHash('sha256').update(fs.readFileSync(path.join(repositoryRoot, file))).digest('hex');

test('current develop source identity binds #200/#201/#202/#206 API, Flyway, and runtime contract files', async () => {
	const inventory = JSON.parse(readScenario('inventory.json'));
	const manifest = JSON.parse(readScenario('source-manifest.json'));
	assert.equal(inventory.currentDevelopContract?.revision, '6796ed146244d8f3f5b5dd7048ebe16865084a97');
	assert.deepEqual(inventory.currentDevelopContract?.integratedIssues, [200, 201, 202, 206]);
	assert.deepEqual(inventory.currentDevelopContract?.schemaContract, {
		flywayCurrentVersion: '11',
		requiredIndexes: ['uk_campus_duty_assignments_active_coffee_user'],
		forbiddenIndexes: ['uk_campus_duty_assignments_active_coffee'],
		requireCoffeeTemplateAccountNeutrality: true,
		requireAllPublicApplicationTablesRls: true,
		requireRlsNotForced: true,
		requireNoRlsPolicies: true,
		requireNoDataApiOrPublicExposure: true,
		requireEffectiveJdbcSelect: true,
	});

	const requiredRefs = [
		'docker-compose.yml',
		'src/main/resources/application-docker.yml',
		'src/main/resources/db/migration/V9__allow_multiple_active_coffee_duties.sql',
		'src/main/resources/db/migration/V10__neutralize_coffee_template_accounts.sql',
		'src/main/resources/db/migration/V11__secure_supabase_data_api.sql',
		'src/main/java/com/faithlog/billing/controller/BillingPageRequests.java',
		'src/main/java/com/faithlog/billing/service/policy/ChargeArchivePolicy.java',
		'src/main/java/com/faithlog/poll/service/CoffeeOperationClassifier.java',
	];
	assert.deepEqual(inventory.currentDevelopContract?.repositorySourceRefs, requiredRefs);
	for (const sourceRef of requiredRefs) {
		assert.equal(manifest.productionSourceRefs?.[sourceRef], sha256(sourceRef), sourceRef);
	}

	const { buildSourceIdentity, validateSourceIdentity } = await import(moduleUrl('source-identity.mjs'));
	const sourceRefs = [
		...inventory.queries.flatMap((query) => query.productionSourceRefs),
		...inventory.currentDevelopContract.repositorySourceRefs,
	];
	const identity = buildSourceIdentity({
		repositoryRoot,
		scenarioRoot,
		gitCommit: inventory.currentDevelopContract.revision,
		gitDirty: false,
		sqlFiles: inventory.queries.map((query) => query.sqlFile),
		productionSourceRefs: sourceRefs,
		inventoryPath: 'inventory.json',
		reportContractPath: 'report-contract.json',
		expected: { productionSourceRefs: manifest.productionSourceRefs },
	});
	assert.deepEqual(identity.sourceHashMismatches, []);
	assert.equal(validateSourceIdentity(identity).adoptable, true);
});

test('24-query inventory records current archive, stable pagination, duty visibility, and coffee ownership variants', () => {
	const inventory = JSON.parse(readScenario('inventory.json'));
	assert.equal(inventory.queries.length, 24);
	const byId = new Map(inventory.queries.map((query) => [query.id, query]));

	assert.deepEqual(byId.get('i193-member-charge-history')?.apiVariant, {
		includeArchived: false,
		archiveWindow: 'P1M',
		paidCompletionColumn: 'charge_items.paid_at',
		waivedCanceledCompletionColumn: 'charge_items.updated_at',
		sort: 'createdAt,desc',
		stableSecondarySort: 'id,desc',
	});
	assert.deepEqual(byId.get('i195-duty-assignment-list')?.apiVariant, {
		staleOnly: false,
		membershipStatus: 'ACTIVE',
		staleOnlyTrueVariantMeasured: false,
	});
	assert.deepEqual(byId.get('i192-due-coffee-polls')?.currentFlowGuards, [
		'consistent COFFEE poll configuration',
		'creator has ACTIVE campus membership',
		'creator has ACTIVE COFFEE duty',
		'poll payment account is active, COFFEE, same-campus, and creator-owned',
	]);

	const historySql = readScenario('sql/i193-member-charge-history.sql');
	assert.match(historySql, /ci\.status\s*=\s*'UNPAID'/i);
	assert.match(historySql, /ci\.status\s*=\s*'PAID'[\s\S]*ci\.paid_at\s*>=\s*:'archive_cutoff'/i);
	assert.match(historySql, /ci\.status\s+IN\s*\(\s*'WAIVED'\s*,\s*'CANCELED'\s*\)[\s\S]*ci\.updated_at\s*>=\s*:'archive_cutoff'/i);
	assert.match(historySql, /ORDER BY\s+ci\.created_at\s+DESC\s*,\s*ci\.id\s+DESC/i);

	const dutySql = readScenario('sql/i195-duty-assignment-list.sql');
	assert.match(dutySql, /EXISTS\s*\([\s\S]*campus_members[\s\S]*status\s*=\s*'ACTIVE'/i);
});

test('#201 archive cutoff is an explicit cross-issue runtime anchor with no default', async () => {
	const { validateAnchors } = await import(moduleUrl('anchor-contract.mjs'));
	const report = JSON.parse(readScenario('cross-issue-report.example.json'));
	const anchors = {
		...report.anchors,
		archive_cutoff: '2026-06-16T00:00:00+09:00',
	};
	assert.equal(validateAnchors(anchors).archive_cutoff, anchors.archive_cutoff);
	const { archive_cutoff: omitted, ...missing } = anchors;
	assert.equal(omitted, anchors.archive_cutoff);
	assert.throws(() => validateAnchors(missing), /archive_cutoff.*required|archive_cutoff.*RFC3339/i);
	assert.throws(() => validateAnchors({ ...anchors, archive_cutoff: '2026-06-16' }), /archive_cutoff.*RFC3339/i);
});

test('schema identity requires Flyway V11 RLS state while proving the runtime JDBC role remains effective', async () => {
	const { normalizeSchemaState, validateSchemaContinuity, validateSchemaSnapshot } = await import(moduleUrl('schema-contract.mjs'));
	const state = {
		flyway: [
			{ installedRank: 10, version: '10', success: true },
			{ installedRank: 11, version: '11', success: true },
		],
		columns: [{ table: 'polls', ordinalPosition: 1, name: 'id' }],
		constraints: [{ table: 'polls', name: 'polls_pkey' }],
		indexes: [
			{ table: 'polls', name: 'polls_pkey' },
			{
				table: 'campus_duty_assignments',
				name: 'uk_campus_duty_assignments_active_coffee_user',
				definition: "CREATE UNIQUE INDEX uk_campus_duty_assignments_active_coffee_user ON public.campus_duty_assignments USING btree (campus_id, duty_type, user_id) WHERE ((is_active = true) AND ((duty_type)::text = 'COFFEE'::text))",
			},
		],
		databaseRole: {
			currentUser: 'runtime-role', sessionUser: 'runtime-role', rowSecuritySetting: 'on',
			superuser: false, bypassRls: false, schemaUsage: true,
			publicExposureCount: 0, dataApiExposureCount: 0, exposedDefaultAclCount: 0,
		},
		tableSecurity: [{
			table: 'polls', owner: 'runtime-role', rowSecurityEnabled: true, rowSecurityForced: false,
			policyCount: 0, currentUserHasSelect: true, currentUserOwnsTable: true,
		}],
	};
	const expected = {
		flywayCurrentVersion: '11',
		requiredIndexes: ['uk_campus_duty_assignments_active_coffee_user'],
		forbiddenIndexes: ['uk_campus_duty_assignments_active_coffee'],
		requireAllPublicApplicationTablesRls: true,
		requireRlsNotForced: true,
		requireNoRlsPolicies: true,
		requireNoDataApiOrPublicExposure: true,
		requireEffectiveJdbcSelect: true,
	};
	const normalized = normalizeSchemaState(state);
	assert.equal(validateSchemaSnapshot(normalized, ['polls'], expected).adoptable, true);
	const disabled = normalizeSchemaState({
		...state,
		tableSecurity: [{ ...state.tableSecurity[0], rowSecurityEnabled: false }],
	});
	assert.equal(validateSchemaSnapshot(disabled, ['polls'], expected).adoptable, false);
	const blocked = normalizeSchemaState({
		...state,
		tableSecurity: [{
			...state.tableSecurity[0], currentUserHasSelect: false, currentUserOwnsTable: false,
		}],
	});
	assert.equal(validateSchemaSnapshot(blocked, ['polls'], expected).adoptable, false);
	const exposed = normalizeSchemaState({
		...state,
		databaseRole: { ...state.databaseRole, dataApiExposureCount: 1 },
	});
	assert.equal(validateSchemaSnapshot(exposed, ['polls'], expected).adoptable, false);
	assert.equal(validateSchemaContinuity(normalized, disabled).stable, false);
});

test('#202 runtime identity binds PGUSER to inspected POSTGRES_USER and the connected database role', async () => {
	const { validateDatabaseContinuity, validateDatabaseIdentity } = await import(moduleUrl('runtime-contract.mjs'));
	const inspected = {
		configuredDatabase: 'faithlog', configuredUser: 'faithlog',
		containerNetworkAddresses: ['172.30.0.2'], postgresInternalPort: 5432,
		containerStartedAt: '2026-07-16T00:00:00.000Z',
	};
	const connected = {
		serverAddress: '172.30.0.2', serverPort: 5432, database: 'faithlog',
		currentUser: 'faithlog', sessionUser: 'faithlog',
		postmasterStartedAt: '2026-07-16T00:00:01.000Z',
	};
	assert.doesNotThrow(() => validateDatabaseIdentity(inspected, connected, 'faithlog', 'faithlog'));
	assert.throws(
		() => validateDatabaseIdentity(inspected, { ...connected, currentUser: 'other' }, 'faithlog', 'faithlog'),
		/database role|PGUSER|POSTGRES_USER/i,
	);
	assert.throws(
		() => validateDatabaseIdentity({ ...inspected, configuredUser: 'other' }, connected, 'faithlog', 'faithlog'),
		/database role|PGUSER|POSTGRES_USER/i,
	);
	assert.equal(validateDatabaseContinuity(connected, { ...connected, currentUser: 'other' }).stable, false);
	const runner = readScenario('run-baseline.mjs');
	assert.match(runner, /POSTGRES_USER/);
	assert.match(runner, /'currentUser'[\s\S]*current_user/);
});

test('#200 coffee fixture preflight rejects stale creator duty, membership, or account ownership before EXPLAIN', async () => {
	const { validateAnchorPreflight } = await import(moduleUrl('anchor-contract.mjs'));
	const expectedState = {
		member_status: 'ACTIVE', poll_status: 'OPEN', poll_title: 'fixture poll',
		meal_poll_status: 'CLOSED', meal_poll_title: 'fixture meal',
		payment_account_is_active: true, payment_account_owner_user_id: 30,
		payment_account_nickname: 'fixture account', prayer_season_status: 'ACTIVE',
		prayer_season_name: 'fixture season', prayer_week_status: 'OPEN',
	};
	const valid = {
		campusExists: true, memberInCampus: true, pollInCampus: true, mealPollInCampus: true,
		paymentAccountInCampus: true, prayerSeasonInCampus: true, prayerWeekInSeason: true,
		pollCreatorActiveMember: true, pollCreatorActiveCoffeeDuty: true,
		pollPaymentAccountOwnedActiveCoffee: true, pollConfigurationConsistent: true,
		coffeeTemplateAccountNeutralityViolationCount: 0,
		coffeeTemplateInconsistentActiveCount: 0,
		memberCount: 1000, pollCount: 1, mealPollCount: 1, paymentAccountCount: 1,
		prayerSeasonCount: 1, prayerWeekCount: 1,
		memberStatus: 'ACTIVE', pollStatus: 'OPEN', pollTitle: 'fixture poll',
		mealPollStatus: 'CLOSED', mealPollTitle: 'fixture meal',
		paymentAccountIsActive: true, paymentAccountOwnerUserId: 30,
		paymentAccountNickname: 'fixture account', prayerSeasonStatus: 'ACTIVE',
		prayerSeasonName: 'fixture season', prayerWeekStatus: 'OPEN',
	};
	assert.equal(validateAnchorPreflight(valid, expectedState).adoptable, true);
	for (const field of [
		'pollCreatorActiveMember', 'pollCreatorActiveCoffeeDuty',
		'pollPaymentAccountOwnedActiveCoffee', 'pollConfigurationConsistent',
	]) {
		const rejected = validateAnchorPreflight({ ...valid, [field]: false }, expectedState);
		assert.equal(rejected.adoptable, false, field);
		assert.match(rejected.reasons.join(','), new RegExp(field, 'i'));
	}
	for (const field of [
		'coffeeTemplateAccountNeutralityViolationCount', 'coffeeTemplateInconsistentActiveCount',
	]) {
		const rejected = validateAnchorPreflight({ ...valid, [field]: 1 }, expectedState);
		assert.equal(rejected.adoptable, false, field);
		assert.match(rejected.reasons.join(','), new RegExp(field, 'i'));
	}
	const runner = readScenario('run-baseline.mjs');
	for (const evidenceField of [
		'pollCreatorActiveMember', 'pollCreatorActiveCoffeeDuty',
		'pollPaymentAccountOwnedActiveCoffee', 'pollConfigurationConsistent',
		'coffeeTemplateAccountNeutralityViolationCount', 'coffeeTemplateInconsistentActiveCount',
	]) {
		assert.match(runner, new RegExp(`'${evidenceField}'`));
	}
});

test('common integrity audit marks only #194-relevant evidence applicable and forbids hidden runtime fallbacks', async () => {
	const contract = JSON.parse(readScenario('report-contract.json'));
	assert.deepEqual(contract.integrityAudit?.notApplicable, {
		redisContinuity: 'No Redis command or Redis-backed evidence is used by the PostgreSQL EXPLAIN scenario.',
		k6V2MetricMath: 'No k6 process, Counter, Rate, Trend, HTTP request, or throughput claim is produced by this scenario.',
		dockerResourceSampling: 'Diagnostic EXPLAIN JSON is not promoted to an API/load baseline; CPU/RAM evidence remains outside this scenario.',
	});
	assert.deepEqual(contract.integrityAudit?.applicable, [
		'current-develop-source-and-schema-identity',
		'runtime-approved-compose-and-database-target',
		'pre-lock-post-lock-final-compose-database-continuity',
		'postgres-cumulative-counter-and-pgss-state-continuity',
		'fixture-and-anchor-correctness',
		'first-machine-readable-rejection',
	]);
	assert.equal(contract.automaticAdoption, false);

	const { validateApprovedComposeTarget } = await import(moduleUrl('runtime-contract.mjs'));
	const actual = {
		composeProject: 'faithlog-approved', composeService: 'postgres',
		postgresImageId: 'sha256:approved', postgresImageReference: 'postgres:17',
	};
	const approved = {
		composeProject: 'faithlog-approved', composeService: 'postgres',
		postgresImageId: 'sha256:approved', postgresImageReference: 'postgres:17',
	};
	assert.doesNotThrow(() => validateApprovedComposeTarget(actual, approved));
	for (const field of Object.keys(approved)) {
		assert.throws(() => validateApprovedComposeTarget(actual, { ...approved, [field]: '' }), /required|approved/i);
		assert.throws(() => validateApprovedComposeTarget(actual, { ...approved, [field]: 'different' }), /mismatch|approved/i);
	}
	const runner = readScenario('run-baseline.mjs');
	for (const input of [
		'EXPECTED_COMPOSE_PROJECT', 'EXPECTED_POSTGRES_SERVICE',
		'EXPECTED_POSTGRES_IMAGE_ID', 'EXPECTED_POSTGRES_IMAGE_REFERENCE',
	]) {
		assert.match(runner, new RegExp(`'${input}'`));
	}
});

test('PostgreSQL cumulative counters stay decimal strings/BigInt and pg_stat_statements availability is continuous', async () => {
	const {
		parsePgCumulativeCounter, validatePgStatStatementsContinuity,
	} = await import(moduleUrl('runtime-contract.mjs'));
	assert.equal(parsePgCumulativeCounter('9007199254740993', 'counter'), 9007199254740993n);
	assert.equal(parsePgCumulativeCounter('0', 'counter'), 0n);
	for (const invalid of [9007199254740992, -1, '-1', '1.0', '', null]) {
		assert.throws(() => parsePgCumulativeCounter(invalid, 'counter'), /decimal string|counter/i);
	}
	assert.equal(validatePgStatStatementsContinuity(
		{ available: false, extensionVersion: null, viewAvailable: false },
		{ available: false, extensionVersion: null, viewAvailable: false },
	).stable, true);
	assert.equal(validatePgStatStatementsContinuity(
		{ available: false, extensionVersion: null, viewAvailable: false },
		{ available: true, extensionVersion: '1.11', viewAvailable: true },
	).stable, false);
	const runner = readScenario('run-baseline.mjs');
	for (const field of ['nModSinceAnalyze', 'vacuumCount', 'autovacuumCount', 'liveTuples', 'deadTuples', 'allVisiblePages']) {
		assert.match(runner, new RegExp(`'${field}'[\\s\\S]*::text`));
	}
	assert.match(runner, /'pgStatStatements'/);
});

test('all seven current cross-issue artifacts remain pending without a separate approval-evidence schema', async () => {
	const { classifyIssueArtifactAcceptance } = await import(moduleUrl('cross-issue-contract.mjs'));
	const observed = new Map([
		[192, { status: 'verified', passed: true, automaticAdoption: false, measurementStatus: 'conditional-boundary-only' }],
		[193, { measurementStatus: 'conditional-shared-stack', automaticAdoption: false }],
		[195, { status: 'conditional-not-adoptable', automaticAdoption: false }],
		[196, { measurementStatus: 'conditional-not-adoptable', accepted: false, automaticAdoption: false }],
		[197, { status: 'baseline-measured', automaticAdoption: false, currentDevelopProvenance: false }],
		[198, { status: 'before-baseline', automaticAdoption: false, cumulativeStateStrategyImplemented: false }],
		[199, { baselineAdoptionStatus: 'conditional-not-adoptable', adoptable: false }],
	]);
	for (const [issueNumber, artifact] of observed) {
		const result = classifyIssueArtifactAcceptance(issueNumber, artifact);
		assert.equal(result.accepted, false, `#${issueNumber}`);
		assert.equal(result.pendingApprovalContract, true, `#${issueNumber}`);
		assert.match(result.reason, new RegExp(`issue-${issueNumber}.*pending`, 'i'));
	}
	const example = JSON.parse(readScenario('cross-issue-report.example.json'));
	for (const issueNumber of observed.keys()) {
		assert.match(example.issueReports[String(issueNumber)], /pending/i);
		assert.doesNotMatch(example.issueReports[String(issueNumber)], /accepted/i);
	}
});

test('the first preflight failure preserves a 0600 machine-readable non-adoptable report', async () => {
	const { writeRejectedReport } = await import(moduleUrl('rejected-report.mjs'));
	const root = fs.mkdtempSync(path.join('/tmp', 'faithlog-194-current-reject-'));
	try {
		const file = path.join(root, 'baseline-report.json');
		const report = writeRejectedReport(file, {
			phase: 'start-integrity', reasons: ['runtime-input-missing'], queryRunCount: 0,
			composeIdentity: null, databaseIdentity: null, capturedSnapshot: null,
		});
		assert.equal(report.automaticAdoption, false);
		assert.equal(report.queryRunCount, 0);
		assert.equal(fs.statSync(file).mode & 0o777, 0o600);
	} finally {
		fs.rmSync(root, { recursive: true, force: true });
	}
	const runner = readScenario('run-baseline.mjs');
	const allocation = runner.indexOf('allocateReportDirectory');
	const runtimeValidation = runner.indexOf('validateRuntimeInputs()', allocation);
	assert.ok(allocation >= 0 && runtimeValidation > allocation, 'report directory must exist before runtime validation');
});
