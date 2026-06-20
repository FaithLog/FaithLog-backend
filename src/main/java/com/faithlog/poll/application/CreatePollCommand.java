package com.faithlog.poll.application;

import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;
import java.util.List;

public record CreatePollCommand(
	Long campusId,
	Long requesterId,
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
	List<CreatePollOptionCommand> options
) {
}
