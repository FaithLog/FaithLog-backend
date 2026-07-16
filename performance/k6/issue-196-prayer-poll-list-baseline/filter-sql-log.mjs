import { readFileSync } from 'node:fs';

const SQL_MARKER = 'org.hibernate.SQL';
const FORBIDDEN_VALUE_LOGGER = /org\.hibernate\.orm\.jdbc\.(?:bind|extract)|org\.hibernate\.type\.descriptor\.sql\.(?:BasicBinder|BasicExtractor)/;
const input = readFileSync(0, 'utf8');
const lines = input.split(/\r?\n/).filter(Boolean);

if (lines.some((line) => FORBIDDEN_VALUE_LOGGER.test(line))) {
	console.error('Forbidden Hibernate bind/extract logger output was observed; SQL evidence was not persisted.');
	process.exit(2);
}

const statements = lines.filter((line) => line.includes(SQL_MARKER));
if (statements.length > 0) {
	process.stdout.write(`${statements.join('\n')}\n`);
}
