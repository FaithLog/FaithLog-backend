package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollResponseResult;
import java.time.Instant;
import java.util.List;

public record PollMyResponseResponse(
	Long responseId,
	Long pollId,
	List<Long> optionIds,
	String memo,
	Instant respondedAt
) {

	public static PollMyResponseResponse from(PollResponseResult result) {
		if (result == null) {
			return null;
		}
		return new PollMyResponseResponse(
			result.responseId(),
			result.pollId(),
			result.optionIds(),
			result.memo(),
			result.respondedAt()
		);
	}
}
