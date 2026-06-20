package com.faithlog.notification.application.port;

import java.util.UUID;

public interface NotificationDispatchPort {

	void dispatch(UUID requestId);
}
