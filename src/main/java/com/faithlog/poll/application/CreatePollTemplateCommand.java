package com.faithlog.poll.application;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CreatePollTemplateCommand(
	Long campusId,
	Long requesterId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	boolean allowUserOptionAdd,
	boolean autoCreateEnabled,
	DayOfWeek startDayOfWeek,
	LocalTime startTime,
	DayOfWeek endDayOfWeek,
	LocalTime endTime,
	List<CreatePollTemplateOptionCommand> options
) {

	public CreatePollTemplateCommand(
		Long campusId,
		Long requesterId,
		String title,
		PollType pollType,
		SelectionType selectionType,
		ChargeGenerationType chargeGenerationType,
		PaymentCategory paymentCategory,
		Long paymentAccountId,
		boolean autoCreateEnabled,
		DayOfWeek startDayOfWeek,
		LocalTime startTime,
		DayOfWeek endDayOfWeek,
		LocalTime endTime,
		List<CreatePollTemplateOptionCommand> options
	) {
		this(
			campusId,
			requesterId,
			title,
			pollType,
			selectionType,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			false,
			autoCreateEnabled,
			startDayOfWeek,
			startTime,
			endDayOfWeek,
			endTime,
			options
		);
	}
}
