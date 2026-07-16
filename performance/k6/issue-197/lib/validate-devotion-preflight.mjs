import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { readJson, validateDevotionManifest } from './fixture-contract.mjs';

export function validatePreflight(manifest, evidence) {
	validateDevotionManifest(manifest);
	const successUsers = manifest.warmupUserIds.length + manifest.measuredUserIds.length;
	const totalUsers = successUsers + manifest.rollbackUserIds.length;
	const expected = {
		distinctFixtureUsers: totalUsers,
		activeFixtureUsers: totalUsers,
		activeCampuses: 2,
		successActiveMembers: successUsers,
		rollbackActiveMembers: manifest.rollbackUserIds.length,
		successUsersInRollbackCampus: 0,
		rollbackUsersInSuccessCampus: 0,
		successActivePenaltyAccounts: 1,
		rollbackActivePenaltyAccounts: 0,
		existingWeeklyCount: 0,
		existingDailyCount: 0,
		existingDevotionCharges: 0,
		activePenaltyRuleCount: 4,
		invalidActivePenaltyRulePairs: 0,
		calculatedPenaltyAmount: manifest.expectedPenaltyAmount,
	};
	const failures = [];
	for (const [name, value] of Object.entries(expected)) {
		if (Number(evidence[name]) !== value) failures.push({ name, expected: value, actual: evidence[name] });
	}
	if (failures.length > 0) throw new Error(`devotion preflight contract failed: ${JSON.stringify(failures)}`);
	return evidence;
}

async function main() {
	const [manifestPath, evidencePath] = process.argv.slice(2);
	const result = validatePreflight(readJson(manifestPath, 'manifest'), readJson(evidencePath, 'preflight evidence'));
	process.stdout.write(`${JSON.stringify({ status: 'preflight-valid', evidence: result })}\n`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
	main().catch((error) => {
		process.stderr.write(`${error.message}\n`);
		process.exitCode = 1;
	});
}
