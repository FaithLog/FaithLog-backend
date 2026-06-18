package com.faithlog.billing.presentation.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.faithlog.billing.application.ChargeItemResult;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.domain.PaymentCategory;
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
