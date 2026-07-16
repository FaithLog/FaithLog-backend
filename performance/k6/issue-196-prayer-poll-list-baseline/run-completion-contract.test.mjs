import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const ROOT = dirname(fileURLToPath(import.meta.url));
const VALIDATOR = join(ROOT, 'validate-run-completion.mjs');
const ENDPOINTS = {
	prayer: ['prayer_current_season', 'prayer_groups', 'prayer_assignable', 'prayer_weekly_board_admin', 'prayer_weekly_board_member'],
	'poll-member': ['poll_member_list', 'poll_member_detail', 'poll_member_results', 'poll_member_comments', 'poll_member_cross_campus_detail', 'poll_member_isolation_campus_detail'],
	'poll-admin': ['poll_admin_list', 'poll_admin_detail', 'poll_admin_results', 'poll_admin_comments', 'poll_admin_missing_members', 'poll_admin_template_list', 'poll_admin_template_detail', 'poll_admin_cross_campus_detail'],
	'poll-duty': ['poll_coffee_creator_list', 'poll_other_coffee_duty_list', 'poll_meal_duty_list', 'poll_coffee_creator_detail', 'poll_meal_duty_detail', 'poll_meal_management_default', 'poll_meal_management_archive', 'poll_meal_management_forbidden'],
};

test('exit 2 is successful only when every requested endpoint has a conditional-complete report', () => {
	const directory = mkdtempSync(join(tmpdir(), 'faithlog-196-run-completion-'));
	try {
		writeReport(directory, 'prayer', ENDPOINTS.prayer[0], 'rejected');
		const early = spawnSync(process.execPath, [VALIDATOR, directory, 'prayer', '2'], { encoding: 'utf8' });
		assert.equal(early.status, 1);
		assert.match(early.stderr, /early rejection or incomplete run/i);

		rmSync(directory, { recursive: true, force: true });
		mkdirSync(directory);
		for (const endpoint of ENDPOINTS.prayer) writeReport(directory, 'prayer', endpoint, 'conditional-not-adoptable');
		const complete = spawnSync(process.execPath, [VALIDATOR, directory, 'prayer', '2'], { encoding: 'utf8' });
		assert.equal(complete.status, 2, complete.stderr);

		const operational = spawnSync(process.execPath, [VALIDATOR, directory, 'prayer', '42'], { encoding: 'utf8' });
		assert.equal(operational.status, 42, 'operational runner failure must not be swallowed by conditional JSON');
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
});

test('all mode requires exactly 27 clean conditional reports before preserving runner exit 2', () => {
	const directory = mkdtempSync(join(tmpdir(), 'faithlog-196-all-completion-'));
	try {
		for (const [mode, endpoints] of Object.entries(ENDPOINTS)) {
			for (const endpoint of endpoints) writeReport(directory, mode, endpoint, 'conditional-not-adoptable');
		}
		const complete = spawnSync(process.execPath, [VALIDATOR, directory, 'all', '2'], { encoding: 'utf8' });
		assert.equal(complete.status, 2, complete.stderr);
		assert.match(complete.stdout, /"endpointCount":27/);

		writeReport(directory, 'poll-duty', ENDPOINTS['poll-duty'].at(-1), 'rejected');
		const rejected = spawnSync(process.execPath, [VALIDATOR, directory, 'all', '2'], { encoding: 'utf8' });
		assert.equal(rejected.status, 1);
	} finally {
		rmSync(directory, { recursive: true, force: true });
	}
});

function writeReport(root, mode, endpoint, measurementStatus) {
	const directory = join(root, mode, endpoint);
	mkdirSync(directory, { recursive: true });
	writeFileSync(join(directory, 'report.json'), JSON.stringify({
		measurementStatus, accepted: false, automaticAdoption: false,
		rejectionReasons: measurementStatus === 'conditional-not-adoptable' ? ['adoption-policy-pending-user-approval'] : ['missing-sql-evidence'],
	}));
}
