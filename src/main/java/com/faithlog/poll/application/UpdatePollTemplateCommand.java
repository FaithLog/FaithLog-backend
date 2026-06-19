package com.faithlog.poll.application;

import com.faithlog.billing.domain.PaymentCategory;
import com.faithlog.poll.domain.ChargeGenerationType;
import com.faithlog.poll.domain.SelectionType;
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
	boolean autoCreateEnabled,
	DayOfWeek startDayOfWeek,
	LocalTime startTime,
	DayOfWeek endDayOfWeek,
	LocalTime endTime,
	List<CreatePollTemplateOptionCommand> options
) {
}
