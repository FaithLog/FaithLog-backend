package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollResultView;
import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollResultsResponse(
	Long pollId,
	Long campusId,
	String title,
	PollType pollType,
	SelectionType selectionType,
	boolean anonymous,
	PollStatus status,
	Instant startsAt,
	Instant endsAt,
	long targetMemberCount,
	long respondedCount,
	long notRespondedCount,
	List<PollOptionResultResponse> optionResults
) {

	public static PollResultsResponse from(PollResultView result) {
		return new PollResultsResponse(
			result.pollId(),
			result.campusId(),
			result.title(),
			result.pollType(),
			result.selectionType(),
			result.anonymous(),
			result.status(),
			result.startsAt(),
			result.endsAt(),
			result.targetMemberCount(),
			result.respondedCount(),
			result.notRespondedCount(),
			result.optionResults().stream().map(PollOptionResultResponse::from).toList()
		);
	}
}
