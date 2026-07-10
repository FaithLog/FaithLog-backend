package com.faithlog.poll.service.command;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
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
	Boolean allowUserOptionAdd,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	Instant startsAt,
	Instant endsAt,
	List<CreatePollOptionCommand> options
) {

	public CreatePollCommand(
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
		this(
			campusId,
			requesterId,
			templateId,
			title,
			pollType,
			selectionType,
			isAnonymous,
			null,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			startsAt,
			endsAt,
			options
		);
	}
}
