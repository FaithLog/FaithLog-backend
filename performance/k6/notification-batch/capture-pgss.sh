#!/usr/bin/env bash
set -euo pipefail

: "${PERF_POSTGRES_CONTAINER_ID:?PERF_POSTGRES_CONTAINER_ID is required.}"
: "${POSTGRES_USER:?POSTGRES_USER is required.}"
: "${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required.}"
: "${POSTGRES_DB:?POSTGRES_DB is required.}"

OUTPUT_PATH="${1:?pg_stat_statements output path is required.}"
TEMP_PATH="${OUTPUT_PATH}.tmp.$$"
trap 'rm -f "${TEMP_PATH}"' EXIT

PGSS_SCHEMA="$(PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD "${PERF_POSTGRES_CONTAINER_ID}" psql \
	-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
	-c "SELECT namespace.nspname
		FROM pg_extension extension
		JOIN pg_namespace namespace ON namespace.oid = extension.extnamespace
		WHERE extension.extname = 'pg_stat_statements';")"

if [[ -z "${PGSS_SCHEMA}" ]]; then
	printf '%s\n' '{"available":false,"reason":"extension-not-installed","rows":[]}' > "${TEMP_PATH}"
elif [[ "${PGSS_SCHEMA}" != *$'\n'* ]]; then
	PGPASSWORD="${POSTGRES_PASSWORD}" docker exec -e PGPASSWORD "${PERF_POSTGRES_CONTAINER_ID}" psql \
		-h 127.0.0.1 -U "${POSTGRES_USER}" -d "${POSTGRES_DB}" -X -q -A -t \
		-v pgss_schema="${PGSS_SCHEMA}" \
		-c "SELECT json_build_object(
			'available', true,
			'databaseId', (SELECT oid::text FROM pg_database WHERE datname = current_database()),
			'statsReset', (SELECT stats_reset FROM :\"pgss_schema\".pg_stat_statements_info),
			'rows', COALESCE((SELECT json_agg(row_to_json(statement_row) ORDER BY
				statement_row."userId", statement_row."databaseId", statement_row."queryId", statement_row."topLevel")
				FROM (
					SELECT userid::text AS "userId", dbid::text AS "databaseId",
						queryid::text AS "queryId", toplevel AS "topLevel",
						calls::bigint::text AS calls,
						round(total_exec_time * 1000)::bigint::text AS "totalExecTimeMicros"
					FROM :\"pgss_schema\".pg_stat_statements
					WHERE dbid = (SELECT oid FROM pg_database WHERE datname = current_database())
				) statement_row), '[]'::json)
		);" > "${TEMP_PATH}"
else
	echo "pg_stat_statements extension schema probe returned multiple values." >&2
	exit 2
fi

mv "${TEMP_PATH}" "${OUTPUT_PATH}"
