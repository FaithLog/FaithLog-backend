package com.faithlog.notification.application.port;

import com.faithlog.notification.application.NotificationDeduplicationKey;
import java.time.Duration;

public interface NotificationDeduplicationPort {

	boolean reserve(NotificationDeduplicationKey key, Duration ttl);
}
