package com.faithlog.billing.service.query;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import org.springframework.data.domain.Pageable;

public record AdminCampusChargeListQuery(
	Long campusId,
	Long requesterId,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	Long userId,
	String keyword,
	Long paymentAccountId,
	boolean includeArchived,
	Pageable pageable
) {
	public AdminCampusChargeListQuery(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Long userId,
		String keyword,
		Long paymentAccountId,
		Pageable pageable
	) {
		this(campusId, requesterId, paymentCategory, status, userId, keyword, paymentAccountId, false, pageable);
	}

	public AdminCampusChargeListQuery(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Long userId,
		String keyword,
		Pageable pageable
	) {
		this(campusId, requesterId, paymentCategory, status, userId, keyword, null, false, pageable);
	}
}
