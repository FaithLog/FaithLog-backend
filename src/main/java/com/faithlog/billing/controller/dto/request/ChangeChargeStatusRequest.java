package com.faithlog.billing.controller.dto.request;

import com.faithlog.billing.service.command.ChangeChargeStatusCommand;
import com.faithlog.billing.domain.type.ChargeStatus;
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
