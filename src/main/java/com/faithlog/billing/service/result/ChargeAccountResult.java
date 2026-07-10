package com.faithlog.billing.service.result;

public record ChargeAccountResult(
	Long paymentAccountId,
	String bankName,
	String accountNumber,
	String accountHolder
) {
}
