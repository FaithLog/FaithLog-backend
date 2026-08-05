package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.Instant;

public record PollListItemResult(
	Long id,
	Long campusId,
	String title,
	boolean hasNotice,
	boolean hasImages,
	Long thumbnailAssetId,
	boolean hasAttachments,
	int attachmentCount,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	boolean allowUserOptionAdd,
	Instant startsAt,
	Instant endsAt,
	PollStatus status,
	boolean responded,
	boolean manageableByMe
) {

	public static PollListItemResult of(Poll poll, boolean responded, boolean manageableByMe) {
		return of(poll, responded, manageableByMe, java.util.List.of());
	}

	public static PollListItemResult of(
		Poll poll,
		boolean responded,
		boolean manageableByMe,
		java.util.List<Long> imageAssetIds
	) {
		return of(poll, responded, manageableByMe, imageAssetIds, java.util.List.of());
	}

	public static PollListItemResult of(Poll poll, boolean responded, boolean manageableByMe,
		java.util.List<Long> imageAssetIds, java.util.List<Long> documentAssetIds) {
		return new PollListItemResult(
			poll.id(),
			poll.campusId(),
			poll.title(),
			poll.hasNotice(),
			!imageAssetIds.isEmpty(),
			imageAssetIds.isEmpty() ? null : imageAssetIds.getFirst(),
			!imageAssetIds.isEmpty() || !documentAssetIds.isEmpty(),
			imageAssetIds.size() + documentAssetIds.size(),
			poll.pollType(),
			poll.selectionType(),
			poll.isAnonymous(),
			poll.allowUserOptionAdd(),
			poll.startsAt(),
			poll.endsAt(),
			poll.status(),
			responded,
			manageableByMe
		);
	}
}
