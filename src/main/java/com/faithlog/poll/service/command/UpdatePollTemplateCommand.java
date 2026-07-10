package com.faithlog.poll.service.command;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record UpdatePollTemplateCommand(
	Long campusId,
	Long templateId,
	Long requesterId,
	String title,
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

	public UpdatePollTemplateCommand(
		Long campusId,
		Long templateId,
		Long requesterId,
		String title,
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
			templateId,
			requesterId,
			title,
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
