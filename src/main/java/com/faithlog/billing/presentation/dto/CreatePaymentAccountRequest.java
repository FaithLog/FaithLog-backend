package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentAccountRequest(
	@NotNull PaymentCategory accountType,
	@NotBlank @Size(max = 100) String nickname,
	@NotBlank @Size(max = 100) String bankName,
	@NotBlank @Size(max = 100) String accountNumber,
	@NotBlank @Size(max = 100) String accountHolder,
	Long ownerUserId
) {

	public CreatePaymentAccountCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreatePaymentAccountCommand(
			campusId,
			authenticatedUser.userId(),
			accountType,
			nickname,
			bankName,
			accountNumber,
			accountHolder,
			ownerUserId
		);
	}
}
