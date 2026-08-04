package com.faithlog.announcement.controller;

import com.faithlog.global.controller.PageSortRequestValidator;
import com.faithlog.global.controller.PageSortRequestValidator.SortValidationRule;
import com.faithlog.global.exception.ErrorCode;
import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class AnnouncementPageRequests {

	private static final SortValidationRule RULE = new SortValidationRule(
		Set.of("publishedAt"),
		ErrorCode.ANNOUNCEMENT_INVALID_PAGE,
		ErrorCode.ANNOUNCEMENT_INVALID_SIZE,
		ErrorCode.ANNOUNCEMENT_INVALID_SORT_FORMAT,
		ErrorCode.ANNOUNCEMENT_INVALID_SORT_PROPERTY,
		ErrorCode.ANNOUNCEMENT_INVALID_SORT_DIRECTION
	);

	private AnnouncementPageRequests() {
	}

	static Pageable stable(int page, int size) {
		Pageable validated = PageSortRequestValidator.pageable(page, size, "publishedAt,desc", RULE);
		return PageRequest.of(validated.getPageNumber(), validated.getPageSize(), Sort.by(
			Sort.Order.desc("pinned"),
			Sort.Order.desc("publishedAt"),
			Sort.Order.desc("id")
		));
	}
}
