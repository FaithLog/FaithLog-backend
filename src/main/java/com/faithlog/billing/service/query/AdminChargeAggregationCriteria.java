package com.faithlog.billing.service.query;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;
import java.util.Set;

public record AdminChargeAggregationCriteria(
	Long campusId,
	Long userId,
	String keyword,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	Set<Long> paymentAccountIds,
	PaymentCategory excludedPaymentCategory,
	Instant terminalCompletedAtFrom
) {
}
