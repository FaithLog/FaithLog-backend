SELECT pt.id AS template_id,
       pt.poll_type,
       pto.id AS option_id,
       pto.sort_order,
       pto.content
FROM poll_templates pt
LEFT JOIN poll_template_options pto ON pto.template_id = pt.id
WHERE pt.campus_id = :'campus_id'::bigint
  AND pt.is_active = TRUE
ORDER BY pt.id ASC, pto.sort_order ASC, pto.id ASC;
