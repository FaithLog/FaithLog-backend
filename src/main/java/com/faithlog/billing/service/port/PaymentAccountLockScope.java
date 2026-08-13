package com.faithlog.billing.service.port;

import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;

public record PaymentAccountLockScope(
	Long id,
	Long campusId,
	PaymentCategory accountType,
	Long ownerUserId,
	Instant deletedAt
) {
}
