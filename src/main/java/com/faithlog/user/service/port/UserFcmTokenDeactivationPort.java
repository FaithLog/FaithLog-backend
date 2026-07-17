package com.faithlog.user.service.port;

public interface UserFcmTokenDeactivationPort {

	void deactivateAllForUser(Long userId);
}
