package com.faithlog.billing.controller.dto.response;

import com.faithlog.billing.service.result.ChargeAmountSummaryResult;

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
