package com.faithlog.billing.service.port;

import com.faithlog.billing.domain.type.PaymentCategory;

public record PaymentAccountLockScope(
	Long id,
	Long campusId,
	PaymentCategory accountType,
	Long ownerUserId
) {
}
