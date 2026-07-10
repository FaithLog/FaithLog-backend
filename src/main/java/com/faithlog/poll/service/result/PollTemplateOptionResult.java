package com.faithlog.poll.service.result;

import com.faithlog.poll.domain.entity.PollTemplateOption;

public record PollTemplateOptionResult(
	Long id,
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder
) {

	public static PollTemplateOptionResult from(PollTemplateOption option) {
		return new PollTemplateOptionResult(
			option.id(),
			option.content(),
			option.composeMenuCode(),
			option.priceAmount(),
			option.sortOrder()
		);
	}
}
