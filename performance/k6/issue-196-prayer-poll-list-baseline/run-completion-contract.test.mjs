import assert from 'node:assert/strict';
import { mkdirSync, mkdtempSync, rmSync, writeFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join } from 'node:path';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { spawnSync } from 'node:child_process';

const ROOT = dirname(fileURLToPath(import.meta.url));
const VALIDATOR = join(ROOT, 'validate-run-completion.mjs');
const PRAYER = ['prayer_current_season', 'prayer_groups', 'prayer_assignable', 'prayer_weekly_board_admin', 'prayer_weekly_board_member'];

test('exit 2 is successful only when every requested endpoint has a conditional-complete report', () => {
	const directory = mkdtempSync(join(tmpdir(), 'faithlog-196-run-completion-'));
	try {
		writeReport(directory, 'prayer', PRAYER[0], 'rejected');
		const early = spawnSync(process.execPath, [VALIDATOR, directory, 'prayer'], { encoding: 'utf8' });
		assert.equal(early.status, 1);
		assert.match(early.stderr, /early rejection or incomplete run/i);

		rmSync(directory, { recursive: true, force: true });
		mkdirSync(directory);
		for (const endpoint of PRAYER) writeReport(directory, 'prayer', endpoint, 'conditional-not-adoptable');
		const complete = spawnSync(process.execPath, [VALIDATOR, directory, 'prayer'], { encoding: 'utf8' });
		assert.equal(complete.status, 0, complete.stderr);
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
