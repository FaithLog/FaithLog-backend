package com.faithlog.admin.application;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.User;

public final class AdminAccessPolicy {

	private AdminAccessPolicy() {
	}

	public static void requireServiceAdmin(User requester) {
		if (!requester.isActive() || !requester.isAdmin()) {
			throw new BusinessException(ErrorCode.ADMIN_ACCESS_FORBIDDEN);
		}
	}
}
