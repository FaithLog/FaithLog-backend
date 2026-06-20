package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollListItemResult;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;

public record PollListResponse(
	Long id,
	Long campusId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	boolean isAnonymous,
	Instant startsAt,
	Instant endsAt,
	PollStatus status,
	boolean responded
) {

	public static PollListResponse from(PollListItemResult result) {
		return new PollListResponse(
			result.id(),
			result.campusId(),
			result.title(),
			result.pollType(),
			result.selectionType(),
			result.isAnonymous(),
			result.startsAt(),
			result.endsAt(),
			result.status(),
			result.responded()
		);
	}
}
