import {validateTokenRemaining} from './auth-contract.mjs';

const accessToken = process.env.PERF_ACCESS_TOKEN;
const minimumTtlSeconds = Number(process.env.MIN_TOKEN_TTL_SECONDS);
if (!accessToken || !Number.isFinite(minimumTtlSeconds)) {
	throw new Error('PERF_ACCESS_TOKEN and MIN_TOKEN_TTL_SECONDS are required.');
}
validateTokenRemaining(accessToken, minimumTtlSeconds);
