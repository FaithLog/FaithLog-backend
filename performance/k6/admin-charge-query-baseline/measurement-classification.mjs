import {pathToFileURL} from 'node:url';

export function classifySharedStackEvidence({
	externalActivityDeclaration,
	transientExternalActivityObserved = false,
}) {
	if (externalActivityDeclaration !== 'none') {
		throw new Error('EXTERNAL_ACTIVITY must be the explicit approved value none.');
	}
	if (typeof transientExternalActivityObserved !== 'boolean') {
		throw new Error('Transient external activity observation must be boolean.');
	}
	return {
		measurementStatus: transientExternalActivityObserved ? 'invalid-shared-stack' : 'conditional-shared-stack',
		evidenceIntegrity: 'validated-separately',
		automaticAdoption: false,
		requiresPmReview: true,
		externalActivityDeclaration,
		provenance: 'boundary-observation-only',
		note: 'PM must verify the exclusive-use window and supporting evidence before any baseline adoption.',
	};
}

if (process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href) {
	const classification = classifySharedStackEvidence({
		externalActivityDeclaration: process.env.EXTERNAL_ACTIVITY,
		transientExternalActivityObserved: false,
	});
	process.stdout.write(`${JSON.stringify(classification, null, 2)}\n`);
}
