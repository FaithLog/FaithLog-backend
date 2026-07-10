package com.faithlog.poll.service.command;

public record CreatePollCommentCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	String content
) {
}
