package com.faithlog.billing.service.command;

public record UpdatePaymentAccountCommand(
	Long campusId,
	Long accountId,
	Long requesterId,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder
) {
}
