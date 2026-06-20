package com.faithlog.poll.application;

import com.faithlog.poll.domain.PollStatus;
import com.faithlog.poll.domain.PollType;
import com.faithlog.poll.domain.SelectionType;
import java.time.Instant;
import java.util.List;

public record PollResultView(
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
	List<PollOptionResultView> optionResults
) {
}
