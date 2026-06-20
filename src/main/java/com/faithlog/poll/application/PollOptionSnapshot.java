package com.faithlog.poll.application;

record PollOptionSnapshot(
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder
) {
}
