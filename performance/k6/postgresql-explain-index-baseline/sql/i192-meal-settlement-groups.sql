SELECT ms.id AS settlement_id,
       ms.poll_id,
       ms.requested_total_amount AS settlement_requested_total,
       ms.actual_total_amount AS settlement_actual_total,
       mg.id AS group_id,
       mg.option_id,
       mg.response_count_snapshot,
       mg.requested_total_amount,
       mg.actual_total_amount,
       mg.rounding_adjustment
FROM meal_poll_settlements ms
JOIN meal_poll_charge_groups mg ON mg.settlement_id = ms.id
WHERE ms.poll_id = :'meal_poll_id'::bigint
ORDER BY mg.id ASC;
