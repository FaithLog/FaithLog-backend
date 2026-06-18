package com.faithlog.billing.application;

import com.faithlog.billing.domain.ChargeStatus;

public record ChangeChargeStatusCommand(
	Long chargeItemId,
	Long requesterId,
	ChargeStatus status
) {
}
