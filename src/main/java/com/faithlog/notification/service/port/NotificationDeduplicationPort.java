package com.faithlog.notification.service.port;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import java.time.Duration;

public interface NotificationDeduplicationPort {

	boolean reserve(NotificationDeduplicationKey key, Duration ttl);

	void release(NotificationDeduplicationKey key);
}
