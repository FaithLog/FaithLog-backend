package com.faithlog.billing.service.port;

import java.time.Instant;

public record AdminChargeMemberAggregate(
	Long userId,
	String name,
	String email,
	long totalAmount,
	long unpaidAmount,
	long paidAmount,
	long waivedAmount,
	long canceledAmount,
	Instant latestChargeCreatedAt
) {
}
