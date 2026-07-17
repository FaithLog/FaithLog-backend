package com.faithlog.admin.service.policy;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.user.domain.entity.User;

public final class AdminAccessPolicy {

	private AdminAccessPolicy() {
	}

	public static void requireServiceAdmin(User requester) {
		if (!requester.isActive() || !requester.isAdmin()) {
			throw new BusinessException(ErrorCode.ADMIN_ACCESS_FORBIDDEN);
		}
	}
}
