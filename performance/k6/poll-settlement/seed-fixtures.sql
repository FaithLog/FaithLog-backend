\set ON_ERROR_STOP on

BEGIN;

SELECT 1 / (NOT EXISTS (SELECT 1 FROM campuses WHERE name = :'dataset_id'))::integer;

SELECT id AS actor_id FROM users WHERE email = :'actor_email' \gset

UPDATE users SET role = 'ADMIN', updated_at = now()
WHERE id = :actor_id AND email = :'actor_email';

INSERT INTO campuses (name, region, description, invite_code, is_active, created_at, updated_at)
VALUES (:'dataset_id', 'PERFORMANCE', 'Issue #192 additive shared develop fixture', :'invite_code', TRUE, now(), now())
RETURNING id AS campus_id \gset

INSERT INTO users (name, email, password_hash, role, is_active, token_version, created_at, updated_at)
SELECT :'dataset_id' || '_MEMBER_' || lpad(series::text, 4, '0'),
	lower(:'dataset_id') || '-member-' || lpad(series::text, 4, '0') || '@example.invalid',
	actor.password_hash, 'USER', TRUE, 0, now(), now()
FROM generate_series(2, :member_count) AS series
CROSS JOIN (SELECT password_hash FROM users WHERE id = :actor_id) actor;

CREATE TEMP TABLE perf_users ON COMMIT DROP AS
SELECT id, row_number() OVER (ORDER BY id) AS member_number
FROM users
WHERE id = :actor_id OR email LIKE lower(:'dataset_id') || '-member-%@example.invalid';

SELECT 1 / ((SELECT count(*) FROM perf_users) = :member_count)::integer;
SELECT id AS observer_id FROM perf_users WHERE member_number = 2 \gset

INSERT INTO campus_members (campus_id, user_id, campus_role, status, joined_at, created_at, updated_at)
SELECT :campus_id, id, CASE WHEN id = :actor_id THEN 'MINISTER' ELSE 'MEMBER' END,
	'ACTIVE', now(), now(), now()
FROM perf_users;

INSERT INTO campus_duty_assignments
	(campus_id, user_id, duty_type, is_active, assigned_at, created_at, updated_at)
VALUES
	(:campus_id, :actor_id, 'COFFEE', TRUE, now(), now(), now()),
	(:campus_id, :observer_id, 'COFFEE', TRUE, now(), now(), now()),
	(:campus_id, :actor_id, 'MEAL', TRUE, now(), now(), now());

INSERT INTO payment_accounts
	(campus_id, account_type, nickname, bank_name, account_number, account_holder,
	 owner_user_id, is_active, created_at, updated_at)
VALUES
	(:campus_id, 'COFFEE', :'dataset_id' || '_COFFEE', 'PERFORMANCE_BANK',
	 'PERFORMANCE_NOT_A_REAL_ACCOUNT_COFFEE', 'PERFORMANCE', :actor_id, TRUE, now(), now())
RETURNING id AS coffee_account_id \gset

INSERT INTO payment_accounts
	(campus_id, account_type, nickname, bank_name, account_number, account_holder,
	 owner_user_id, is_active, created_at, updated_at)
VALUES
	(:campus_id, 'MEAL', :'dataset_id' || '_MEAL', 'PERFORMANCE_BANK',
	 'PERFORMANCE_NOT_A_REAL_ACCOUNT_MEAL', 'PERFORMANCE', :actor_id, TRUE, now(), now())
RETURNING id AS meal_account_id \gset

SELECT 1 / (NOT EXISTS (
	SELECT 1 FROM polls WHERE campus_id = :campus_id
	AND title LIKE :'dataset_id' || '|' || :'fixture_run_id' || '|%'
))::integer;

CREATE TEMP TABLE perf_poll_specs (
	poll_type text NOT NULL,
	fixture_group text NOT NULL,
	ordinal integer NOT NULL,
	title text NOT NULL,
	PRIMARY KEY (poll_type, fixture_group, ordinal)
) ON COMMIT DROP;

INSERT INTO perf_poll_specs (poll_type, fixture_group, ordinal, title)
SELECT poll_type, fixture_group, ordinal,
	:'dataset_id' || '|' || :'fixture_run_id' || '|' || poll_type || '|' || fixture_group || '|' || lpad(ordinal::text, 3, '0')
FROM (
	SELECT poll_type, fixture_group, generate_series(1, fixture_count) ordinal
	FROM (VALUES
		('COFFEE', 'sequentialWarmup', 1), ('COFFEE', 'sequentialMeasured', 10),
		('COFFEE', 'concurrentWarmup', 1), ('COFFEE', 'concurrentMeasured', 5),
		('MEAL', 'sequentialWarmup', 1), ('MEAL', 'sequentialMeasured', 10),
		('MEAL', 'concurrentWarmup', 1), ('MEAL', 'concurrentMeasured', 5)
	) fixtures(poll_type, fixture_group, fixture_count)
) expanded;

INSERT INTO polls (
	campus_id, template_id, title, poll_type, selection_type, is_anonymous,
	allow_user_option_add, charge_generation_type, payment_category, payment_account_id,
	starts_at, ends_at, status, created_by, created_at, updated_at
)
SELECT :campus_id, NULL, title, poll_type, 'SINGLE', FALSE, FALSE,
	CASE WHEN poll_type = 'COFFEE' THEN 'OPTION_PRICE' ELSE 'NONE' END,
	CASE WHEN poll_type = 'COFFEE' THEN 'COFFEE' ELSE NULL END,
	CASE WHEN poll_type = 'COFFEE' THEN :coffee_account_id ELSE NULL END,
	now() - interval '1 day',
	CASE WHEN poll_type = 'COFFEE' THEN now() + interval '7 days' ELSE now() - interval '1 second' END,
	CASE WHEN poll_type = 'COFFEE' THEN 'OPEN' ELSE 'CLOSED' END,
	:actor_id, now(), now()
FROM perf_poll_specs ORDER BY poll_type, fixture_group, ordinal;

CREATE TEMP TABLE perf_polls ON COMMIT DROP AS
SELECT p.id, p.poll_type, s.fixture_group, s.ordinal
FROM polls p JOIN perf_poll_specs s ON s.title = p.title;

INSERT INTO poll_options
	(poll_id, content, compose_menu_code, price_amount, sort_order, user_added, created_by_user_id)
SELECT p.id,
	:'fixture_run_id' || '_' || p.poll_type || '_OPTION_' || option_number,
	CASE WHEN p.poll_type = 'COFFEE' THEN :'fixture_run_id' || '_MENU_' || option_number ELSE NULL END,
	CASE WHEN p.poll_type = 'COFFEE' THEN 1500 + option_number * 500 ELSE 0 END,
	option_number, FALSE, NULL
FROM perf_polls p CROSS JOIN generate_series(1, 4) option_number;

INSERT INTO poll_responses (poll_id, user_id, memo, responded_at, created_at, updated_at)
SELECT p.id, u.id, NULL, now(), now(), now()
FROM perf_polls p CROSS JOIN perf_users u ORDER BY p.id, u.member_number;

INSERT INTO poll_response_options (response_id, option_id)
SELECT r.id, o.id
FROM perf_polls p
JOIN poll_responses r ON r.poll_id = p.id
JOIN perf_users u ON u.id = r.user_id
JOIN poll_options o ON o.poll_id = p.id
	AND o.sort_order = (((u.member_number + :seed) - 1) % 4) + 1;

COMMIT;
