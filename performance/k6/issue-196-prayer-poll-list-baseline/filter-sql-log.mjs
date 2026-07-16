import { createReadStream, createWriteStream, mkdtempSync, rmSync } from 'node:fs';
import { once } from 'node:events';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { createInterface } from 'node:readline';
import { finished } from 'node:stream/promises';

const SQL_MARKER = 'org.hibernate.SQL';
const FORBIDDEN_VALUE_LOGGER = /org\.hibernate\.orm\.jdbc\.(?:bind|extract)|org\.hibernate\.type\.descriptor\.sql\.(?:BasicBinder|BasicExtractor)/;
const temporaryDirectory = mkdtempSync(join(tmpdir(), 'faithlog-196-sql-filter-'));
const statementSpool = join(temporaryDirectory, 'statements.log');

try {
	const spool = createWriteStream(statementSpool, { flags: 'wx', mode: 0o600 });
	const lines = createInterface({ input: process.stdin, crlfDelay: Infinity });
	let forbiddenValueLoggerObserved = false;

	for await (const line of lines) {
		if (FORBIDDEN_VALUE_LOGGER.test(line)) {
			forbiddenValueLoggerObserved = true;
			continue;
		}
		if (!forbiddenValueLoggerObserved && line.includes(SQL_MARKER)) {
			if (!spool.write(`${line}\n`)) await once(spool, 'drain');
		}
	}
	spool.end();
	await finished(spool);

	if (forbiddenValueLoggerObserved) {
		console.error('Forbidden Hibernate bind/extract logger output was observed; SQL evidence was not persisted.');
		process.exitCode = 2;
	} else {
		const statements = createReadStream(statementSpool);
		for await (const chunk of statements) {
			if (!process.stdout.write(chunk)) await once(process.stdout, 'drain');
		}
	}
} catch {
	console.error('SQL evidence filtering failed.');
	process.exitCode = 1;
} finally {
	rmSync(temporaryDirectory, { recursive: true, force: true });
}
