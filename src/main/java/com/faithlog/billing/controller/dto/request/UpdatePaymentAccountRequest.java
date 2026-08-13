package com.faithlog.billing.controller.dto.request;

import com.faithlog.billing.service.command.UpdatePaymentAccountCommand;
import com.faithlog.global.security.AuthenticatedUser;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdatePaymentAccountRequest(
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String nickname,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String bankName,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountNumber,
	@NotBlank(message = "공백일 수 없습니다") @Size(max = 100, message = "100자 이하여야 합니다") String accountHolder
) {

	public UpdatePaymentAccountRequest {
		nickname = trim(nickname);
		bankName = trim(bankName);
		accountNumber = trim(accountNumber);
		accountHolder = trim(accountHolder);
	}

	public UpdatePaymentAccountCommand toCommand(
		Long campusId,
		Long accountId,
		AuthenticatedUser authenticatedUser
	) {
		return new UpdatePaymentAccountCommand(
			campusId,
			accountId,
			authenticatedUser.userId(),
			nickname,
			bankName,
			accountNumber,
			accountHolder
		);
	}

	private static String trim(String value) {
		return value == null ? null : value.trim();
	}
}
