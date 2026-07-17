SELECT p.id AS poll_id,
       count(DISTINCT pr.user_id) AS responded_user_count
FROM polls p
LEFT JOIN poll_responses pr ON pr.poll_id = p.id
WHERE p.campus_id = :'campus_id'::bigint
  AND p.status = 'OPEN'
GROUP BY p.id
ORDER BY p.id ASC;
