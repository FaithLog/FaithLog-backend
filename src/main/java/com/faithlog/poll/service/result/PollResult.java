package com.faithlog.poll.service.result;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollResult(
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
	List<PollOptionResult> options
) {

	public static PollResult of(Poll poll, List<PollOptionResult> options) {
		return new PollResult(
			poll.id(),
			poll.campusId(),
			poll.templateId(),
			poll.title(),
			poll.pollType(),
			poll.selectionType(),
			poll.isAnonymous(),
			poll.allowUserOptionAdd(),
			poll.chargeGenerationType(),
			poll.paymentCategory(),
			poll.paymentAccountId(),
			poll.startsAt(),
			poll.endsAt(),
			poll.status(),
			options
		);
	}
}
