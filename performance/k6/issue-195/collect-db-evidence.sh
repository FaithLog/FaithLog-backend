#!/usr/bin/env bash
set -euo pipefail

case_name=${1:?case_name is required}
PHASE=${2:?phase is required: before or after}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
REPORT_DIR=${PERF_REPORT_DIR:?PERF_REPORT_DIR is required}
POSTGRES_CONTAINER_ID=${POSTGRES_CONTAINER_ID:?POSTGRES_CONTAINER_ID is required}
POSTGRES_USER=${POSTGRES_USER:?POSTGRES_USER is required}
POSTGRES_DB=${POSTGRES_DB:?POSTGRES_DB is required}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}
PERF_DATASET_ID=${PERF_DATASET_ID:?PERF_DATASET_ID is required}
PERF_FIXTURE_RUN_ID=${PERF_FIXTURE_RUN_ID:?PERF_FIXTURE_RUN_ID is required}
CAMPUS_ID=${CAMPUS_ID:?CAMPUS_ID is required}
ISOLATION_CAMPUS_ID=${ISOLATION_CAMPUS_ID:?ISOLATION_CAMPUS_ID is required}

if [[ ! "$case_name" =~ ^[a-z0-9_-]+$ ]]; then
	printf 'Unsafe case_name: %s\n' "$case_name" >&2
	exit 1
fi
if [[ "$PHASE" != "before" && "$PHASE" != "after" ]]; then
	printf 'phase must be before or after: %s\n' "$PHASE" >&2
	exit 1
fi

CASE_REPORT_DIR="$REPORT_DIR/db-evidence/$case_name"
mkdir -p "$CASE_REPORT_DIR"

capture_table_counters() {
	PGPASSWORD="$POSTGRES_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		"$POSTGRES_CONTAINER_ID" \
		psql -X -v ON_ERROR_STOP=1 \
			-U "$POSTGRES_USER" \
			-d "$POSTGRES_DB" \
			-v dataset_id="$PERF_DATASET_ID" \
			-v fixture_run_id="$PERF_FIXTURE_RUN_ID" \
			-v campus_id="$CAMPUS_ID" \
			-v isolation_campus_id="$ISOLATION_CAMPUS_ID" \
		< "$SCRIPT_DIR/db-table-counters.sql" \
		> "$CASE_REPORT_DIR/${PHASE}-table-counters.csv"
}

capture_runtime_integrity() {
	PGPASSWORD="$POSTGRES_PASSWORD" docker exec -i \
		-e PGPASSWORD \
		-e PGAPPNAME="faithlog-issue195-observer-${case_name}-${PHASE}" \
		"$POSTGRES_CONTAINER_ID" \
		psql -X -v ON_ERROR_STOP=1 \
			-U "$POSTGRES_USER" \
			-d "$POSTGRES_DB" \
		< "$SCRIPT_DIR/db-runtime-integrity.sql" \
		> "$CASE_REPORT_DIR/${PHASE}-runtime-integrity.json"
}

PG_STAT_RELATION=$(PGPASSWORD="$POSTGRES_PASSWORD" docker exec \
	-e PGPASSWORD \
	"$POSTGRES_CONTAINER_ID" \
	psql -X -At -v ON_ERROR_STOP=1 \
		-U "$POSTGRES_USER" \
		-d "$POSTGRES_DB" \
		-c "select coalesce(to_regclass('public.pg_stat_statements')::text, '');")

capture_query_evidence() {
	local QUERY_AVAILABILITY_PATH="$CASE_REPORT_DIR/${PHASE}-query-availability.json"
	if [[ "$PG_STAT_RELATION" == "pg_stat_statements" ]]; then
		printf '{"schemaVersion":1,"phase":"%s","status":"available","relation":"pg_stat_statements"}\n' "$PHASE" \
			> "$QUERY_AVAILABILITY_PATH"
		PGPASSWORD="$POSTGRES_PASSWORD" docker exec \
			-e PGPASSWORD \
			"$POSTGRES_CONTAINER_ID" \
			psql -X -At -v ON_ERROR_STOP=1 \
				-U "$POSTGRES_USER" \
				-d "$POSTGRES_DB" \
				-c "select json_build_object('userId', userid::text, 'dbId', dbid::text, 'queryId', queryid::text, 'topLevel', toplevel, 'calls', calls::text, 'rows', rows::text, 'totalExecTime', total_exec_time, 'query', query)::text from pg_stat_statements where dbid = (select oid from pg_database where datname = current_database()) and query not ilike '%pg_stat_statements%' and query not ilike '%faithlog_issue195_runtime_integrity_observer%' and (query ilike '%users%' or query ilike '%campuses%' or query ilike '%campus_members%' or query ilike '%campus_duty_assignments%') order by userid, dbid, queryid, toplevel;" \
			> "$CASE_REPORT_DIR/${PHASE}-query-evidence.ndjson"
	else
		printf '{"schemaVersion":1,"phase":"%s","status":"unavailable","relation":null}\n' "$PHASE" \
			> "$QUERY_AVAILABILITY_PATH"
	fi
}

if [[ "$PHASE" == "before" ]]; then
	# Absorb evidence-query calls into the before snapshot instead of the measured delta.
	capture_table_counters
	capture_query_evidence
	# This transaction commits after its pg_stat_database values are read, so the
	# adoption validator expects exactly one observer commit in the measured delta.
	capture_runtime_integrity
else
	# Close the transaction/activity/analyze/planner window before other evidence queries.
	capture_runtime_integrity
	# Close the query window before after-table counters can pollute it.
	capture_query_evidence
	capture_table_counters
fi
