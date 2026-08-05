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
	String notice,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	Boolean allowUserOptionAdd,
	ChargeGenerationType chargeGenerationType,
	PaymentCategory paymentCategory,
	Long paymentAccountId,
	Instant startsAt,
	Instant endsAt,
	List<CreatePollOptionCommand> options,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public CreatePollCommand {
		options = options == null ? List.of() : List.copyOf(options);
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}
	public CreatePollCommand(
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
		this(campusId, requesterId, templateId, title, null, pollType, selectionType, isAnonymous,
			allowUserOptionAdd, chargeGenerationType, paymentCategory, paymentAccountId, startsAt, endsAt, options,
			List.of(), List.of());
	}

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
			null,
			pollType,
			selectionType,
			isAnonymous,
			null,
			chargeGenerationType,
			paymentCategory,
			paymentAccountId,
			startsAt,
			endsAt,
			options,
			List.of(),
			List.of()
		);
	}
}
