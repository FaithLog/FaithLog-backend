package com.faithlog.billing.application;

import java.time.Instant;

public record AdminCampusChargeMemberResult(
	Long userId,
	String name,
	String email,
	int totalAmount,
	int unpaidAmount,
	int paidAmount,
	int waivedAmount,
	int canceledAmount,
	Instant latestChargeCreatedAt
) {
}
