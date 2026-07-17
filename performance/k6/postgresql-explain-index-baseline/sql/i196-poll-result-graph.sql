SELECT po.id AS option_id,
       po.sort_order,
       pr.id AS response_id,
       pr.user_id,
       u.name,
       pro.id AS response_option_id
FROM poll_options po
LEFT JOIN poll_response_options pro ON pro.option_id = po.id
LEFT JOIN poll_responses pr ON pr.id = pro.response_id AND pr.poll_id = po.poll_id
LEFT JOIN users u ON u.id = pr.user_id
WHERE po.poll_id = :'poll_id'::bigint
ORDER BY po.sort_order ASC, po.id ASC, pr.id ASC;
