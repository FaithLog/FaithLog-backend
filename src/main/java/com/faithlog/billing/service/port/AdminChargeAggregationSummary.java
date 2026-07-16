package com.faithlog.billing.service.port;

public record AdminChargeAggregationSummary(
	long totalAmount,
	long unpaidAmount,
	long paidAmount,
	long waivedAmount,
	long canceledAmount
) {
}
