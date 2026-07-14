const dbActivity = JSON.parse(process.env.DB_ACTIVITY_JSON || '{}');
const k6Pid = Number(process.env.K6_PID);
const lsofText = process.env.LSOF_TEXT || '';
const allowedCommands = new Set(['com.docker', 'Docker', 'vpnkit']);
const unexpectedHttpClients = [];
let current = null;
for (const line of lsofText.split(/\r?\n/)) {
	if (line.startsWith('p')) {
		if (current && current.pid !== k6Pid && !allowedCommands.has(current.command)) unexpectedHttpClients.push(current);
		current = { pid: Number(line.slice(1)), command: null };
	} else if (line.startsWith('c') && current) {
		current.command = line.slice(1);
	}
}
if (current && current.pid !== k6Pid && !allowedCommands.has(current.command)) unexpectedHttpClients.push(current);

process.stdout.write(`${JSON.stringify({
	capturedAt: new Date().toISOString(),
	observerApplicationName: 'faithlog_issue196_observer',
	unexpectedDbSessions: Array.isArray(dbActivity.unexpectedSessions) ? dbActivity.unexpectedSessions : [{ invalidEvidence: true }],
	unexpectedHttpClients,
})}\n`);
