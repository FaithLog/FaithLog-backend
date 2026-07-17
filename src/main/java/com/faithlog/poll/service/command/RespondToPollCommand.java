package com.faithlog.poll.service.command;

import java.util.List;

public record RespondToPollCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	List<Long> optionIds,
	String memo
) {
}
