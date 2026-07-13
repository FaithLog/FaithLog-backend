package com.faithlog.billing.service.query;

import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.util.Set;

public record ChargeSearchCriteria(
	Long campusId,
	Set<Long> userIds,
	PaymentCategory paymentCategory,
	ChargeStatus status,
	Set<Long> paymentAccountIds,
	PaymentCategory excludedPaymentCategory
) {

	public ChargeSearchCriteria(
		Long campusId,
		Set<Long> userIds,
		PaymentCategory paymentCategory,
		ChargeStatus status,
		Set<Long> paymentAccountIds
	) {
		this(campusId, userIds, paymentCategory, status, paymentAccountIds, null);
	}

	public ChargeSearchCriteria(
		Long campusId,
		Set<Long> userIds,
		PaymentCategory paymentCategory,
		ChargeStatus status
	) {
		this(campusId, userIds, paymentCategory, status, null, null);
	}
}
