package com.faithlog.notification.service.port;

import com.faithlog.notification.service.NotificationDeduplicationKey;
import com.faithlog.notification.service.NotificationDeduplicationReservation;
import java.time.Duration;
import java.util.Optional;

public interface NotificationDeduplicationPort {

	Optional<NotificationDeduplicationReservation> reserve(NotificationDeduplicationKey key, Duration ttl);

	void release(NotificationDeduplicationReservation reservation);
}
