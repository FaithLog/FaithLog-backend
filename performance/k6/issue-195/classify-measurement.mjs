import fs from 'node:fs';

const [outputPath] = process.argv.slice(2);
if (!outputPath) throw new Error('outputPath is required.');

const classification = {
	status: 'conditional-not-adoptable',
	automaticAdoption: false,
	requiresUserApprovedExclusiveProvenance: true,
	reason: 'boundary evidence and a cooperative lock do not prove absence of transient external load',
};

fs.writeFileSync(outputPath, `${JSON.stringify(classification, null, 2)}\n`);
process.exitCode = 2;
