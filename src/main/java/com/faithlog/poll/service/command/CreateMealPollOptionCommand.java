package com.faithlog.poll.service.command;

import java.util.Set;

public record CreateMealPollOptionCommand(
	String content,
	int sortOrder,
	Set<String> unknownFields
) {
}
