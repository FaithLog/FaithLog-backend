import { readFileSync, writeFileSync } from 'node:fs';
import { resolve } from 'node:path';
import { requireExactBaseUrl, requireTokenCoverage } from './scenario-contract.js';

const target = JSON.parse(readFileSync(resolve(required('TARGET_CONTRACT')), 'utf8'));
const manifest = JSON.parse(readFileSync(resolve(required('MANIFEST_PATH')), 'utf8'));
const baseUrl = requireExactBaseUrl(required('BASE_URL'));
const mode = required('MODE');
const output = resolve(required('TOKEN_OUTPUT'));
const password = required('PERF_PASSWORD');
const safetySeconds = positiveInteger(required('TOKEN_EXPIRY_SAFETY_SECONDS'), 'TOKEN_EXPIRY_SAFETY_SECONDS');
const maxDurationSeconds = mode.endsWith('-sequential') ? 1620 : mode.endsWith('-concurrent') ? 840 : fail('Invalid MODE.');
if (baseUrl !== target.baseUrl || manifest.baseUrl !== baseUrl) throw new Error('Token target mismatch.');
if (manifest.datasetId !== required('PERF_DATASET_ID') || manifest.fixtureRunId !== required('PERF_FIXTURE_RUN_ID')) throw new Error('Token fixture case mismatch.');

const response = await fetch(`${baseUrl}/api/v1/auth/login`, { method: 'POST', headers: { 'Content-Type': 'application/json' }, body: JSON.stringify({ email: manifest.actorEmail, password }) });
const body = await response.json(); const token = body.data?.accessToken;
if (response.status !== 200 || typeof token !== 'string' || !token) throw new Error(`Measured token login failed: ${response.status}`);
const parts = token.split('.');
if (parts.length !== 3) throw new Error('Measured JWT shape is invalid.');
let payload;
try { payload = JSON.parse(Buffer.from(parts[1], 'base64url').toString('utf8')); } catch { throw new Error('Measured JWT payload is invalid.'); }
requireTokenCoverage(payload.exp, Date.now(), maxDurationSeconds, safetySeconds);
writeFileSync(output, token, { flag: 'wx', mode: 0o600 });

function positiveInteger(value, label) { if (!/^[1-9]\d*$/.test(value)) throw new Error(`${label} must be a positive integer.`); const parsed = Number(value); if (!Number.isSafeInteger(parsed)) throw new Error(`${label} is unsafe.`); return parsed; }
function required(name) { const value = process.env[name]; if (!value) throw new Error(`${name} is required.`); return value; }
function fail(message) { throw new Error(message); }
