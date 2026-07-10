package com.faithlog.billing.service.command;

import com.faithlog.billing.domain.type.ChargeStatus;

public record ChangeChargeStatusCommand(
	Long chargeItemId,
	Long requesterId,
	ChargeStatus status
) {
}
