import http from 'k6/http';
import { check } from 'k6';

const TOKEN_NAMES = [
	'PERF_ADMIN_ACCESS_TOKEN',
	'PERF_MEMBER_ACCESS_TOKEN',
	'PERF_COFFEE_CREATOR_ACCESS_TOKEN',
	'PERF_OTHER_COFFEE_DUTY_ACCESS_TOKEN',
	'PERF_MEAL_DUTY_ACCESS_TOKEN',
];

export const options = {
	vus: 1,
	iterations: 1,
};

export function setup() {
	return Object.fromEntries(TOKEN_NAMES.map((name) => [name, __ENV[name] || login(name)]));
}

export default function (tokens) {
	check(tokens, {
		'all explicit tokens reached setup': (value) => TOKEN_NAMES.every((name) => value[name] === `sentinel-${name}`),
	});
}

function login(name) {
	return http.post(`${__ENV.BASE_URL}/synthetic-login`, JSON.stringify({ name })).body;
}
