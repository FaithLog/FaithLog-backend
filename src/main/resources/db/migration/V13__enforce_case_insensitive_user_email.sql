DO $$
BEGIN
    IF EXISTS (
        SELECT 1
        FROM users
        GROUP BY lower(email)
        HAVING count(*) > 1
    ) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23505',
            MESSAGE = 'V13 cannot enforce case-insensitive user email uniqueness',
            DETAIL = 'Resolve duplicate lower(email) groups without automatic merge, delete, or reassignment.';
    END IF;
END
$$;

CREATE UNIQUE INDEX uk_users_email_lower
    ON users (lower(email));
