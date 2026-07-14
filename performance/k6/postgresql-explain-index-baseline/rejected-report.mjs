import fs from 'node:fs';

export function writeRejectedReport(filePath, {
	phase, reasons, queryRunCount, composeIdentity, databaseIdentity, capturedSnapshot,
	schemaState = null, sourceIdentity = null, datasetId = null, fixtureRunId = null,
	crossIssueArtifacts = [], activityWindows = [], activitySampleIntervalMs = null, schemaVersion = 2,
}) {
	const safePhase = phase === 'start-integrity' ? 'start-integrity' : 'runtime-failure';
	const report = {
		schemaVersion,
		status: `invalid-pending-${safePhase}`,
		datasetId,
		fixtureRunId,
		activitySampleIntervalMs,
		reasons: sanitizeReasons(reasons),
		queryRunCount: Number.isSafeInteger(queryRunCount) && queryRunCount >= 0 ? queryRunCount : 0,
		composeIdentity: composeIdentity ?? null,
		databaseIdentity: databaseIdentity ?? null,
		capturedSnapshot: capturedSnapshot ?? null,
		schemaState,
		sourceIdentity,
		crossIssueArtifacts: crossIssueArtifacts.map(({ issueNumber, relativePath, sha256 }) => ({ issueNumber, relativePath, sha256 })),
		activityWindows,
		credentialsRecorded: false,
		rejectedAt: new Date().toISOString(),
	};
	fs.writeFileSync(filePath, `${JSON.stringify(report, null, 2)}\n`, { mode: 0o600 });
	fs.chmodSync(filePath, 0o600);
	return report;
}

function sanitizeReasons(reasons) {
	return (Array.isArray(reasons) ? reasons : ['unspecified-integrity-failure'])
		.map((reason) => String(reason))
		.filter((reason) => /^[a-z0-9][a-z0-9:._-]*$/i.test(reason))
		.slice(0, 100);
}
