package com.faithlog.billing.controller;

import com.faithlog.global.exception.ErrorCode;
import com.faithlog.global.controller.PageSortRequestValidator;
import com.faithlog.global.controller.PageSortRequestValidator.SortValidationRule;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class BillingPageRequests {

	private static final List<String> CHARGE_ITEM_SORT_PROPERTIES = List.of(
		"createdAt", "dueDate", "paidAt", "amount", "status", "paymentCategory"
	);
	private static final List<String> ADMIN_MEMBER_SORT_PROPERTIES = List.of(
		"createdAt", "userId", "name", "email", "totalAmount", "unpaidAmount", "paidAmount", "waivedAmount",
		"canceledAmount"
	);
	private static final SortValidationRule CHARGE_ITEM_SORT_RULE = billingSortRule(CHARGE_ITEM_SORT_PROPERTIES);
	private static final SortValidationRule ADMIN_MEMBER_SORT_RULE = billingSortRule(ADMIN_MEMBER_SORT_PROPERTIES);

	private BillingPageRequests() {
	}

	static Pageable chargeItems(int page, int size, String sort) {
		Pageable primary = PageSortRequestValidator.pageable(page, size, sort, CHARGE_ITEM_SORT_RULE);
		Sort.Order primaryOrder = primary.getSort().iterator().next();
		Sort stableSort = primary.getSort().and(Sort.by(primaryOrder.getDirection(), "id"));
		return PageRequest.of(primary.getPageNumber(), primary.getPageSize(), stableSort);
	}

	static Pageable adminMembers(int page, int size, String sort) {
		return PageSortRequestValidator.pageable(page, size, sort, ADMIN_MEMBER_SORT_RULE);
	}

	private static SortValidationRule billingSortRule(List<String> allowedSortProperties) {
		return new SortValidationRule(
			allowedSortProperties,
			ErrorCode.BILLING_INVALID_PAGE,
			ErrorCode.BILLING_INVALID_SIZE,
			ErrorCode.BILLING_INVALID_SORT_FORMAT,
			ErrorCode.BILLING_INVALID_SORT_PROPERTY,
			ErrorCode.BILLING_INVALID_SORT_DIRECTION
		);
	}
}
