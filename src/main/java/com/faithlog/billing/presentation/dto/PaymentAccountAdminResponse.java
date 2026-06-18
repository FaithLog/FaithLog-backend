package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.PaymentAccountResult;
import com.faithlog.billing.domain.PaymentCategory;

public record PaymentAccountAdminResponse(
	Long id,
	Long campusId,
	PaymentCategory accountType,
	String nickname,
	String bankName,
	String accountNumber,
	String accountHolder,
	Long ownerUserId,
	boolean isActive
) {

	public static PaymentAccountAdminResponse from(PaymentAccountResult result) {
		return new PaymentAccountAdminResponse(
			result.id(),
			result.campusId(),
			result.accountType(),
			result.nickname(),
			result.bankName(),
			result.accountNumber(),
			result.accountHolder(),
			result.ownerUserId(),
			result.isActive()
		);
	}
}
