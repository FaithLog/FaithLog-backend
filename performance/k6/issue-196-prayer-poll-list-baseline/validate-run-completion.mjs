import assert from 'node:assert/strict';
import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { join, relative, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const ENDPOINTS = Object.freeze({
	prayer: ['prayer_current_season', 'prayer_groups', 'prayer_assignable', 'prayer_weekly_board_admin', 'prayer_weekly_board_member'],
	'poll-member': ['poll_member_list', 'poll_member_detail', 'poll_member_results', 'poll_member_comments', 'poll_member_cross_campus_detail', 'poll_member_isolation_campus_detail'],
	'poll-admin': ['poll_admin_list', 'poll_admin_detail', 'poll_admin_results', 'poll_admin_comments', 'poll_admin_missing_members', 'poll_admin_template_list', 'poll_admin_template_detail', 'poll_admin_cross_campus_detail'],
	'poll-duty': ['poll_coffee_creator_list', 'poll_other_coffee_duty_list', 'poll_meal_duty_list', 'poll_coffee_creator_detail', 'poll_meal_duty_detail', 'poll_meal_management_default', 'poll_meal_management_archive', 'poll_meal_management_forbidden'],
});

function collectReportPaths(root, current = root) {
	const paths = [];
	for (const entry of readdirSync(current, { withFileTypes: true })) {
		const absolute = join(current, entry.name);
		if (entry.isDirectory()) paths.push(...collectReportPaths(root, absolute));
		else if (entry.isFile() && entry.name === 'report.json') paths.push(relative(root, absolute));
	}
	return paths.sort();
}

function validateConditionalReport(path) {
	const report = JSON.parse(readFileSync(path, 'utf8'));
	assert.equal(report.measurementStatus, 'conditional-not-adoptable');
	assert.equal(report.accepted, false);
	assert.equal(report.automaticAdoption, false);
	assert.deepEqual(report.rejectionReasons, ['adoption-policy-pending-user-approval']);
}

export function validateRunCompletion(reportRoot, requestedMode) {
	assert.ok(typeof reportRoot === 'string' && reportRoot.length > 0 && existsSync(reportRoot), 'report root is required');
	const modes = requestedMode === 'all' ? Object.keys(ENDPOINTS) : [requestedMode];
	assert.ok(modes.every((mode) => Object.hasOwn(ENDPOINTS, mode)), 'requested mode is invalid');
	const expected = modes.flatMap((mode) => ENDPOINTS[mode].map((endpoint) => join(mode, endpoint, 'report.json'))).sort();
	assert.deepEqual(collectReportPaths(reportRoot), expected, 'requested endpoint report set is incomplete or contains extras');
	for (const path of expected) validateConditionalReport(join(reportRoot, path));
	return { status: 'conditional-complete', requestedMode, endpointCount: expected.length, automaticAdoption: false };
}

function main() {
	const [reportRoot, requestedMode, runnerStatusText] = process.argv.slice(2);
	assert.match(runnerStatusText || '', /^(0|[1-9][0-9]{0,2})$/, 'runner status is required');
	const runnerStatus = Number(runnerStatusText);
	assert.ok(Number.isInteger(runnerStatus) && runnerStatus >= 0 && runnerStatus <= 255, 'runner status is invalid');
	if (runnerStatus !== 2) {
		assert.notEqual(runnerStatus, 0, 'runner status 0 cannot represent conditional completion');
		process.exitCode = runnerStatus;
		return;
	}
	const result = validateRunCompletion(resolve(reportRoot || ''), requestedMode);
	process.stdout.write(`${JSON.stringify(result)}\n`);
	process.exitCode = 2;
}

if (process.argv[1] && fileURLToPath(import.meta.url) === resolve(process.argv[1])) {
	try {
		main();
	} catch {
		process.stderr.write('early rejection or incomplete run; refusing exit-2 success.\n');
		process.exitCode = 1;
	}
}
