package com.faithlog.admin.controller;

import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.controller.PageSortRequestValidator;
import java.util.Set;
import org.springframework.data.domain.Pageable;

public final class AdminPageRequests {

	private static final PageSortRequestValidator.SortValidationRule USER_SORT_RULE =
		new PageSortRequestValidator.SortValidationRule(
			Set.of("id", "name", "email", "role", "createdAt"),
			ErrorCode.ADMIN_INVALID_PAGE,
			ErrorCode.ADMIN_INVALID_SIZE,
			ErrorCode.ADMIN_INVALID_SORT_FORMAT,
			ErrorCode.ADMIN_INVALID_SORT_PROPERTY,
			ErrorCode.ADMIN_INVALID_SORT_DIRECTION
		);

	private static final PageSortRequestValidator.SortValidationRule CAMPUS_SORT_RULE =
		new PageSortRequestValidator.SortValidationRule(
			Set.of("id", "name", "region", "createdAt"),
			ErrorCode.ADMIN_INVALID_PAGE,
			ErrorCode.ADMIN_INVALID_SIZE,
			ErrorCode.ADMIN_INVALID_SORT_FORMAT,
			ErrorCode.ADMIN_INVALID_SORT_PROPERTY,
			ErrorCode.ADMIN_INVALID_SORT_DIRECTION
		);

	private AdminPageRequests() {
	}

	public static Pageable users(int page, int size, String sort) {
		return PageSortRequestValidator.pageable(page, size, sort, USER_SORT_RULE);
	}

	public static Pageable campuses(int page, int size, String sort) {
		return PageSortRequestValidator.pageable(page, size, sort, CAMPUS_SORT_RULE);
	}
}
