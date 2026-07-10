package com.faithlog.poll.service.command;

public record DeletePollCommentCommand(
	Long campusId,
	Long pollId,
	Long commentId,
	Long requesterId
) {
}
