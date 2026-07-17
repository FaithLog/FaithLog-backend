import { check, fail } from 'k6';

const TOKEN_KEYS = [
	'admin',
	'member',
	'coffeeCreator',
	'otherCoffeeDuty',
	'mealDuty',
];
const credentials = JSON.parse(open(__ENV.CREDENTIALS_FILE));

export const options = {
	vus: 1,
	iterations: 1,
};

export function setup() {
	if (credentials.schemaVersion !== 1 || credentials.phase !== __ENV.PHASE
		|| Object.keys(credentials.tokens).sort().join(',') !== [...TOKEN_KEYS].sort().join(',')
		|| !TOKEN_KEYS.every((key) => typeof credentials.tokens[key] === 'string' && credentials.tokens[key].length > 0)) {
		fail('invalid synthetic credentials file');
	}
	return { phase: credentials.phase, tokenCount: TOKEN_KEYS.length };
}

export default function (setupData) {
	check(setupData, {
		'credentials reached init without setup token serialization': (value) => value.phase === __ENV.PHASE && value.tokenCount === 5,
	});
}
