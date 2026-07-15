\set ON_ERROR_STOP on
SET TIME ZONE 'Asia/Seoul';

BEGIN;

SELECT (
	:'dataset_id' ~ '^[A-Za-z0-9_-]{1,32}$'
	AND :'fixture_run_id' ~ '^[A-Za-z0-9_-]{1,32}$'
) AS namespace_is_valid \gset
\if :namespace_is_valid
\else
	\echo 'DATASET_ID and FIXTURE_RUN_ID must use 1-32 ASCII letters, digits, underscore, or hyphen.'
	\quit 3
\endif

SELECT NOT EXISTS (
	SELECT 1 FROM campuses
	WHERE name = 'PERF_ISSUE_193:' || :'dataset_id'
		OR name = 'PERF_ISSUE_193:' || :'dataset_id' || ':CROSS'
	UNION ALL
	SELECT 1 FROM payment_accounts
	WHERE nickname LIKE 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':%'
	UNION ALL
	SELECT 1 FROM charge_items
	WHERE reason LIKE 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':%'
) AS dataset_namespace_is_fresh \gset
\if :dataset_namespace_is_fresh
\else
	\echo 'The current DATASET_ID/FIXTURE_RUN_ID namespace already exists.'
	\quit 3
\endif

SELECT EXISTS (
	SELECT 1 FROM users
	WHERE id = :'requester_user_id'::bigint AND role = 'ADMIN' AND is_active = TRUE
) AS admin_identity_valid \gset
\if :admin_identity_valid
\else
	\echo 'REQUESTER_USER_ID must be an ACTIVE service ADMIN.'
	\quit 3
\endif

SELECT EXISTS (
	SELECT 1 FROM users
	WHERE id = :'duty_requester_user_id'::bigint
		AND role <> 'ADMIN'
		AND is_active = TRUE
) AS duty_identity_valid \gset
\if :duty_identity_valid
\else
	\echo 'DUTY_REQUESTER_USER_ID must be an ACTIVE non-ADMIN user.'
	\quit 3
\endif

SELECT COUNT(*) >= 1000 AS has_active_user_pool
FROM users
WHERE is_active = TRUE \gset
\if :has_active_user_pool
\else
	\echo 'At least 1,000 ACTIVE users are required to create the isolated dataset.'
	\quit 3
\endif

INSERT INTO campuses (
	name, region, description, invite_code, is_active, created_at, updated_at
)
VALUES (
	'PERF_ISSUE_193:' || :'dataset_id',
	'PERF_REGION',
	'Issue #193 isolated performance dataset',
	'P193-' || SUBSTRING(MD5(:'dataset_id') FROM 1 FOR 32),
	TRUE,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
)
RETURNING id AS campus_id \gset

INSERT INTO campuses (
	name, region, description, invite_code, is_active, created_at, updated_at
)
VALUES (
	'PERF_ISSUE_193:' || :'dataset_id' || ':CROSS',
	'PERF_REGION',
	'Issue #193 cross-campus isolation marker',
	'P193X-' || SUBSTRING(MD5(:'dataset_id' || ':CROSS') FROM 1 FOR 30),
	TRUE,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
)
RETURNING id AS cross_campus_id \gset

WITH selected_users AS (
	SELECT :'requester_user_id'::bigint AS user_id, 1 AS ordinal
	UNION ALL
	SELECT :'duty_requester_user_id'::bigint, 2
	UNION ALL
	SELECT id, ROW_NUMBER() OVER (ORDER BY id) + 2
	FROM (
		SELECT id
		FROM users
		WHERE is_active = TRUE
			AND id NOT IN (:'requester_user_id'::bigint, :'duty_requester_user_id'::bigint)
		ORDER BY id
		LIMIT 998
	) remaining_users
)
INSERT INTO campus_members (
	campus_id, user_id, campus_role, status, joined_at, created_at, updated_at
)
SELECT
	:campus_id::bigint,
	su.user_id,
	'MEMBER',
	'ACTIVE',
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
FROM selected_users su;

SELECT (
	(SELECT COUNT(*) = 1000
	 FROM campus_members
	 WHERE campus_id = :campus_id::bigint AND status = 'ACTIVE')
	AND NOT EXISTS (
		SELECT 1
		FROM campus_members cm
		LEFT JOIN users u ON u.id = cm.user_id
		WHERE cm.campus_id = :campus_id::bigint
			AND cm.status = 'ACTIVE'
			AND u.is_active IS DISTINCT FROM TRUE
	)
) AS active_members_have_active_users \gset
\if :active_members_have_active_users
\else
	\echo 'The isolated campus must contain exactly 1,000 ACTIVE memberships backed by ACTIVE users.'
	\quit 3
\endif

INSERT INTO campus_duty_assignments (
	campus_id, user_id, duty_type, is_active, assigned_at, revoked_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint,
	:'duty_requester_user_id'::bigint,
	'COFFEE',
	TRUE,
	CURRENT_TIMESTAMP,
	NULL,
	CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP
);

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint, 'PENALTY',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':PENALTY',
	'PERF_BANK', 'P193-PENALTY-' || :'fixture_run_id', 'PERF_PENALTY',
	NULL, TRUE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS penalty_account_id \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':ADMIN',
	'PERF_BANK', 'P193-ADMIN-' || :'fixture_run_id', 'PERF_ADMIN',
	:'requester_user_id'::bigint, TRUE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS owned_coffee_account_id \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':DUTY',
	'PERF_BANK', 'P193-DUTY-' || :'fixture_run_id', 'PERF_DUTY',
	:'duty_requester_user_id'::bigint, TRUE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS duty_owned_coffee_account_id \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':DUTY_HISTORY',
	'PERF_BANK', 'P193-DUTY-HISTORY-' || :'fixture_run_id', 'PERF_DUTY',
	:'duty_requester_user_id'::bigint, FALSE, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP,
	CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS duty_historical_coffee_account_id \gset

SELECT user_id AS fixture_account_owner_id
FROM campus_members
WHERE campus_id = :campus_id::bigint
	AND status = 'ACTIVE'
	AND user_id NOT IN (:'requester_user_id'::bigint, :'duty_requester_user_id'::bigint)
ORDER BY user_id
LIMIT 1 \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:campus_id::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':FOREIGN',
	'PERF_BANK', 'P193-FOREIGN-' || :'fixture_run_id', 'PERF_FOREIGN',
	:fixture_account_owner_id::bigint, TRUE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS fixture_account_id \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:cross_campus_id::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':CROSS',
	'PERF_BANK', 'P193-CROSS-' || :'fixture_run_id', 'PERF_CROSS',
	NULL, FALSE, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS cross_campus_account_id \gset

WITH bounds AS (
	SELECT
		CURRENT_TIMESTAMP - INTERVAL '1 month' AS terminal_cutoff
), fixture_members AS (
	SELECT cm.user_id, ROW_NUMBER() OVER (ORDER BY cm.user_id) AS rn
	FROM campus_members cm
	JOIN users u ON u.id = cm.user_id AND u.is_active = TRUE
	WHERE cm.campus_id = :campus_id::bigint AND cm.status = 'ACTIVE'
	ORDER BY cm.user_id
), fixture_accounts AS (
	SELECT :penalty_account_id::bigint AS account_id, 'PENALTY'::text AS category, 'DEVOTION_RECORD'::text AS source_type, 1 AS ordinal
	UNION ALL SELECT :owned_coffee_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 2
	UNION ALL SELECT :duty_owned_coffee_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 3
	UNION ALL SELECT :duty_historical_coffee_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 4
	UNION ALL SELECT :fixture_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 5
), fixture_shapes AS (
	SELECT 'OLD_UNPAID'::text AS shape, 'UNPAID'::text AS status,
		terminal_cutoff - INTERVAL '1 month' AS completed_at, 1 AS ordinal FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:PAID', 'PAID', terminal_cutoff + INTERVAL '10 days', 2 FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:WAIVED', 'WAIVED', terminal_cutoff + INTERVAL '10 days', 3 FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:CANCELED', 'CANCELED', terminal_cutoff + INTERVAL '10 days', 4 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:PAID', 'PAID', terminal_cutoff - INTERVAL '1 month', 5 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:WAIVED', 'WAIVED', terminal_cutoff - INTERVAL '1 month', 6 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:CANCELED', 'CANCELED', terminal_cutoff - INTERVAL '1 month', 7 FROM bounds
)
INSERT INTO charge_items (
	campus_id, user_id, payment_category, payment_account_id,
	bank_name_snapshot, account_number_snapshot, account_holder_snapshot,
	source_type, source_id, title, reason, amount, status, due_date, paid_at,
	created_at, updated_at
)
SELECT
	:campus_id::bigint,
	m.user_id,
	a.category,
	a.account_id,
	pa.bank_name,
	pa.account_number,
	pa.account_holder,
	a.source_type,
	(hashtextextended(:'dataset_id' || ':' || :'fixture_run_id' || ':' || a.ordinal || ':' || s.ordinal, 0)
		& 4611686018427387903) + 1,
	'PERF_ISSUE_193 ' || :'dataset_id' || ' ' || :'fixture_run_id' || ' ' || s.shape,
	'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':' || s.shape,
	(1000 + a.ordinal * 1000 + (m.rn % 17) * 10 + s.ordinal)::integer,
	s.status,
	NULL,
	CASE WHEN s.status = 'PAID' THEN s.completed_at ELSE NULL END,
	s.completed_at - (m.rn * INTERVAL '1 millisecond') - (s.ordinal * INTERVAL '1 microsecond'),
	s.completed_at
FROM fixture_members m
CROSS JOIN fixture_accounts a
CROSS JOIN fixture_shapes s
JOIN payment_accounts pa ON pa.id = a.account_id;

SELECT (
	(SELECT COUNT(*) = 1000 FROM campus_members
	 WHERE campus_id = :campus_id::bigint AND status = 'ACTIVE')
	AND (SELECT COUNT(*) = 5 FROM payment_accounts WHERE campus_id = :campus_id::bigint)
	AND (SELECT COUNT(*) = 35000 FROM charge_items WHERE campus_id = :campus_id::bigint)
) AS isolated_dataset_shape_is_exact \gset
\if :isolated_dataset_shape_is_exact
\else
	\echo 'The isolated dataset shape is not exactly 1,000 members, 5 accounts, and 35,000 charges.'
	\quit 3
\endif

COMMIT;
