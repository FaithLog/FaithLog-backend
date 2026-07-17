import {validateLoginContract} from './auth-contract.mjs';

const baseUrl = required('BASE_URL').replace(/\/$/, '');
const email = required('PERF_LOGIN_EMAIL');
const password = required('PERF_LOGIN_PASSWORD');
const requesterUserId = required('EXPECTED_USER_ID');
const identityLabel = required('IDENTITY_LABEL');
const minimumTtlSeconds = Number(required('MIN_TOKEN_TTL_SECONDS'));

const response = await fetch(`${baseUrl}/api/v1/auth/login`, {
	method: 'POST',
	headers: {'Content-Type': 'application/json'},
	body: JSON.stringify({email, password}),
});
const body = await parseJson(response);
if (response.status !== 200 || body.success !== true || !body.data) {
	throw new Error(`Runtime ${identityLabel} login failed with status ${response.status}.`);
}
const {accessToken} = validateLoginContract(body.data, requesterUserId, minimumTtlSeconds);
process.stdout.write(accessToken);

async function parseJson(fetchResponse) {
	try {
		return await fetchResponse.json();
	} catch (_error) {
		return {};
	}
}

function required(name) {
	const value = process.env[name];
	if (!value) {
		throw new Error(`${name} is required for authentication.`);
	}
	return value;
}
