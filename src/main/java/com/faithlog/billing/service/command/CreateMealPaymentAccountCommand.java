package com.faithlog.billing.service.command;

public record CreateMealPaymentAccountCommand(
	Long campusId,
	Long requesterId,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder
) {
}
