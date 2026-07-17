SELECT pr.id AS response_id,
       pr.user_id,
       pro.id AS response_option_id,
       po.id AS option_id,
       po.content,
       po.price_amount
FROM poll_responses pr
JOIN poll_response_options pro ON pro.response_id = pr.id
JOIN poll_options po ON po.id = pro.option_id
WHERE pr.poll_id = :'poll_id'::bigint
ORDER BY pr.id ASC, pro.id ASC;
