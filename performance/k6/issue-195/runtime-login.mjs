const BASE_URL = process.env.BASE_URL?.replace(/\/$/, '');
const ADMIN_EMAIL = process.env.PERF_ADMIN_EMAIL;
const ADMIN_PASSWORD = process.env.PERF_ADMIN_PASSWORD;

if (!BASE_URL || !ADMIN_EMAIL || !ADMIN_PASSWORD) {
	throw new Error('BASE_URL, PERF_ADMIN_EMAIL, and PERF_ADMIN_PASSWORD are required at runtime.');
}
if (!/^https?:\/\/(localhost|127\.0\.0\.1|\[::1\]|host\.docker\.internal|faithlog-backend|app)(?::\d+)?($|\/)/.test(BASE_URL)) {
	throw new Error('Issue #195 runtime login is local Docker only.');
}

const response = await fetch(`${BASE_URL}/api/v1/auth/login`, {
	method: 'POST',
	headers: { 'Content-Type': 'application/json' },
	body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
});
const text = await response.text();
const body = text ? JSON.parse(text) : null;
if (!response.ok || body?.success === false || !body?.data?.accessToken) {
	throw new Error(`Runtime admin login failed with status ${response.status}.`);
}

process.stdout.write(body.data.accessToken);
