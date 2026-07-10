package com.faithlog.poll.service.result;

import com.faithlog.campus.service.port.CampusUserLookupResult;
import com.faithlog.poll.domain.entity.PollComment;
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
