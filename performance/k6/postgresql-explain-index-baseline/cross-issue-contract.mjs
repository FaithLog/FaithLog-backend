import { createHash } from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';

const REQUIRED_ISSUES = [192, 193, 195, 196, 197, 198, 199];

export function validateCrossIssueArtifacts({
	crossIssueReportPath, issueReports, datasetId, fixtureRunId, memberCount,
}) {
	if (!issueReports || typeof issueReports !== 'object' || Array.isArray(issueReports)) {
		throw new Error('CROSS_ISSUE_REPORT issueReports must be an object.');
	}
	const root = path.dirname(path.resolve(crossIssueReportPath));
	const rootReal = fs.realpathSync(root);
	const seen = new Set();
	const artifacts = REQUIRED_ISSUES.map((issueNumber) => {
		const relativePath = issueReports[String(issueNumber)];
		if (typeof relativePath !== 'string' || relativePath.length === 0 || path.isAbsolute(relativePath)) {
			throw new Error(`issueReports.${issueNumber} must be a non-empty relative path.`);
		}
		const segments = relativePath.split(/[\\/]+/);
		if (segments.includes('..') || segments.includes('.')) {
			throw new Error(`issueReports.${issueNumber} path traversal is forbidden.`);
		}
		const absolutePath = path.resolve(root, relativePath);
		if (!isInside(root, absolutePath)) {
			throw new Error(`issueReports.${issueNumber} resolves outside the cross-report directory.`);
		}
		rejectSymlinkComponents(root, segments, issueNumber);
		let descriptor;
		try {
			descriptor = fs.openSync(absolutePath, fs.constants.O_RDONLY | fs.constants.O_NOFOLLOW);
		} catch (error) {
			throw new Error(`issueReports.${issueNumber} cannot be opened as a non-symlink artifact.`, { cause: error });
		}
		const stat = fs.fstatSync(descriptor);
		if (!stat.isFile()) {
			fs.closeSync(descriptor);
			throw new Error(`issueReports.${issueNumber} must be a regular file.`);
		}
		const bytes = fs.readFileSync(descriptor);
		fs.closeSync(descriptor);
		const realPath = fs.realpathSync(absolutePath);
		const realStat = fs.statSync(realPath);
		if (!isInside(rootReal, realPath)) {
			throw new Error(`issueReports.${issueNumber} real path resolves outside the cross-report directory.`);
		}
		if (stat.dev !== realStat.dev || stat.ino !== realStat.ino) {
			throw new Error(`issueReports.${issueNumber} path identity changed while it was validated.`);
		}
		if (seen.has(realPath)) {
			throw new Error(`issueReports contains duplicate artifact path: ${relativePath}`);
		}
		seen.add(realPath);
		let artifact;
		try {
			artifact = JSON.parse(bytes.toString('utf8'));
		} catch (error) {
			throw new Error(`issueReports.${issueNumber} is not valid JSON.`, { cause: error });
		}
		validateArtifactSchema(artifact, { issueNumber, datasetId, fixtureRunId, memberCount });
		return {
			issueNumber,
			relativePath: path.relative(root, realPath),
			absolutePath: realPath,
			sha256: createHash('sha256').update(bytes).digest('hex'),
			artifact,
		};
	});
	for (const entry of artifacts) {
		const acceptance = classifyIssueArtifactAcceptance(entry.issueNumber, entry.artifact);
		if (acceptance.pendingApprovalContract || !acceptance.accepted) {
			throw new Error(acceptance.reason);
		}
	}
	return artifacts;
}

function rejectSymlinkComponents(root, segments, issueNumber) {
	let current = root;
	for (const segment of segments) {
		current = path.join(current, segment);
		let stat;
		try {
			stat = fs.lstatSync(current);
		} catch (error) {
			throw new Error(`issueReports.${issueNumber} does not exist.`, { cause: error });
		}
		if (stat.isSymbolicLink()) {
			throw new Error(`issueReports.${issueNumber} must not contain a symbolic link (symlink).`);
		}
	}
}

function validateArtifactSchema(artifact, expected) {
	if (!artifact || typeof artifact !== 'object' || Array.isArray(artifact)) {
		throw new Error(`Issue ${expected.issueNumber} artifact must be a JSON object.`);
	}
	for (const field of ['issueNumber', 'datasetId', 'fixtureRunId', 'memberCount', 'status', 'expectedAnchors']) {
		if (!Object.hasOwn(artifact, field)) {
			throw new Error(`Issue ${expected.issueNumber} artifact schema is missing ${field}.`);
		}
	}
	for (const field of ['issueNumber', 'datasetId', 'fixtureRunId', 'memberCount']) {
		if (artifact[field] !== expected[field]) {
			throw new Error(`Issue ${expected.issueNumber} artifact ${field} does not match the approved cross-report identity.`);
		}
	}
	if (!artifact.expectedAnchors || typeof artifact.expectedAnchors !== 'object' || Array.isArray(artifact.expectedAnchors)) {
		throw new Error(`Issue ${expected.issueNumber} artifact expectedAnchors must be an object.`);
	}
}

export function classifyIssueArtifactAcceptance(issueNumber, artifact) {
	void artifact;
	const knownIssue = REQUIRED_ISSUES.includes(issueNumber);
	return {
		accepted: false,
		pendingApprovalContract: knownIssue,
		reason: knownIssue
			? `issue-${issueNumber}-approval-contract-pending: current artifacts have automaticAdoption=false and no separate user/PM approval evidence schema.`
			: `Issue ${issueNumber} is outside the #194 cross-issue contract.`,
	};
}

function isInside(root, candidate) {
	const relative = path.relative(root, candidate);
	return relative === '' || (!relative.startsWith(`..${path.sep}`) && relative !== '..' && !path.isAbsolute(relative));
}
