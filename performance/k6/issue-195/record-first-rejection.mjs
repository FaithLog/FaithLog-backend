import fs from 'node:fs';

const [outputPath, stage, scenarioInput, caseInput, exitCodeInput] = process.argv.slice(2);
if (!outputPath || !stage || !exitCodeInput) {
	throw new Error('outputPath, stage, and exitCode are required.');
}
if (!/^[a-z0-9_-]+$/.test(stage)) throw new Error('stage has an invalid format.');
const optionalIdentity = (value, label) => {
	if (!value) return null;
	if (!/^[a-z0-9_-]+$/.test(value)) throw new Error(`${label} has an invalid format.`);
	return value;
};
const exitCode = Number(exitCodeInput);
if (!Number.isSafeInteger(exitCode) || exitCode <= 0 || exitCode > 255) {
	throw new Error('exitCode must be an integer from 1 through 255.');
}
const rejection = {
	schemaVersion: 1,
	status: 'non-adoptable',
	automaticAdoption: false,
	stage,
	scenario: optionalIdentity(scenarioInput, 'scenario'),
	case: optionalIdentity(caseInput, 'case'),
	exitCode,
};
try {
	fs.writeFileSync(outputPath, `${JSON.stringify(rejection, null, 2)}\n`, { flag: 'wx', mode: 0o600 });
} catch (error) {
	if (error?.code !== 'EEXIST') throw error;
}
