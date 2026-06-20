package com.faithlog.poll.application;

import java.util.List;

public record PollOptionResultView(
	Long optionId,
	String content,
	int sortOrder,
	long responseCount,
	List<PollRespondentResult> respondents
) {
}
