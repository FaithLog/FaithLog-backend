package com.faithlog.poll.service.command;

public record UpdatePollCommentCommand(
	Long campusId,
	Long pollId,
	Long commentId,
	Long requesterId,
	String content
) {
}
