package com.faithlog.poll.presentation.dto;

import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.poll.application.PollResult;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollResponse(
	Long id,
	Long campusId,
	Long templateId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	Instant startsAt,
	Instant endsAt,
	PollStatus status,
	List<PollOptionResponse> options
) {

	public static PollResponse from(PollResult result) {
		return new PollResponse(
			result.id(),
			result.campusId(),
			result.templateId(),
			result.title(),
			result.pollType(),
			result.selectionType(),
			result.isAnonymous(),
			result.chargeGenerationType(),
			result.paymentCategory(),
			result.paymentAccountId(),
			result.startsAt(),
			result.endsAt(),
			result.status(),
			result.options().stream().map(PollOptionResponse::from).toList()
		);
	}
}
