\set ON_ERROR_STOP on

BEGIN;

SELECT EXISTS (
	SELECT 1 FROM campuses
	WHERE id = :'campus_id'::bigint
		AND name LIKE '%' || :'dataset_id' || '%'
) AS target_dataset_matches \gset
\if :target_dataset_matches
\else
	\echo 'CAMPUS_ID does not match DATASET_ID.'
	\quit 3
\endif

SELECT COUNT(*) >= 1000 AS has_member_cardinality
FROM campus_members cm
JOIN users u ON u.id = cm.user_id AND u.is_active = TRUE
WHERE cm.campus_id = :'campus_id'::bigint AND cm.status = 'ACTIVE' \gset
\if :has_member_cardinality
\else
	\echo 'At least 1,000 ACTIVE members are required.'
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
	SELECT 1
	FROM users u
	JOIN campus_members cm ON cm.user_id = u.id
	JOIN campus_duty_assignments cda ON cda.campus_id = cm.campus_id AND cda.user_id = u.id
	WHERE u.id = :'duty_requester_user_id'::bigint
		AND u.is_active = TRUE
		AND u.role <> 'ADMIN'
		AND cm.campus_id = :'campus_id'::bigint
		AND cm.status = 'ACTIVE'
		AND cm.campus_role = 'MEMBER'
		AND cda.duty_type = 'COFFEE'
		AND cda.is_active = TRUE
) AS duty_identity_valid \gset
\if :duty_identity_valid
\else
	\echo 'DUTY_REQUESTER_USER_ID must be a restricted ACTIVE COFFEE duty member.'
	\quit 3
\endif

SELECT COALESCE(MIN(id), 0) AS penalty_account_id
FROM payment_accounts
WHERE campus_id = :'campus_id'::bigint
	AND account_type = 'PENALTY'
	AND is_active = TRUE
	AND deleted_at IS NULL \gset

SELECT COALESCE(MIN(id), 0) AS owned_coffee_account_id
FROM payment_accounts
WHERE campus_id = :'campus_id'::bigint
	AND owner_user_id = :'requester_user_id'::bigint
	AND account_type = 'COFFEE'
	AND is_active = TRUE
	AND deleted_at IS NULL \gset

SELECT COALESCE(MIN(id), 0) AS duty_owned_coffee_account_id
FROM payment_accounts
WHERE campus_id = :'campus_id'::bigint
	AND owner_user_id = :'duty_requester_user_id'::bigint
	AND account_type = 'COFFEE'
	AND is_active = TRUE
	AND deleted_at IS NULL \gset

SELECT (:penalty_account_id::bigint > 0
	AND :owned_coffee_account_id::bigint > 0
	AND :duty_owned_coffee_account_id::bigint > 0) AS required_accounts_exist \gset
\if :required_accounts_exist
\else
	\echo 'The target requires ACTIVE PENALTY, admin-owned COFFEE, and duty-owned COFFEE accounts.'
	\quit 3
\endif

SELECT COALESCE((
	SELECT cm.user_id
	FROM campus_members cm
	WHERE cm.campus_id = :'campus_id'::bigint
		AND cm.status = 'ACTIVE'
		AND cm.user_id NOT IN (:'requester_user_id'::bigint, :'duty_requester_user_id'::bigint)
		AND NOT EXISTS (
			SELECT 1
			FROM payment_accounts pa
			WHERE pa.campus_id = cm.campus_id
				AND pa.owner_user_id = cm.user_id
				AND pa.account_type = 'COFFEE'
				AND pa.is_active = TRUE
				AND pa.deleted_at IS NULL
		)
	ORDER BY cm.user_id
	LIMIT 1
), 0) AS fixture_account_owner_id \gset

SELECT :fixture_account_owner_id::bigint > 0 AS fixture_account_owner_exists \gset
\if :fixture_account_owner_exists
\else
	\echo 'No ACTIVE member is available for the additive foreign-owner COFFEE account.'
	\quit 3
\endif

SELECT EXISTS (
	SELECT 1
	FROM campuses c
	JOIN campus_members cm ON cm.campus_id = c.id AND cm.status = 'ACTIVE'
	WHERE c.id = :'cross_campus_id'::bigint AND c.id <> :'campus_id'::bigint
) AS cross_campus_valid \gset
\if :cross_campus_valid
\else
	\echo 'CROSS_CAMPUS_ID must identify another campus with ACTIVE members.'
	\quit 3
\endif

SELECT NOT EXISTS (
	SELECT 1 FROM payment_accounts
	WHERE nickname IN (
		'PERF_ISSUE_193:' || :'fixture_run_id' || ':FOREIGN',
		'PERF_ISSUE_193:' || :'fixture_run_id' || ':CROSS'
	)
	UNION ALL
	SELECT 1 FROM charge_items
	WHERE reason LIKE 'PERF_ISSUE_193:' || :'fixture_run_id' || ':%'
) AS fixture_run_is_fresh \gset
\if :fixture_run_is_fresh
\else
	\echo 'FIXTURE_RUN_ID must be new and immutable.'
	\quit 3
\endif

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:'campus_id'::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'fixture_run_id' || ':FOREIGN',
	'PERF_BANK', 'PERF-' || :'fixture_run_id', 'PERF_ISSUE_193',
	:fixture_account_owner_id::bigint, TRUE, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS fixture_account_id \gset

INSERT INTO payment_accounts (
	campus_id, account_type, nickname, bank_name, account_number, account_holder,
	owner_user_id, is_active, deactivated_at, deleted_at, created_at, updated_at
)
VALUES (
	:'cross_campus_id'::bigint, 'COFFEE',
	'PERF_ISSUE_193:' || :'fixture_run_id' || ':CROSS',
	'PERF_BANK', 'PERF-CROSS-' || :'fixture_run_id', 'PERF_ISSUE_193',
	NULL, FALSE, CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
)
RETURNING id AS cross_campus_account_id \gset

WITH bounds AS (
	SELECT
		CURRENT_TIMESTAMP - INTERVAL '1 month' AS terminal_cutoff,
		CURRENT_TIMESTAMP - INTERVAL '10 days' AS recent_completed_at,
		CURRENT_TIMESTAMP - INTERVAL '2 months' AS archived_completed_at
), fixture_members AS (
	SELECT cm.user_id, ROW_NUMBER() OVER (ORDER BY cm.user_id) AS rn
	FROM campus_members cm
	JOIN users u ON u.id = cm.user_id AND u.is_active = TRUE
	WHERE cm.campus_id = :'campus_id'::bigint AND cm.status = 'ACTIVE'
	ORDER BY cm.user_id
	LIMIT 1000
), fixture_accounts AS (
	SELECT :penalty_account_id::bigint AS account_id, 'PENALTY'::text AS category, 'DEVOTION_RECORD'::text AS source_type, 1 AS ordinal
	UNION ALL SELECT :owned_coffee_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 2
	UNION ALL SELECT :duty_owned_coffee_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 3
	UNION ALL SELECT :fixture_account_id::bigint, 'COFFEE', 'POLL_RESPONSE', 4
), fixture_shapes AS (
	SELECT 'OLD_UNPAID'::text AS shape, 'UNPAID'::text AS status, archived_completed_at AS completed_at, 1 AS ordinal FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:PAID', 'PAID', recent_completed_at, 2 FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:WAIVED', 'WAIVED', recent_completed_at, 3 FROM bounds
	UNION ALL SELECT 'RECENT_TERMINAL:CANCELED', 'CANCELED', recent_completed_at, 4 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:PAID', 'PAID', archived_completed_at, 5 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:WAIVED', 'WAIVED', archived_completed_at, 6 FROM bounds
	UNION ALL SELECT 'ARCHIVED_TERMINAL:CANCELED', 'CANCELED', archived_completed_at, 7 FROM bounds
)
INSERT INTO charge_items (
	campus_id, user_id, payment_category, payment_account_id,
	bank_name_snapshot, account_number_snapshot, account_holder_snapshot,
	source_type, source_id, title, reason, amount, status, due_date, paid_at,
	created_at, updated_at
)
SELECT
	:'campus_id'::bigint,
	m.user_id,
	a.category,
	a.account_id,
	pa.bank_name,
	pa.account_number,
	pa.account_holder,
	a.source_type,
	(hashtextextended(:'fixture_run_id' || ':' || a.ordinal || ':' || s.ordinal, 0) & 4611686018427387903) + 1,
	'PERF_ISSUE_193 ' || :'fixture_run_id' || ' ' || s.shape,
	'PERF_ISSUE_193:' || :'fixture_run_id' || ':' || s.shape,
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

WITH cross_members AS (
	SELECT cm.user_id, ROW_NUMBER() OVER (ORDER BY cm.user_id) AS rn
	FROM campus_members cm
	JOIN users u ON u.id = cm.user_id AND u.is_active = TRUE
	WHERE cm.campus_id = :'cross_campus_id'::bigint AND cm.status = 'ACTIVE'
	ORDER BY cm.user_id
	LIMIT 10
)
INSERT INTO charge_items (
	campus_id, user_id, payment_category, payment_account_id,
	bank_name_snapshot, account_number_snapshot, account_holder_snapshot,
	source_type, source_id, title, reason, amount, status, due_date, paid_at,
	created_at, updated_at
)
SELECT
	:'cross_campus_id'::bigint,
	m.user_id,
	'COFFEE',
	:cross_campus_account_id::bigint,
	pa.bank_name,
	pa.account_number,
	pa.account_holder,
	'POLL_RESPONSE',
	(hashtextextended(:'fixture_run_id' || ':CROSS', 0) & 4611686018427387903) + 1,
	'PERF_ISSUE_193 ' || :'fixture_run_id' || ' CROSS',
	'PERF_ISSUE_193:' || :'fixture_run_id' || ':CROSS',
	(9000 + m.rn)::integer,
	'UNPAID',
	NULL,
	NULL,
	CURRENT_TIMESTAMP - (m.rn * INTERVAL '1 millisecond'),
	CURRENT_TIMESTAMP
FROM cross_members m
JOIN payment_accounts pa ON pa.id = :cross_campus_account_id::bigint;

COMMIT;
