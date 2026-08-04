package com.faithlog.user.service.port;

public record CommentActivityRecapAggregate(long writtenCount) {
	public CommentActivityRecapAggregate {
		if (writtenCount < 0) {
			throw new IllegalArgumentException("writtenCount must be nonnegative");
		}
	}
}
