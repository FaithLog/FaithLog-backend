package com.faithlog.poll.service.command;

import java.time.Instant;
import java.util.List;
import java.util.Set;

public record CreateMealPollCommand(
	Long campusId,
	Long requesterId,
	String title,
	String notice,
	boolean isAnonymous,
	boolean allowUserOptionAdd,
	Instant endsAt,
	List<CreateMealPollOptionCommand> options,
	Set<String> unknownFields,
	List<Long> imageAssetIds
) {
	public CreateMealPollCommand {
		options = options == null ? List.of() : List.copyOf(options);
		unknownFields = unknownFields == null ? Set.of() : Set.copyOf(unknownFields);
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
	}
}
