package com.faithlog.billing.controller.dto.response;

import com.faithlog.billing.service.result.PaymentAccountResult;
import com.faithlog.billing.domain.type.PaymentCategory;

public record PaymentAccountMemberResponse(
	Long id,
	PaymentCategory accountType,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder
) {

	public static PaymentAccountMemberResponse from(PaymentAccountResult result) {
		return new PaymentAccountMemberResponse(
			result.id(),
			result.accountType(),
			result.nickname(),
			result.bankName(),
			result.accountNumber(),
			result.accountHolder()
		);
	}
}
