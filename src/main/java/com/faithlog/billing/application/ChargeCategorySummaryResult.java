package com.faithlog.billing.application;

import com.faithlog.billing.domain.PaymentCategory;

public record ChargeCategorySummaryResult(
	PaymentCategory paymentCategory,
	int paidAmount,
	int unpaidAmount,
	int totalAmount
) {
}
