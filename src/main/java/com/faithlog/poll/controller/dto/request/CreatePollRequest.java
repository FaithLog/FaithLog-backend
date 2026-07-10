package com.faithlog.poll.controller.dto.request;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.global.security.AuthenticatedUser;
import com.faithlog.poll.service.command.CreatePollCommand;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.Instant;
import java.util.List;

public record CreatePollRequest(
	Long templateId,
	@NotBlank String title,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	Boolean allowUserOptionAdd,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	@NotNull Instant startsAt,
	@NotNull Instant endsAt,
	@Valid List<PollOptionRequest> options
) {

	public CreatePollCommand toCommand(Long campusId, AuthenticatedUser authenticatedUser) {
		return new CreatePollCommand(
			campusId,
			authenticatedUser.userId(),
			templateId,
			title,
			pollType,
			selectionType,
			isAnonymous,
			allowUserOptionAdd,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			startsAt,
			endsAt,
			options == null ? List.of() : options.stream().map(PollOptionRequest::toCommand).toList()
		);
	}
}
