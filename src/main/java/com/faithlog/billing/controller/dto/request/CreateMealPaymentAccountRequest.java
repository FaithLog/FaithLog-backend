package com.faithlog.billing.controller.dto.request;

import com.faithlog.billing.service.command.CreateMealPaymentAccountCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateMealPaymentAccountRequest(
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String nickname,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String bankName,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountNumber,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountHolder
) {

	public CreateMealPaymentAccountCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreateMealPaymentAccountCommand(
			campusId,
			authenticatedUser.userId(),
			nickname,
			bankName,
			accountNumber,
			accountHolder
		);
	}
}
