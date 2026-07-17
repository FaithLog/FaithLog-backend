package com.faithlog.billing.service.command;

import java.time.Instant;

public record CompleteChargePaymentCommand(
	Long campusId,
	Long chargeItemId,
	Long requesterId,
	Instant paidAt
) {
}
