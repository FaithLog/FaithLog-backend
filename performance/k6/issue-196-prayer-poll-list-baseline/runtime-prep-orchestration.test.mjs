import assert from 'node:assert/strict';
import { execFileSync, spawnSync } from 'node:child_process';
import { chmodSync, cpSync, existsSync, mkdirSync, mkdtempSync, readFileSync, realpathSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { validateRuntimePrepManifest, validateRuntimePrepRejection } from './runtime-prep-contract.mjs';
import { RUNTIME_TOOLING_FILES } from './tooling-provenance.mjs';

const ROOT = dirname(fileURLToPath(import.meta.url));
const REPOSITORY = realpathSync(join(ROOT, '../../..'));
const PREP = join(ROOT, 'prepare-runtime.sh');

test('fake app-only runtime prep writes one validator-clean manifest without inheriting caller credentials', () => {
	const run = runFakeRuntimePrep();
	try {
		assert.equal(run.result.error, undefined);
		assert.equal(run.result.signal, null);
		assert.equal(run.result.status, 0, run.result.stderr);
		assert.equal(existsSync(run.manifestPath), true);
		const manifestText = readFileSync(run.manifestPath, 'utf8');
		const manifest = JSON.parse(manifestText);
		assert.doesNotThrow(() => validateRuntimePrepManifest(manifest));
		assert.equal(manifest.attemptReceipt.previousApp.containerId, 'app-old-id');
		assert.equal(manifest.attemptReceipt.preservedDatabase.containerId, 'db-id');
		assert.equal(manifest.attemptReceipt.preservedRedis.containerId, 'redis-id');
		assert.equal(manifest.attemptReceipt.toolingAggregateSha256, manifest.tooling.aggregateSha256);
		assert.equal(manifest.environmentAttestation.previousSanitizedSha256, manifest.environmentAttestation.newSanitizedSha256);
		assert.equal(readFileSync(run.calls, 'utf8').trim(), 'compose-child-sanitized');
		assert.doesNotMatch(manifestText, /db-super-secret|jwt-super-secret|firebase-super-secret|caller-token/);
		assert.equal(existsSync(run.rejectionPath), false);
	} finally {
		rmSync(run.temporary, { recursive: true, force: true });
	}
});

test('fake runtime prep rejects unrelated app env drift and preserves one secret-free partial receipt', () => {
	const run = runFakeRuntimePrep({ unrelatedEnvDrift: true });
	try {
		assert.equal(run.result.error, undefined);
		assert.equal(run.result.signal, null);
		assert.notEqual(run.result.status, 0);
		assert.equal(existsSync(run.manifestPath), false);
		assert.equal(existsSync(run.rejectionPath), true, run.result.stderr);
		const rejectionText = readFileSync(run.rejectionPath, 'utf8');
		const rejection = JSON.parse(rejectionText);
		assert.doesNotThrow(() => validateRuntimePrepRejection(rejection));
		assert.equal(rejection.reusable, false);
		assert.equal(rejection.automaticCleanup, false);
		assert.equal(rejection.previousApp.containerId, 'app-old-id');
		assert.equal(rejection.currentApp.containerId, 'app-new-id');
		assert.match(rejection.stage, /environment/i);
		assert.doesNotMatch(rejectionText, /db-super-secret|jwt-super-secret|firebase-super-secret|caller-token/);
	} finally {
		rmSync(run.temporary, { recursive: true, force: true });
	}
});

function runFakeRuntimePrep({ unrelatedEnvDrift = false } = {}) {
	const temporary = realpathSync(mkdtempSync(join(tmpdir(), 'faithlog-196-runtime-prep-')));
	const scenarioWorktree = join(temporary, 'scenario');
	const deployDirectory = join(temporary, 'deploy');
	const bin = join(temporary, 'bin');
	const reportRoot = join(temporary, 'reports');
	const state = join(temporary, 'runtime-state');
	const calls = join(temporary, 'calls.log');
	mkdirSync(scenarioWorktree);
	mkdirSync(deployDirectory);
	mkdirSync(bin);
	for (const relativePath of RUNTIME_TOOLING_FILES) {
		const destination = join(scenarioWorktree, relativePath);
		mkdirSync(dirname(destination), { recursive: true });
		cpSync(join(REPOSITORY, relativePath), destination);
	}
	const scenarioHead = commitRepository(scenarioWorktree, 'scenario tooling');

	const baseCompose = join(deployDirectory, 'docker-compose.yml');
	const baseOverride = join(deployDirectory, 'runtime.override.yml');
	const composeEnv = join(temporary, 'approved-compose.env');
	writeFileSync(baseCompose, 'services:\n  app:\n    image: approved-app-image\n');
	writeFileSync(baseOverride, 'services:\n  app:\n    environment:\n      FAITHLOG_SCHEDULER_ENABLED: "false"\n');
	writeFileSync(composeEnv, 'SPRING_DATASOURCE_PASSWORD=approved-runtime-only\nJWT_SECRET=approved-runtime-only\n', { mode: 0o600 });
	const sourceRevision = commitRepository(deployDirectory, 'deploy source', true);
	writeFileSync(state, 'old\n');

	const evidenceOverride = join(ROOT, 'runtime-evidence.override.yml');
	writeFileSync(join(bin, 'docker'), fakeDocker({ state, calls, deployDirectory, baseCompose, baseOverride, evidenceOverride, unrelatedEnvDrift }));
	writeFileSync(join(bin, 'node'), `#!/usr/bin/env bash\nif [[ "$1" == -e && "$2" == *"await fetch"* ]]; then exit 0; fi\nexec ${JSON.stringify(process.execPath)} "$@"\n`);
	chmodSync(join(bin, 'docker'), 0o755);
	chmodSync(join(bin, 'node'), 0o755);

	const attemptId = unrelatedEnvDrift ? 'i196prepdrift' : 'i196prepsuccess';
	const manifestPath = join(reportRoot, attemptId, 'runtime-prep-manifest.json');
	const result = spawnSync('bash', [PREP], {
		env: {
			...process.env,
			PATH: `${bin}:${process.env.PATH}`,
			PERF_SCENARIO_WORKTREE: scenarioWorktree,
			EXPECTED_SCENARIO_HEAD: scenarioHead,
			PERF_RUNTIME_PREP_ATTEMPT_ID: attemptId,
			PERF_RUNTIME_PREP_REPORT_ROOT: reportRoot,
			PERF_RUNTIME_PREP_MANIFEST: manifestPath,
			PERF_DEPLOY_DIR: deployDirectory,
			PERF_BASE_COMPOSE_FILE: baseCompose,
			PERF_BASE_OVERRIDE_FILE: baseOverride,
			PERF_COMPOSE_ENV_FILE: composeEnv,
			PERF_APP_READY_TIMEOUT_SECONDS: '2',
			BASE_URL: 'http://127.0.0.1:28080',
			APP_CONTAINER: 'approved-app', DB_CONTAINER: 'approved-db', REDIS_CONTAINER: 'approved-redis',
			EXPECTED_COMPOSE_PROJECT: 'approved-project', EXPECTED_APP_SERVICE: 'app',
			EXPECTED_DB_SERVICE: 'postgres', EXPECTED_REDIS_SERVICE: 'redis', EXPECTED_SOURCE_REVISION: sourceRevision,
			EXPECTED_CURRENT_APP_CONTAINER_ID: 'app-old-id', EXPECTED_CURRENT_APP_IMAGE_ID: 'sha256:app-old',
			EXPECTED_CURRENT_APP_STARTED_AT: '2026-07-16T00:00:00.000Z', EXPECTED_CURRENT_APP_CONFIG_HASH: 'app-old-hash',
			EXPECTED_DB_CONTAINER_ID: 'db-id', EXPECTED_DB_IMAGE_ID: 'sha256:db',
			EXPECTED_DB_STARTED_AT: '2026-07-15T00:00:00.000Z', EXPECTED_DB_CONFIG_HASH: 'db-hash',
			EXPECTED_REDIS_CONTAINER_ID: 'redis-id', EXPECTED_REDIS_IMAGE_ID: 'sha256:redis',
			EXPECTED_REDIS_STARTED_AT: '2026-07-15T00:00:00.000Z', EXPECTED_REDIS_CONFIG_HASH: 'redis-hash',
			PERF_ADMIN_PASSWORD: 'caller-secret', PERF_ACCESS_TOKEN: 'caller-token',
			SPRING_DATASOURCE_PASSWORD: 'db-super-secret', JWT_SECRET: 'jwt-super-secret', FIREBASE_CONFIG_BASE64: 'firebase-super-secret',
		},
		encoding: 'utf8', timeout: 15000,
	});
	return { temporary, result, calls, manifestPath, rejectionPath: join(reportRoot, attemptId, 'runtime-prep-rejected.json') };
}

function commitRepository(directory, message, detach = false) {
	execFileSync('git', ['init', '-q'], { cwd: directory });
	execFileSync('git', ['add', '.'], { cwd: directory });
	execFileSync('git', ['-c', 'user.name=Test', '-c', 'user.email=test@example.test', 'commit', '-qm', message], { cwd: directory });
	const head = execFileSync('git', ['rev-parse', 'HEAD'], { cwd: directory, encoding: 'utf8' }).trim();
	if (detach) execFileSync('git', ['checkout', '-q', '--detach', head], { cwd: directory });
	return head;
}

function fakeDocker({ state, calls, deployDirectory, baseCompose, baseOverride, evidenceOverride, unrelatedEnvDrift }) {
	const oldEnv = JSON.stringify([
		'SPRING_PROFILES_ACTIVE=local', 'SPRING_DATASOURCE_PASSWORD=db-super-secret', 'JWT_SECRET=jwt-super-secret',
		'FIREBASE_CONFIG_BASE64=firebase-super-secret', 'FAITHLOG_SCHEDULER_ENABLED=false',
	]);
	const newEnv = JSON.stringify([
		`SPRING_PROFILES_ACTIVE=${unrelatedEnvDrift ? 'prod' : 'local'}`, 'SPRING_DATASOURCE_PASSWORD=db-super-secret',
		'JWT_SECRET=jwt-super-secret', 'FIREBASE_CONFIG_BASE64=firebase-super-secret', 'FAITHLOG_SCHEDULER_ENABLED=false',
		'LOGGING_LEVEL_ORG_HIBERNATE_SQL=DEBUG', 'SPRING_JPA_PROPERTIES_HIBERNATE_FORMAT_SQL=false', 'SPRING_JPA_SHOW_SQL=false',
		'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_BIND=OFF', 'LOGGING_LEVEL_ORG_HIBERNATE_ORM_JDBC_EXTRACT=OFF',
	]);
	return `#!/usr/bin/env bash
set -euo pipefail
runtime_state="$(tr -d '\\n' < ${JSON.stringify(state)})"
if [[ "$1" == compose ]]; then
	if [[ -n "\${PERF_ADMIN_PASSWORD+x}\${PERF_ACCESS_TOKEN+x}\${SPRING_DATASOURCE_PASSWORD+x}\${JWT_SECRET+x}\${FIREBASE_CONFIG_BASE64+x}" ]]; then exit 77; fi
	[[ "$*" == *"--env-file ${join(dirname(baseCompose), '..', 'approved-compose.env')}"* || "$*" == *"--env-file"* ]] || exit 78
	echo compose-child-sanitized > ${JSON.stringify(calls)}
	echo new > ${JSON.stringify(state)}
	exit 0
fi
if [[ "$1" == port ]]; then echo 0.0.0.0:28080; exit 0; fi
if [[ "$1" == image && "$2" == inspect ]]; then echo 2099-01-01T00:00:00.000Z; exit 0; fi
if [[ "$1" != inspect ]]; then exit 90; fi
format="$3"; container="$4"
case "$format|$container" in
	*com.docker.compose.project.config_files*approved-app) if [[ "$runtime_state" == old ]]; then echo ${baseCompose},${baseOverride}; else echo ${baseCompose},${baseOverride},${evidenceOverride}; fi ;;
	*com.docker.compose.project.working_dir*approved-app) echo ${deployDirectory} ;;
	*com.docker.compose.project*approved-*) echo approved-project ;;
	*com.docker.compose.service*approved-app) echo app ;;
	*com.docker.compose.service*approved-db) echo postgres ;;
	*com.docker.compose.service*approved-redis) echo redis ;;
	*com.docker.compose.config-hash*approved-app) if [[ "$runtime_state" == old ]]; then echo app-old-hash; else echo app-new-hash; fi ;;
	*com.docker.compose.config-hash*approved-db) echo db-hash ;;
	*com.docker.compose.config-hash*approved-redis) echo redis-hash ;;
	'{{.Config.Image}}|approved-app') echo approved-app-image ;;
	'{{.Config.Image}}|approved-db') echo postgres:17 ;;
	'{{.Config.Image}}|approved-redis') echo redis:7-alpine ;;
	'{{.Image}}|approved-app') if [[ "$runtime_state" == old ]]; then echo sha256:app-old; else echo sha256:app-new; fi ;;
	'{{.Image}}|approved-db') echo sha256:db ;;
	'{{.Image}}|approved-redis') echo sha256:redis ;;
	'{{.Id}}|approved-app') if [[ "$runtime_state" == old ]]; then echo app-old-id; else echo app-new-id; fi ;;
	'{{.Id}}|approved-db') echo db-id ;;
	'{{.Id}}|approved-redis') echo redis-id ;;
	'{{.State.StartedAt}}|approved-app') if [[ "$runtime_state" == old ]]; then echo 2026-07-16T00:00:00.000Z; else echo 2026-07-16T01:00:00.000Z; fi ;;
	'{{.State.StartedAt}}|approved-db') echo 2026-07-15T00:00:00.000Z ;;
	'{{.State.StartedAt}}|approved-redis') echo 2026-07-15T00:00:00.000Z ;;
	'{{json .Config.Env}}|approved-app') if [[ "$runtime_state" == old ]]; then printf '%s\\n' ${JSON.stringify(oldEnv)}; else printf '%s\\n' ${JSON.stringify(newEnv)}; fi ;;
	*) echo "unexpected fake docker inspect: $format $container" >&2; exit 91 ;;
esac
`;
}
