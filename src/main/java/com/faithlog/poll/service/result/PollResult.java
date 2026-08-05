package com.faithlog.poll.service.result;

import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollResult(
	Long id,
	Long campusId,
	Long templateId,
	String title,
	String notice,
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
	List<PollOptionResult> options,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public PollResult {
		options = options == null ? List.of() : List.copyOf(options);
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}

	public static PollResult of(Poll poll, List<PollOptionResult> options) {
		return of(poll, options, List.of(), List.of());
	}

	public static PollResult of(Poll poll, List<PollOptionResult> options, List<Long> imageAssetIds) {
		return of(poll, options, imageAssetIds, List.of());
	}

	public static PollResult of(Poll poll, List<PollOptionResult> options, List<Long> imageAssetIds,
		List<Long> documentAssetIds) {
		return new PollResult(
			poll.id(),
			poll.campusId(),
			poll.templateId(),
			poll.title(),
			poll.notice(),
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
			options,
			imageAssetIds,
			documentAssetIds
		);
	}
}
