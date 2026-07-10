package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.type.PollStatus;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
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
