package com.faithlog.poll.application;

import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.poll.domain.PollComment;
import java.time.Instant;

public record PollCommentResult(
	Long commentId,
	Long pollId,
	Long userId,
	String name,
	String content,
	boolean deleted,
	Instant createdAt,
	Instant updatedAt
) {

	public static PollCommentResult of(PollComment comment, CampusUserLookupResult author) {
		return new PollCommentResult(
			comment.id(),
			comment.pollId(),
			comment.userId(),
			author.name(),
			comment.content(),
			comment.isDeleted(),
			comment.createdAt(),
			comment.updatedAt()
		);
	}
}
