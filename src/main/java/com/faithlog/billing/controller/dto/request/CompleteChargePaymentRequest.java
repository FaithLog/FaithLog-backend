package com.faithlog.billing.controller.dto.request;

import com.faithlog.billing.service.command.CompleteChargePaymentCommand;
import com.faithlog.global.security.AuthenticatedUser;
import java.time.Instant;

public record CompleteChargePaymentRequest(
	Instant paidAt
) {

	public CompleteChargePaymentCommand toCommand(
		Long campusId,
		Long chargeItemId,
		AuthenticatedUser authenticatedUser
	) {
		return new CompleteChargePaymentCommand(
			campusId,
			chargeItemId,
			authenticatedUser.userId(),
			paidAt
		);
	}

	public static CompleteChargePaymentCommand toCommand(
		CompleteChargePaymentRequest request,
		Long campusId,
		Long chargeItemId,
		AuthenticatedUser authenticatedUser
	) {
		Instant paidAt = request == null ? null : request.paidAt();
		return new CompleteChargePaymentCommand(
			campusId,
			chargeItemId,
			authenticatedUser.userId(),
			paidAt
		);
	}
}
