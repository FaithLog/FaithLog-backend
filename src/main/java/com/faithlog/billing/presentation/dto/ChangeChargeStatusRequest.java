package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.ChangeChargeStatusCommand;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotNull;

public record ChangeChargeStatusRequest(
	@NotNull
	ChargeStatus status
) {

	public ChangeChargeStatusCommand toCommand(Long chargeItemId, AuthenticatedUser authenticatedUser) {
		return new ChangeChargeStatusCommand(
			chargeItemId,
			authenticatedUser.userId(),
			status
		);
	}
}
