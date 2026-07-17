import fs from 'node:fs';
import path from 'node:path';

export function reserveFixtureReportDirectory({ reportRoot, datasetId, fixtureRunId }) {
	const directory = path.join(path.resolve(reportRoot), datasetId, fixtureRunId);
	fs.mkdirSync(path.dirname(directory), { recursive: true });
	try {
		fs.mkdirSync(directory);
	} catch (error) {
		if (error?.code === 'EEXIST') {
			throw new Error(`Fixture report directory already exists; fixtureRunId is not reusable: ${directory}`);
		}
		throw error;
	}
	return directory;
}

export function createFixtureRejectionRecorder({
	directory,
	datasetId,
	fixtureRunId,
	getMutationCounts,
	getRefreshCount,
}) {
	return (stage) => {
		const rejection = {
			schemaVersion: 1,
			status: 'partial-non-reusable',
			automaticAdoption: false,
			reusable: false,
			automaticCleanup: false,
			datasetId,
			fixtureRunId,
			stage,
			mutationCounts: normalizeCounts(getMutationCounts()),
			refreshCount: normalizeCount(getRefreshCount()),
		};
		try {
			writeExclusive(path.join(directory, 'first-rejection.json'), rejection);
		} catch (error) {
			if (error?.code !== 'EEXIST') throw error;
		}
	};
}

export function writeFixtureManifest(directory, manifest) {
	const manifestPath = path.join(directory, 'fixture-manifest.json');
	writeExclusive(manifestPath, manifest);
	return manifestPath;
}

function writeExclusive(filePath, value) {
	fs.writeFileSync(filePath, `${JSON.stringify(value, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
}

function normalizeCounts(value) {
	return {
		campuses: normalizeCount(value?.campuses),
		memberships: normalizeCount(value?.memberships),
		duties: normalizeCount(value?.duties),
	};
}

function normalizeCount(value) {
	return Number.isSafeInteger(value) && value >= 0 ? value : 0;
}
