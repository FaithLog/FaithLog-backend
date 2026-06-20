package com.faithlog.poll.application;

public record CreatePollCommentCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	String content
) {
}
