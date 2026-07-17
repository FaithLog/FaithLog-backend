package com.faithlog.poll.service;

record PollOptionSnapshot(
	String content,
	String composeMenuCode,
	int priceAmount,
	int sortOrder
) {
}
