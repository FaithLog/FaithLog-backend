package com.faithlog.poll.presentation.dto;

import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.poll.application.PollTemplateResult;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.LocalTime;
import java.util.List;

public record PollTemplateResponse(
	Long id,
	Long campusId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	boolean autoCreateEnabled,
	int startDayOfWeek,
	LocalTime startTime,
	int endDayOfWeek,
	LocalTime endTime,
	boolean isDefault,
	boolean isActive,
	List<PollTemplateOptionResponse> options
) {

	public static PollTemplateResponse from(PollTemplateResult result) {
		return new PollTemplateResponse(
			result.id(),
			result.campusId(),
			result.title(),
			result.pollType(),
			result.selectionType(),
			result.chargeGenerationType(),
			result.paymentCategory(),
			result.paymentAccountId(),
			result.autoCreateEnabled(),
			result.startDayOfWeek().getValue(),
			result.startTime(),
			result.endDayOfWeek().getValue(),
			result.endTime(),
			result.isDefault(),
			result.isActive(),
			result.options().stream().map(PollTemplateOptionResponse::from).toList()
		);
	}
}
