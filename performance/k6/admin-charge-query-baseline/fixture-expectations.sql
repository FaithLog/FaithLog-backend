\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned
SET TIME ZONE 'Asia/Seoul';

WITH ids AS (
	SELECT
		(SELECT id FROM payment_accounts
		 WHERE nickname = 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':FOREIGN') AS fixture_account_id,
		(SELECT id FROM payment_accounts
		 WHERE nickname = 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':CROSS') AS cross_campus_account_id,
		(SELECT MIN(id) FROM payment_accounts
		 WHERE campus_id = :'campus_id'::bigint
			AND owner_user_id = :'requester_user_id'::bigint
			AND account_type = 'COFFEE' AND is_active = TRUE AND deleted_at IS NULL) AS owned_coffee_account_id,
		(SELECT MIN(id) FROM payment_accounts
		 WHERE campus_id = :'campus_id'::bigint
			AND owner_user_id = :'duty_requester_user_id'::bigint
			AND account_type = 'COFFEE' AND is_active = TRUE AND deleted_at IS NULL) AS duty_owned_coffee_account_id,
		(SELECT MIN(id) FROM payment_accounts
		 WHERE campus_id = :'campus_id'::bigint
			AND owner_user_id = :'duty_requester_user_id'::bigint
			AND account_type = 'COFFEE' AND is_active = FALSE AND deleted_at IS NOT NULL) AS duty_historical_coffee_account_id
), campus_identity AS (
	SELECT id, name, region
	FROM campuses
	WHERE id = :'campus_id'::bigint
), dataset_shape AS (
	SELECT
		(SELECT COUNT(*) FROM campus_members
		 WHERE campus_id = :'campus_id'::bigint AND status = 'ACTIVE')::integer AS active_member_count,
		(SELECT COUNT(*) FROM payment_accounts
		 WHERE campus_id = :'campus_id'::bigint)::integer AS fixture_account_count,
		(SELECT COUNT(*) FROM charge_items
		 WHERE campus_id = :'campus_id'::bigint)::integer AS fixture_charge_count
), duty_active_accounts AS (
	SELECT id
	FROM payment_accounts
	WHERE campus_id = :'campus_id'::bigint
		AND owner_user_id = :'duty_requester_user_id'::bigint
		AND account_type = 'COFFEE'
		AND is_active = TRUE
		AND deleted_at IS NULL
), duty_all_accounts AS (
	SELECT id
	FROM payment_accounts
	WHERE campus_id = :'campus_id'::bigint
		AND owner_user_id = :'duty_requester_user_id'::bigint
		AND account_type = 'COFFEE'
), target AS (
	SELECT c.user_id, u.name, u.email AS keyword
	FROM charge_items c
	JOIN ids ON c.payment_account_id = ids.fixture_account_id
	JOIN users u ON u.id = c.user_id
	WHERE c.reason LIKE 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':%'
	ORDER BY c.user_id
	LIMIT 1
), admin_my_accounts AS (
	SELECT id
	FROM payment_accounts
	WHERE campus_id = :'campus_id'::bigint
		AND is_active = TRUE
		AND deleted_at IS NULL
		AND (
			account_type = 'PENALTY'
			OR (account_type = 'COFFEE' AND owner_user_id = :'requester_user_id'::bigint)
		)
), cases(
	name, group_type, scope, category, status, user_id, keyword, account_id, page, include_archived
) AS (
	SELECT * FROM (VALUES
		('my_initial_penalty_unpaid', 'measured', 'my', 'PENALTY', 'UNPAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('my_payment_category', 'measured', 'my', 'COFFEE', 'UNPAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('my_status', 'measured', 'my', 'COFFEE', 'PAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('my_user_id', 'measured', 'my', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, NULL::bigint, 0, FALSE),
		('my_keyword', 'measured', 'my', 'COFFEE', NULL::text, NULL::bigint, (SELECT keyword FROM target), NULL::bigint, 0, FALSE),
		('my_payment_account_unknown_param_ignored', 'measured', 'my', 'COFFEE', NULL::text, NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('my_pagination_page_0', 'measured', 'my', 'COFFEE', NULL::text, NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('my_pagination_page_1', 'measured', 'my', 'COFFEE', NULL::text, NULL::bigint, NULL::text, NULL::bigint, 1, FALSE),
		('admin_initial_penalty_unpaid', 'measured', 'admin', 'PENALTY', 'UNPAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('admin_payment_category', 'measured', 'admin', 'COFFEE', 'UNPAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('admin_status', 'measured', 'admin', 'COFFEE', 'PAID', NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('admin_user_id', 'measured', 'admin', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, NULL::bigint, 0, FALSE),
		('admin_keyword', 'measured', 'admin', 'COFFEE', NULL::text, NULL::bigint, (SELECT keyword FROM target), NULL::bigint, 0, FALSE),
		('admin_payment_account', 'measured', 'admin', 'COFFEE', NULL::text, NULL::bigint, NULL::text, (SELECT fixture_account_id FROM ids), 0, FALSE),
		('admin_pagination_page_0', 'measured', 'admin', 'COFFEE', NULL::text, NULL::bigint, NULL::text, (SELECT fixture_account_id FROM ids), 0, FALSE),
		('admin_pagination_page_1', 'measured', 'admin', 'COFFEE', NULL::text, NULL::bigint, NULL::text, (SELECT fixture_account_id FROM ids), 1, FALSE),
		('admin_archive_default', 'archive', 'admin', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, (SELECT fixture_account_id FROM ids), 0, FALSE),
		('admin_archive_included', 'archive', 'admin', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, (SELECT fixture_account_id FROM ids), 0, TRUE),
		('my_archive_default', 'archive', 'my', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, NULL::bigint, 0, FALSE),
		('my_archive_included', 'archive', 'my', 'COFFEE', NULL::text, (SELECT user_id FROM target), NULL::text, NULL::bigint, 0, TRUE),
		('duty_owned_accounts_visible', 'duty', 'duty', 'COFFEE', NULL::text, NULL::bigint, NULL::text, NULL::bigint, 0, FALSE),
		('duty_owned_account_filter_visible', 'duty', 'duty', 'COFFEE', NULL::text, NULL::bigint, NULL::text, (SELECT duty_owned_coffee_account_id FROM ids), 0, FALSE)
	) AS configured(
		name, group_type, scope, category, status, user_id, keyword, account_id, page, include_archived
	)
), active_users AS (
	SELECT cm.user_id, u.name, u.email
	FROM campus_members cm
	JOIN users u ON u.id = cm.user_id AND u.is_active = TRUE
	WHERE cm.campus_id = :'campus_id'::bigint AND cm.status = 'ACTIVE'
), matching_charges AS (
	SELECT cs.name AS case_name, cs.group_type, cs.page, au.name AS user_name, au.email AS user_email, c.*
	FROM cases cs
	JOIN active_users au ON (cs.user_id IS NULL OR au.user_id = cs.user_id)
		AND (cs.keyword IS NULL
			OR LOWER(au.name) LIKE '%' || LOWER(cs.keyword) || '%'
			OR LOWER(au.email) LIKE '%' || LOWER(cs.keyword) || '%')
	JOIN charge_items c ON c.campus_id = :'campus_id'::bigint AND c.user_id = au.user_id
	WHERE c.payment_category = cs.category
		AND (cs.status IS NULL OR c.status = cs.status)
		AND (
			cs.include_archived
			OR c.status = 'UNPAID'
			OR (c.status = 'PAID' AND c.paid_at >= CURRENT_TIMESTAMP - INTERVAL '1 month')
			OR (c.status IN ('WAIVED', 'CANCELED') AND c.updated_at >= CURRENT_TIMESTAMP - INTERVAL '1 month')
		)
		AND (
			(cs.scope = 'admin' AND (cs.account_id IS NULL OR c.payment_account_id = cs.account_id))
			OR (cs.scope = 'my' AND c.payment_account_id IN (SELECT id FROM admin_my_accounts))
			OR (cs.scope = 'duty' AND (
				(cs.account_id IS NULL AND c.payment_account_id IN (SELECT id FROM duty_active_accounts))
				OR c.payment_account_id = cs.account_id
			))
		)
), summaries AS (
	SELECT
		cs.name,
		cs.group_type,
		COALESCE(SUM(mc.amount), 0)::bigint AS total_amount,
		COALESCE(SUM(mc.amount) FILTER (WHERE mc.status = 'UNPAID'), 0)::bigint AS unpaid_amount,
		COALESCE(SUM(mc.amount) FILTER (WHERE mc.status = 'PAID'), 0)::bigint AS paid_amount,
		COALESCE(SUM(mc.amount) FILTER (WHERE mc.status = 'WAIVED'), 0)::bigint AS waived_amount,
		COALESCE(SUM(mc.amount) FILTER (WHERE mc.status = 'CANCELED'), 0)::bigint AS canceled_amount,
		COUNT(DISTINCT mc.user_id)::bigint AS total_elements,
		cs.page
	FROM cases cs
	LEFT JOIN matching_charges mc ON mc.case_name = cs.name
	GROUP BY cs.name, cs.group_type, cs.page
), member_totals AS (
	SELECT
		case_name,
		page,
		user_id,
		MAX(user_name) AS user_name,
		MAX(user_email) AS user_email,
		SUM(amount)::bigint AS total_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'UNPAID'), 0)::bigint AS unpaid_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'PAID'), 0)::bigint AS paid_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'WAIVED'), 0)::bigint AS waived_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'CANCELED'), 0)::bigint AS canceled_amount,
		MAX(created_at) AS latest_created_at
	FROM matching_charges
	GROUP BY case_name, page, user_id
), ranked_members AS (
	SELECT
		mt.*,
		ROW_NUMBER() OVER (PARTITION BY case_name ORDER BY latest_created_at DESC, user_id) AS row_number
	FROM member_totals mt
), pages AS (
	SELECT
		cs.name,
		COALESCE(JSONB_AGG(
			JSONB_BUILD_OBJECT(
				'userId', rm.user_id,
				'name', rm.user_name,
				'email', rm.user_email,
				'totalAmount', rm.total_amount,
				'unpaidAmount', rm.unpaid_amount,
				'paidAmount', rm.paid_amount,
				'waivedAmount', rm.waived_amount,
				'canceledAmount', rm.canceled_amount
			) ORDER BY rm.row_number
		) FILTER (WHERE rm.user_id IS NOT NULL), '[]'::jsonb) AS member_rows
	FROM cases cs
	LEFT JOIN ranked_members rm ON rm.case_name = cs.name
		AND rm.row_number > (cs.page * 10)
		AND rm.row_number <= ((cs.page + 1) * 10)
	GROUP BY cs.name
), case_objects AS (
	SELECT
		s.name,
		s.group_type,
		JSONB_BUILD_OBJECT(
			'summary', JSONB_BUILD_OBJECT(
				'totalAmount', s.total_amount,
				'unpaidAmount', s.unpaid_amount,
				'paidAmount', s.paid_amount,
				'waivedAmount', s.waived_amount,
				'canceledAmount', s.canceled_amount
			),
			'memberRows', p.member_rows,
			'page', s.page,
			'size', 10,
			'totalElements', s.total_elements,
			'totalPages', CASE WHEN s.total_elements = 0 THEN 0 ELSE CEIL(s.total_elements / 10.0)::integer END
		) || CASE WHEN s.group_type IN ('archive', 'duty') THEN JSONB_BUILD_OBJECT(
			'campusId', ci.id,
			'campusName', ci.name,
			'region', ci.region
		) ELSE '{}'::jsonb END
		|| CASE WHEN s.group_type = 'duty' THEN JSONB_BUILD_OBJECT('status', 200)
			ELSE '{}'::jsonb END AS value
	FROM summaries s
	JOIN pages p ON p.name = s.name
	CROSS JOIN campus_identity ci
), grouped_cases AS (
	SELECT
		COALESCE(JSONB_OBJECT_AGG(name, value) FILTER (WHERE group_type = 'measured'), '{}'::jsonb) AS measured,
		COALESCE(JSONB_OBJECT_AGG(name, value) FILTER (WHERE group_type = 'archive'), '{}'::jsonb) AS archive,
		COALESCE(JSONB_OBJECT_AGG(name, value) FILTER (WHERE group_type = 'duty'), '{}'::jsonb) AS duty
	FROM case_objects
), duty_detail_charges AS (
	SELECT c.*
	FROM charge_items c, ids
	WHERE c.campus_id = :'campus_id'::bigint
		AND c.user_id = (SELECT user_id FROM target)
		AND c.payment_category = 'COFFEE'
		AND c.payment_account_id IN (SELECT id FROM duty_all_accounts)
		AND (
			c.status = 'UNPAID'
			OR (c.status = 'PAID' AND c.paid_at >= CURRENT_TIMESTAMP - INTERVAL '1 month')
			OR (c.status IN ('WAIVED', 'CANCELED') AND c.updated_at >= CURRENT_TIMESTAMP - INTERVAL '1 month')
		)
), duty_detail_summary AS (
	SELECT
		COALESCE(SUM(amount), 0)::bigint AS total_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'UNPAID'), 0)::bigint AS unpaid_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'PAID'), 0)::bigint AS paid_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'WAIVED'), 0)::bigint AS waived_amount,
		COALESCE(SUM(amount) FILTER (WHERE status = 'CANCELED'), 0)::bigint AS canceled_amount,
		COUNT(*)::bigint AS total_elements
	FROM duty_detail_charges
), duty_detail_items AS (
	SELECT COALESCE(JSONB_AGG(
		JSONB_BUILD_OBJECT(
			'id', id,
			'paymentCategory', payment_category,
			'title', title,
			'reason', reason,
			'amount', amount,
			'status', status,
			'dueDate', due_date,
			'paidAt', paid_at,
			'account', JSONB_BUILD_OBJECT(
				'paymentAccountId', payment_account_id,
				'bankName', bank_name_snapshot,
				'accountNumber', account_number_snapshot,
				'accountHolder', account_holder_snapshot
			),
			'source', JSONB_BUILD_OBJECT('sourceType', source_type, 'sourceId', source_id),
			'_sortCreatedAt', created_at
		)
		ORDER BY created_at DESC, id DESC
	), '[]'::jsonb) AS items
	FROM (
		SELECT * FROM duty_detail_charges ORDER BY created_at DESC, id DESC LIMIT 10
	) page_items
), duplicates AS (
	SELECT COUNT(*)::integer AS source_duplicate_count
	FROM (
		SELECT campus_id, user_id, payment_category, source_type, source_id
		FROM charge_items
		WHERE reason LIKE 'PERF_ISSUE_193:' || :'dataset_id' || ':' || :'fixture_run_id' || ':%'
		GROUP BY campus_id, user_id, payment_category, source_type, source_id
		HAVING COUNT(*) > 1
	) duplicate_sources
)
SELECT JSONB_BUILD_OBJECT(
	'datasetId', :'dataset_id',
	'fixtureRunId', :'fixture_run_id',
	'campusId', :'campus_id'::bigint,
	'campusName', ci.name,
	'region', ci.region,
	'activeMemberCount', ds.active_member_count,
	'fixtureAccountCount', ds.fixture_account_count,
	'fixtureChargeCount', ds.fixture_charge_count,
	'crossCampusId', :'cross_campus_id'::bigint,
	'requesterUserId', :'requester_user_id'::bigint,
	'dutyUserId', :'duty_requester_user_id'::bigint,
	'fixtureAccountId', ids.fixture_account_id,
	'foreignCoffeeAccountId', ids.fixture_account_id,
	'crossCampusAccountId', ids.cross_campus_account_id,
	'ownedCoffeeAccountId', ids.owned_coffee_account_id,
	'dutyOwnedCoffeeAccountId', ids.duty_owned_coffee_account_id,
	'dutyHistoricalCoffeeAccountId', ids.duty_historical_coffee_account_id,
	'targetUserId', target.user_id,
	'archivedMemberUserId', target.user_id,
	'targetKeyword', target.keyword,
	'sourceDuplicateCount', duplicates.source_duplicate_count,
	'cases', grouped_cases.measured,
	'archiveCases', grouped_cases.archive,
	'dutyScope', grouped_cases.duty || JSONB_BUILD_OBJECT(
		'duty_foreign_account_hidden', JSONB_BUILD_OBJECT('status', 403),
		'duty_member_detail_owned_only', JSONB_BUILD_OBJECT(
			'status', 200,
			'campusId', ci.id,
			'campusName', ci.name,
			'region', ci.region,
			'userId', target.user_id,
			'name', target.name,
			'email', target.keyword,
			'summary', JSONB_BUILD_OBJECT(
				'totalAmount', duty_detail_summary.total_amount,
				'unpaidAmount', duty_detail_summary.unpaid_amount,
				'paidAmount', duty_detail_summary.paid_amount,
				'waivedAmount', duty_detail_summary.waived_amount,
				'canceledAmount', duty_detail_summary.canceled_amount
			),
			'items', duty_detail_items.items,
			'page', 0,
			'size', 10,
			'totalElements', duty_detail_summary.total_elements,
			'totalPages', CASE WHEN duty_detail_summary.total_elements = 0 THEN 0
				ELSE CEIL(duty_detail_summary.total_elements / 10.0)::integer END
		)
	)
)
FROM ids, target, campus_identity ci, dataset_shape ds, grouped_cases,
	duty_detail_summary, duty_detail_items, duplicates;
