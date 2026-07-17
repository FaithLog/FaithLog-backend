package com.faithlog.billing.service.result;

public record ChargeAmountSummaryResult(
	int totalAmount,
	int unpaidAmount,
	int paidAmount,
	int waivedAmount,
	int canceledAmount
) {
}
