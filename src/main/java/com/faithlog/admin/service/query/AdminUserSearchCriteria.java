package com.faithlog.admin.service.query;

import com.faithlog.user.domain.type.UserRole;

public record AdminUserSearchCriteria(
	String name,
	String email,
	Long userId,
	UserRole role
) {
}
