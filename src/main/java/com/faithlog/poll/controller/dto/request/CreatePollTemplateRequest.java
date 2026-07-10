package com.faithlog.poll.controller.dto.request;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.CreatePollTemplateCommand;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.time.DayOfWeek;
import java.time.LocalTime;
import java.util.List;

public record CreatePollTemplateRequest(
	@NotBlank String title,
	@NotNull PollType pollType,
	@NotNull SelectionType selectionType,
	@NotNull ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	boolean allowUserOptionAdd,
	boolean autoCreateEnabled,
	@NotNull @Min(1) @Max(7) Integer startDayOfWeek,
	@NotNull LocalTime startTime,
	@NotNull @Min(1) @Max(7) Integer endDayOfWeek,
	@NotNull LocalTime endTime,
	@NotEmpty @Valid List<PollTemplateOptionRequest> options
) {

	public CreatePollTemplateCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreatePollTemplateCommand(
			campusId,
			authenticatedUser.userId(),
			title,
			pollType,
			selectionType,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			allowUserOptionAdd,
			autoCreateEnabled,
			DayOfWeek.of(startDayOfWeek),
			startTime,
			DayOfWeek.of(endDayOfWeek),
			endTime,
			options.stream().map(PollTemplateOptionRequest::toCommand).toList()
		);
	}
}
