package com.faithlog.poll.application;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
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
