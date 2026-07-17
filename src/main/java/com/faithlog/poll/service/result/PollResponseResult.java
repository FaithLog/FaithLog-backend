package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.PollResponse;
import java.time.Instant;
import java.util.List;

public record PollResponseResult(
	Long responseId,
	Long pollId,
	List<Long> optionIds,
	String memo,
	Instant respondedAt
) {

	public static PollResponseResult of(PollResponse response, List<Long> optionIds) {
		return new PollResponseResult(
			response.id(),
			response.pollId(),
			optionIds,
			response.memo(),
			response.respondedAt()
		);
	}
}
