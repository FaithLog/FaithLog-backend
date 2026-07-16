const dbActivity = JSON.parse(process.env.DB_ACTIVITY_JSON || '{}');
const k6Pid = Number(process.env.K6_PID);
const lsofText = process.env.LSOF_TEXT || '';
const expectedInfrastructureCommands = new Set(['com.docker.backend']);
const expectedHttpInfrastructure = [];
const unexpectedHttpClients = [];
let current = null;
const classify = (client) => {
	if (!client || client.pid === k6Pid) return;
	if (expectedInfrastructureCommands.has(client.command)) expectedHttpInfrastructure.push(client);
	else unexpectedHttpClients.push(client);
};
for (const line of lsofText.split(/\r?\n/)) {
	if (line.startsWith('p')) {
		classify(current);
		current = { pid: Number(line.slice(1)), command: null };
	} else if (line.startsWith('c') && current) {
		current.command = line.slice(1);
	}
}
classify(current);

process.stdout.write(`${JSON.stringify({
	capturedAt: new Date().toISOString(),
	observerApplicationName: 'faithlog_issue196_observer',
	unexpectedDbSessions: Array.isArray(dbActivity.unexpectedSessions) ? dbActivity.unexpectedSessions : [{ invalidEvidence: true }],
	expectedHttpInfrastructure,
	unexpectedHttpClients,
})}\n`);
