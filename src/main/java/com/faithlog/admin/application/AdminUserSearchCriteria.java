package com.faithlog.admin.application;

import com.faithlog.user.domain.UserRole;

public record AdminUserSearchCriteria(
	String name,
	String email,
	Long userId,
	UserRole role
) {
}
