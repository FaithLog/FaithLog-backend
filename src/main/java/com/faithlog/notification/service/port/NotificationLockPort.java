package com.faithlog.notification.service.port;

import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import java.time.Duration;
import java.util.Optional;

public interface NotificationLockPort {

	Optional<NotificationLockLease> acquire(NotificationLockKey key, Duration ttl);

	void release(NotificationLockLease lease);
}
