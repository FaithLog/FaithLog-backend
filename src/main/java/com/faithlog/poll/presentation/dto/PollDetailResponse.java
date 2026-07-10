package com.faithlog.poll.presentation.dto;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.application.PollDetailResult;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollDetailResponse(
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
	List<PollOptionResponse> options,
	PollMyResponseResponse myResponse
) {

	public static PollDetailResponse from(PollDetailResult result) {
		return new PollDetailResponse(
			result.poll().id(),
			result.poll().campusId(),
			result.poll().templateId(),
			result.poll().title(),
			result.poll().pollType(),
			result.poll().selectionType(),
			result.poll().isAnonymous(),
			result.poll().chargeGenerationType(),
			result.poll().paymentCategory(),
			result.poll().paymentAccountId(),
			result.poll().startsAt(),
			result.poll().endsAt(),
			result.poll().status(),
			result.poll().options().stream().map(PollOptionResponse::from).toList(),
			PollMyResponseResponse.from(result.myResponse())
		);
	}
}
