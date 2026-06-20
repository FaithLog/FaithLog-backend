package com.faithlog.poll.application;

public record DeletePollCommentCommand(
	Long campusId,
	Long pollId,
	Long commentId,
	Long requesterId
) {
}
