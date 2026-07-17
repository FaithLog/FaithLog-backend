SELECT pc.id AS comment_id,
       pc.user_id,
       u.name,
       CASE WHEN pc.is_deleted THEN '삭제된 댓글입니다.' ELSE pc.content END AS content,
       pc.is_deleted,
       pc.created_at,
       pc.updated_at
FROM poll_comments pc
JOIN users u ON u.id = pc.user_id
WHERE pc.poll_id = :'poll_id'::bigint
ORDER BY pc.id ASC;
