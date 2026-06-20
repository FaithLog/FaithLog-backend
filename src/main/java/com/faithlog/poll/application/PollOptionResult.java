package com.faithlog.poll.application;

import com.faithlog.poll.domain.PollOption;

public record PollOptionResult(
	Long id,
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder
) {

	public static PollOptionResult from(PollOption option) {
		return new PollOptionResult(
			option.id(),
			option.content(),
			option.composeMenuCode(),
			option.priceAmount(),
			option.sortOrder()
		);
	}
}
