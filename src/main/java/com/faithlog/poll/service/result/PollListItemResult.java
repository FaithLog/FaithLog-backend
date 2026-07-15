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
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	Instant startsAt,
	Instant endsAt,
	PollStatus status,
	boolean responded,
	boolean manageableByMe
) {

	public static PollListItemResult of(Poll poll, boolean responded, boolean manageableByMe) {
		return new PollListItemResult(
			poll.id(),
			poll.campusId(),
			poll.title(),
			poll.pollType(),
			poll.selectionType(),
			poll.isAnonymous(),
			poll.startsAt(),
			poll.endsAt(),
			poll.status(),
			responded,
			manageableByMe
		);
	}
}
