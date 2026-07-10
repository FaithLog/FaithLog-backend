package com.faithlog.poll.service.result;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.PollTemplate;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record PollTemplateResult(
	Long id,
	Long campusId,
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
	boolean isDefault,
	boolean isActive,
	List<PollTemplateOptionResult> options
) {

	public static PollTemplateResult of(PollTemplate template, List<PollTemplateOptionResult> options) {
		return new PollTemplateResult(
			template.id(),
			template.campusId(),
			template.title(),
			template.pollType(),
			template.selectionType(),
			template.chargeGenerationType(),
			template.paymentCategory(),
			template.paymentAccountId(),
			template.allowUserOptionAdd(),
			template.autoCreateEnabled(),
			template.startDayOfWeek(),
			template.startTime(),
			template.endDayOfWeek(),
			template.endTime(),
			template.isDefault(),
			template.isActive(),
			options
		);
	}
}
