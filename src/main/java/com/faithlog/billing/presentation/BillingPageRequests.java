package com.faithlog.billing.presentation;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

final class BillingPageRequests {

	private static final String DEFAULT_SORT = "createdAt,desc";
	private static final List<String> CHARGE_ITEM_SORT_PROPERTIES = List.of(
		"createdAt", "dueDate", "paidAt", "amount", "status", "paymentCategory"
	);
	private static final List<String> ADMIN_MEMBER_SORT_PROPERTIES = List.of(
		"createdAt", "userId", "name", "email", "totalAmount", "unpaidAmount", "paidAmount", "waivedAmount",
		"canceledAmount"
	);

	private BillingPageRequests() {
	}

	static Pageable chargeItems(int page, int size, String sort) {
		return pageable(page, size, sort, CHARGE_ITEM_SORT_PROPERTIES);
	}

	static Pageable adminMembers(int page, int size, String sort) {
		return pageable(page, size, sort, ADMIN_MEMBER_SORT_PROPERTIES);
	}

	private static Pageable pageable(int page, int size, String sort, List<String> allowedSortProperties) {
		int safePage = Math.max(page, 0);
		int safeSize = Math.min(Math.max(size, 1), 100);
		return PageRequest.of(safePage, safeSize, sort(sort, allowedSortProperties));
	}

	private static Sort sort(String sort, List<String> allowedSortProperties) {
		String sortValue = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
		String[] tokens = sortValue.split(",");
		String property = tokens[0].trim();
		if (!allowedSortProperties.contains(property)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST, "지원하지 않는 정렬 기준입니다.");
		}
		Sort.Direction direction = tokens.length > 1 && "asc".equalsIgnoreCase(tokens[1].trim())
			? Sort.Direction.ASC
			: Sort.Direction.DESC;
		return Sort.by(direction, property);
	}
}
