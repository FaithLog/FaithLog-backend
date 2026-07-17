package com.faithlog.poll.service.result;

import java.util.List;

public record PollOptionResultView(
	Long optionId,
	String content,
	int sortOrder,
	long responseCount,
	List<PollRespondentResult> respondents
) {
}
