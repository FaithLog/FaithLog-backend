package com.faithlog.poll.presentation.dto;

import com.faithlog.poll.application.PollCommentResult;
import java.time.Instant;

public record PollCommentResponse(
	Long commentId,
	Long pollId,
	Long userId,
	String name,
	String content,
	boolean deleted,
	Instant createdAt,
	Instant updatedAt
) {

	public static PollCommentResponse from(PollCommentResult result) {
		return new PollCommentResponse(
			result.commentId(),
			result.pollId(),
			result.userId(),
			result.name(),
			result.content(),
			result.deleted(),
			result.createdAt(),
			result.updatedAt()
		);
	}
}
