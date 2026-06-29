package com.faithlog.poll.application;

public record AddPollOptionCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	String content
) {
}
