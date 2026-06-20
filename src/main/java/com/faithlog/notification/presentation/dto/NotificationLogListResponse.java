package com.faithlog.notification.presentation.dto;

import com.faithlog.notification.application.NotificationLogItemResult;
import java.util.List;
import org.springframework.data.domain.Page;

public record NotificationLogListResponse(
	List<NotificationLogResponse> items,
	int page,
	int size,
	long totalElements,
	int totalPages
) {

	public static NotificationLogListResponse from(Page<NotificationLogItemResult> page) {
		return new NotificationLogListResponse(
			page.getContent().stream().map(NotificationLogResponse::from).toList(),
			page.getNumber(),
			page.getSize(),
			page.getTotalElements(),
			page.getTotalPages()
		);
	}
}
