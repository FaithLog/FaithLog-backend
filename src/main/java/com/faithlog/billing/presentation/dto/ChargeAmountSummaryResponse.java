package com.faithlog.billing.presentation.dto;

import com.faithlog.billing.application.ChargeAmountSummaryResult;

public record ChargeAmountSummaryResponse(
	int totalAmount,
	int unpaidAmount,
	int paidAmount,
	int waivedAmount,
	int canceledAmount
) {

	public static ChargeAmountSummaryResponse from(ChargeAmountSummaryResult result) {
		return new ChargeAmountSummaryResponse(
			result.totalAmount(),
			result.unpaidAmount(),
			result.paidAmount(),
			result.waivedAmount(),
			result.canceledAmount()
		);
	}
}
