package com.faithlog.billing.service.result;

import com.faithlog.billing.domain.type.PaymentCategory;

public record ChargeCategorySummaryResult(
	PaymentCategory paymentCategory,
	int paidAmount,
	int unpaidAmount,
	int totalAmount
) {
}
