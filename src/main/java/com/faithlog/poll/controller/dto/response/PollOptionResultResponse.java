package com.faithlog.poll.controller.dto.response;

import com.faithlog.poll.service.result.PollOptionResultView;
import java.util.List;

public record PollOptionResultResponse(
	Long id,
	String content,
	int sortOrder,
	long responseCount,
	List<PollRespondentResponse> respondents
) {

	public static PollOptionResultResponse from(PollOptionResultView result) {
		return new PollOptionResultResponse(
			result.optionId(),
			result.content(),
			result.sortOrder(),
			result.responseCount(),
			result.respondents().stream().map(PollRespondentResponse::from).toList()
		);
	}
}
