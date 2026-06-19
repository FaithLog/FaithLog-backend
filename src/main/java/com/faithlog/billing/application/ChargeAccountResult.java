package com.faithlog.billing.application;

public record ChargeAccountResult(
	Long paymentAccountId,
	String bankName,
	String accountNumber,
	String accountHolder
) {
}
