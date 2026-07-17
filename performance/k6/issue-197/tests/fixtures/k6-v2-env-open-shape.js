import { check } from 'k6';

const fixture = JSON.parse(open(__ENV.FIXTURE_MANIFEST));
const credentials = JSON.parse(open(__ENV.CREDENTIALS_FILE));

export const options = {
	vus: 1,
	iterations: 1,
};

export default function () {
	check({ fixture, credentials }, {
		'fixture env reached init': ({ fixture: value }) => value.fixtureRunId === __ENV.EXPECTED_FIXTURE_RUN_ID,
		'credential env reached init': ({ credentials: value }) => value.tokens.length === 1,
	});
}
