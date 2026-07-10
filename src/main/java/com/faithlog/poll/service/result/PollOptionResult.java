package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.PollOption;

public record PollOptionResult(
	Long id,
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder,
	boolean userAdded
) {

	public static PollOptionResult from(PollOption option) {
		return new PollOptionResult(
			option.id(),
			option.content(),
			option.composeMenuCode(),
			option.priceAmount(),
			option.sortOrder(),
			option.userAdded()
		);
	}
}
