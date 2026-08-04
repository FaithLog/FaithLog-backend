package com.faithlog.poll.controller.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Positive;
import java.util.List;

public record UpdatePollNoticeRequest(
	@NotBlank @Size(max = 200) String title,
	@Size(max = 5_000) String notice,
	List<@Positive Long> imageAssetIds
) {
	public UpdatePollNoticeRequest(String title, String notice) {
		this(title, notice, List.of());
	}

	public UpdatePollNoticeRequest {
		title = title == null ? null : title.trim();
		notice = notice == null || notice.isBlank() ? null : notice.trim();
		imageAssetIds = imageAssetIds == null ? List.of() : List.copyOf(imageAssetIds);
	}
}
