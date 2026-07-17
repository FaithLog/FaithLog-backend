package com.faithlog.billing.controller.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.billing.service.result.ChargeItemResult;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import java.time.Instant;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record ChargeItemResponse(
	Long id,
	Long campusId,
	Long userId,
	PaymentCategory paymentCategory,
	String title,
	String reason,
	int amount,
	ChargeStatus status,
	Instant paidAt
) {

	public static ChargeItemResponse from(ChargeItemResult result) {
		return new ChargeItemResponse(
			result.id(),
			result.campusId(),
			result.userId(),
			result.paymentCategory(),
			result.title(),
			result.reason(),
			result.amount(),
			result.status(),
			result.paidAt()
		);
	}
}
