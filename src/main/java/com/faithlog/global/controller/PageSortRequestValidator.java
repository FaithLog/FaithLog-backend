package com.faithlog.global.controller;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.Collection;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageSortRequestValidator {

	public static final int DEFAULT_PAGE = 0;
	public static final int DEFAULT_SIZE = 20;
	public static final int MAX_SIZE = 100;
	public static final String DEFAULT_SORT = "createdAt,desc";

	private PageSortRequestValidator() {
	}

	public static Pageable pageable(int page, int size, String sort, SortValidationRule rule) {
		if (page < DEFAULT_PAGE) {
			throw new BusinessException(rule.invalidPageCode());
		}
		if (size < 1 || size > MAX_SIZE) {
			throw new BusinessException(rule.invalidSizeCode());
		}
		return PageRequest.of(page, size, sort(sort, rule));
	}

	private static Sort sort(String sort, SortValidationRule rule) {
		String sortValue = sort == null || sort.isBlank() ? DEFAULT_SORT : sort;
		String[] tokens = sortValue.split(",", -1);
		if (tokens.length > 2 || tokens[0].isBlank()) {
			throw new BusinessException(rule.invalidSortFormatCode());
		}
		String property = tokens[0].trim();
		if (!rule.allowedProperties().contains(property)) {
			throw new BusinessException(rule.invalidSortPropertyCode());
		}
		return Sort.by(direction(tokens, rule), property);
	}

	private static Sort.Direction direction(String[] tokens, SortValidationRule rule) {
		if (tokens.length == 1 || tokens[1].isBlank()) {
			return Sort.Direction.DESC;
		}
		String direction = tokens[1].trim();
		if ("asc".equalsIgnoreCase(direction)) {
			return Sort.Direction.ASC;
		}
		if ("desc".equalsIgnoreCase(direction)) {
			return Sort.Direction.DESC;
		}
		throw new BusinessException(rule.invalidSortDirectionCode());
	}

	public record SortValidationRule(
		Collection<String> allowedProperties,
		ErrorCode invalidPageCode,
		ErrorCode invalidSizeCode,
		ErrorCode invalidSortFormatCode,
		ErrorCode invalidSortPropertyCode,
		ErrorCode invalidSortDirectionCode
	) {
	}
}
