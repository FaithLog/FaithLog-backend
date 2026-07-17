#!/usr/bin/env bash
set -euo pipefail

case_name=${1:?case_name is required}
phase=${2:?phase is required: before or after}
output_path=${3:?output_path is required}
SCRIPT_DIR=$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)
POSTGRES_CONTAINER_ID=${POSTGRES_CONTAINER_ID:?POSTGRES_CONTAINER_ID is required}
POSTGRES_USER=${POSTGRES_USER:?POSTGRES_USER is required}
POSTGRES_DB=${POSTGRES_DB:?POSTGRES_DB is required}
POSTGRES_PASSWORD=${POSTGRES_PASSWORD:?POSTGRES_PASSWORD is required}

if [[ ! "$case_name" =~ ^[a-z0-9_-]+$ ]]; then
	printf 'Unsafe case_name: %s\n' "$case_name" >&2
	exit 1
fi
if [[ "$phase" != before && "$phase" != after ]]; then
	printf 'phase must be before or after: %s\n' "$phase" >&2
	exit 1
fi
if [[ -e "$output_path" ]]; then
	printf 'Control snapshot already exists: %s\n' "$output_path" >&2
	exit 1
fi

capture_started_at=$(env -i PATH="$PATH" node -e 'process.stdout.write(new Date().toISOString())')
PGPASSWORD="$POSTGRES_PASSWORD" docker exec -i \
	-e PGPASSWORD \
	-e PGAPPNAME="faithlog-issue195-control-${case_name}-${phase}" \
	"$POSTGRES_CONTAINER_ID" \
	psql -X -q -v ON_ERROR_STOP=1 \
		-U "$POSTGRES_USER" \
		-d "$POSTGRES_DB" \
	< "$SCRIPT_DIR/db-runtime-integrity.sql" \
	| env -i PATH="$PATH" CAPTURE_STARTED_AT="$capture_started_at" CONTROL_PHASE="$phase" OUTPUT_PATH="$output_path" \
		node -e '
			const fs = require("node:fs");
			let input = "";
			process.stdin.setEncoding("utf8");
			process.stdin.on("data", chunk => input += chunk);
			process.stdin.on("end", () => {
				const value = JSON.parse(input);
				const output = {
					...value,
					schemaVersion: 1,
					phase: process.env.CONTROL_PHASE,
					captureStartedAt: process.env.CAPTURE_STARTED_AT,
					captureCompletedAt: new Date().toISOString(),
				};
				fs.writeFileSync(process.env.OUTPUT_PATH, JSON.stringify(output) + "\n", {
					encoding: "utf8", flag: "wx", mode: 0o600,
				});
			});
		'
