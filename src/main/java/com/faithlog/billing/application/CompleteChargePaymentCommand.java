package com.faithlog.billing.application;

import java.time.Instant;

public record CompleteChargePaymentCommand(
	Long campusId,
	Long chargeItemId,
	Long requesterId,
	Instant paidAt
) {
}
