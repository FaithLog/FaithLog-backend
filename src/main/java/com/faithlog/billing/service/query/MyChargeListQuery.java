package com.faithlog.billing.service.query;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import org.springframework.data.domain.Pageable;

public record MyChargeListQuery(
	Long campusId,
	Long requesterId,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	boolean includeArchived,
	Pageable pageable
) {
	public MyChargeListQuery(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Pageable pageable
	) {
		this(campusId, requesterId, paymentCategory, status, false, pageable);
	}
}
