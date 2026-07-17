\set ON_ERROR_STOP on
\pset tuples_only on
\pset format unaligned

SELECT JSONB_BUILD_OBJECT(
	'campusId', (
		SELECT id
		FROM campuses
		WHERE name = 'PERF_ISSUE_193:' || :'dataset_id'
	),
	'crossCampusId', (
		SELECT id
		FROM campuses
		WHERE name = 'PERF_ISSUE_193:' || :'dataset_id' || ':CROSS'
	)
);
