package com.faithlog.notification.service.port;

import java.util.UUID;

public interface NotificationDispatchPort {

	void dispatch(UUID requestId);
}
