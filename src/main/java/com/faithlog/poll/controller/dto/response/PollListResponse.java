package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollListItemResult;
import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import java.time.Instant;

public record PollListResponse(
	Long id,
	Long campusId,
	String title,
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

	public static PollListResponse from(PollListItemResult result) {
		return new PollListResponse(
			result.id(),
			result.campusId(),
			result.title(),
			result.pollType(),
			result.selectionType(),
			result.isAnonymous(),
			result.allowUserOptionAdd(),
			result.startsAt(),
			result.endsAt(),
			result.status(),
			result.responded(),
			result.manageableByMe()
		);
	}
}
