package com.faithlog.billing.service.command;

import com.faithlog.billing.domain.type.PaymentCategory;

public record CreatePaymentAccountCommand(
	Long campusId,
	Long requesterId,
	PaymentCategory accountType,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder,
	Long ownerUserId
) {
}
