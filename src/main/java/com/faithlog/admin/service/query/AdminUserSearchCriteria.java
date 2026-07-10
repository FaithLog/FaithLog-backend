package com.faithlog.admin.service.query;

import com.faithlog.user.domain.UserRole;

public record AdminUserSearchCriteria(
	String name,
	String email,
	Long userId,
	UserRole role
) {
}
