package com.faithlog.billing.controller.dto.request;

import com.faithlog.billing.service.command.CreatePaymentAccountCommand;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record CreatePaymentAccountRequest(
	@NotNull PaymentCategory accountType,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String nickname,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String bankName,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountNumber,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountHolder,
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
