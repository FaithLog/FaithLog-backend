select json_build_object(
  'capturedAt', clock_timestamp(),
  'observerPid', pg_backend_pid(),
  'unexpectedSessions', coalesce(json_agg(json_build_object(
    'pid', pid,
    'applicationName', application_name,
    'clientAddr', client_addr,
    'state', state,
    'queryStart', query_start
  )) filter (where pid is not null), '[]'::json)
)
from (
  select pid, application_name, client_addr, state, query_start
  from pg_stat_activity
  where datname = current_database()
    and pid <> pg_backend_pid()
    and state <> 'idle'
    and not (
      application_name = 'PostgreSQL JDBC Driver'
      and client_addr = any(string_to_array(:'app_client_addrs', ',')::inet[])
    )
) unexpected;
