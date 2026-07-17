\set ON_ERROR_STOP on

WITH token_state AS (
	SELECT
		count(*)::text AS row_count,
		md5(COALESCE(string_agg(md5(row_to_json(token_row)::text), '' ORDER BY token_row.id), '')) AS row_hash
	FROM user_fcm_tokens token_row
), log_state AS (
	SELECT
		count(*)::text AS row_count,
		md5(COALESCE(string_agg(md5(row_to_json(log_row)::text), '' ORDER BY log_row.id), '')) AS row_hash
	FROM notification_logs log_row
), member_state AS (
	SELECT
		count(*)::text AS row_count,
		md5(COALESCE(string_agg(md5(row_to_json(member_row)::text), '' ORDER BY member_row.id), '')) AS row_hash
	FROM campus_members member_row
)
SELECT json_build_object(
	'cardinality', json_build_object(
		'userFcmTokens', token_state.row_count,
		'notificationLogs', log_state.row_count,
		'campusMembers', member_state.row_count
	),
	'rowHashes', json_build_object(
		'userFcmTokens', token_state.row_hash,
		'notificationLogs', log_state.row_hash,
		'campusMembers', member_state.row_hash
	)
)
FROM token_state, log_state, member_state;
