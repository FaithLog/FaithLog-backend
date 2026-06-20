package com.faithlog.notification.application.port;

import com.faithlog.notification.application.NotificationLockKey;
import com.faithlog.notification.application.NotificationLockLease;
import java.time.Duration;
import java.util.Optional;

public interface NotificationLockPort {

	Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl);

	void release(NotificationLockLease lease);
}
