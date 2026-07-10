package com.faithlog.billing.service.query;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import org.springframework.data.domain.Pageable;

public record AdminMemberChargeListQuery(
	Long campusId,
	Long userId,
	Long requesterId,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	Pageable pageable
) {
}
