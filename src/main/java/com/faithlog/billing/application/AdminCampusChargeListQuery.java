package com.faithlog.billing.application;

import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
import org.springframework.data.domain.Pageable;

public record AdminCampusChargeListQuery(
	Long campusId,
	Long requesterId,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	Long userId,
	String keyword,
	Pageable pageable
) {
}
