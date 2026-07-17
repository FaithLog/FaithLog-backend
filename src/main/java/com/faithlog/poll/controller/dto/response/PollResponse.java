package com.faithlog.poll.controller.dto.response;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.service.result.PollResult;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
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
	boolean allowUserOptionAdd,
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
			result.allowUserOptionAdd(),
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
