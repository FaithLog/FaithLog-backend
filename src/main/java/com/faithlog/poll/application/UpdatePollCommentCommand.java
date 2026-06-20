package com.faithlog.poll.application;

public record UpdatePollCommentCommand(
	Long campusId,
	Long pollId,
	Long commentId,
	Long requesterId,
	String content
) {
}
