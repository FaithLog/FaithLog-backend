package com.faithlog.billing.application;

public record ChargeAmountSummaryResult(
	int totalAmount,
	int unpaidAmount,
	int paidAmount,
	int waivedAmount,
	int canceledAmount
) {
}
