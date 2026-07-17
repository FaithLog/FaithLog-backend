SELECT n_tup_upd::text FROM pg_stat_user_tables WHERE relid = 'users'::regclass;
