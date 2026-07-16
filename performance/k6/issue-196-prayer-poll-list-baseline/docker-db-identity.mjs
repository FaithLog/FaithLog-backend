import { execFileSync } from 'node:child_process';

export function captureDockerDbIdentity({
	dockerCommand = 'docker',
	container,
	user,
	database,
	password,
	applicationName,
	sql,
	env,
}) {
	for (const [name, value] of Object.entries({ container, user, database, password, applicationName, sql })) {
		if (typeof value !== 'string' || value.length === 0) throw new Error(`DB identity collector requires ${name}.`);
	}
	if (!env || typeof env !== 'object' || Array.isArray(env)) throw new Error('DB identity collector requires an explicit child environment.');

	let output;
	try {
		output = execFileSync(dockerCommand, [
			'exec', '-i', '-e', 'PGPASSWORD', '-e', `PGAPPNAME=${applicationName}`, container,
			'psql', '-X', '-v', 'ON_ERROR_STOP=1', '-h', '127.0.0.1', '-U', user, '-d', database, '-At',
			'-f', '-',
		], {
			encoding: 'utf8',
			input: sql,
			env: { ...env, PGPASSWORD: password },
			stdio: ['pipe', 'pipe', 'pipe'],
		}).trim();
	} catch {
		throw new Error('DB identity collector child failed.');
	}

	if (output.length === 0) throw new Error('DB identity collector returned empty output.');
	try {
		return JSON.parse(output);
	} catch {
		throw new Error('DB identity collector returned invalid JSON.');
	}
}
