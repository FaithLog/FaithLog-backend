package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.MealPollSettlementStatus;
import com.faithlog.poll.domain.type.PollStatus;
import java.time.Instant;

public record MealPollManagementListItemResult(
	Long id,
	String title,
	boolean hasNotice,
	boolean hasImages,
	Long thumbnailAssetId,
	PollStatus status,
	Instant startsAt,
	Instant endsAt,
	MealPollSettlementStatus settlementStatus
) {

	public static MealPollManagementListItemResult of(Poll poll, boolean charged) {
		return of(poll, charged, java.util.List.of());
	}

	public static MealPollManagementListItemResult of(Poll poll, boolean charged, java.util.List<Long> imageAssetIds) {
		return new MealPollManagementListItemResult(
			poll.id(), poll.title(), poll.hasNotice(), !imageAssetIds.isEmpty(),
			imageAssetIds.isEmpty() ? null : imageAssetIds.getFirst(), poll.status(), poll.startsAt(), poll.endsAt(),
			charged ? MealPollSettlementStatus.CHARGED : MealPollSettlementStatus.NOT_CHARGED
		);
	}
}
