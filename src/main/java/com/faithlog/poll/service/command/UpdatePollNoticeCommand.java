package com.faithlog.poll.service.command;

import java.util.List;

public record UpdatePollNoticeCommand(
	Long campusId,
	Long pollId,
	Long requesterId,
	String title,
	String notice,
	List<Long> imageAssetIds,
	List<Long> documentAssetIds
) {
	public UpdatePollNoticeCommand {
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
		documentAssetIds = documentAssetIds == null ? List.of() : List.copyOf(documentAssetIds);
	}
}
