package com.faithlog.billing.application;

import com.faithlog.billing.domain.PaymentCategory;

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
