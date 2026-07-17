\pset format csv
\pset tuples_only on

select 'users_total' as metric, count(*)::bigint as value from users;
select 'users_active' as metric, count(*)::bigint as value from users where is_active = true;
select 'users_dataset_name_match' as metric, count(*)::bigint as value
from users
where position(lower(:'dataset_id') in lower(name)) > 0;
select 'users_active_dataset_name_match' as metric, count(*)::bigint as value
from users
where is_active = true and position(lower(:'dataset_id') in lower(name)) > 0;
select 'users_active_dataset_name_email_match' as metric, count(*)::bigint as value
from users
where is_active = true
  and position(lower(:'dataset_id') in lower(name)) > 0
  and position(lower(:'dataset_id') in lower(email)) > 0;
select 'users_inactive_dataset_name_match' as metric, count(*)::bigint as value
from users
where is_active = false and position(lower(:'dataset_id') in lower(name)) > 0;
select 'users_active_dataset_' || lower(role) as metric, count(*)::bigint as value
from users
where is_active = true and position(lower(:'dataset_id') in lower(name)) > 0
group by role
order by role;
select 'fixture_campuses' as metric, count(*)::bigint as value
from campuses
where position(:'fixture_run_id' in name) > 0;
select 'primary_active_members' as metric, count(*)::bigint as value
from campus_members
where campus_id = :'campus_id'::bigint and status = 'ACTIVE';
select 'primary_active_members_' || lower(campus_role) as metric, count(*)::bigint as value
from campus_members
where campus_id = :'campus_id'::bigint and status = 'ACTIVE'
group by campus_role
order by campus_role;
select 'isolation_active_members' as metric, count(*)::bigint as value
from campus_members
where campus_id = :'isolation_campus_id'::bigint and status = 'ACTIVE';
select 'primary_active_duties' as metric, count(*)::bigint as value
from campus_duty_assignments
where campus_id = :'campus_id'::bigint and is_active = true;
select 'primary_active_duties_' || lower(duty_type) as metric, count(*)::bigint as value
from campus_duty_assignments
where campus_id = :'campus_id'::bigint and is_active = true
group by duty_type
order by duty_type;
