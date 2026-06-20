package com.faithlog.notification.infrastructure.jpa;

import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.SendStatus;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>, JpaSpecificationExecutor<NotificationLog> {

	List<NotificationLog> findByRequestIdOrderByIdAsc(UUID requestId);

	List<NotificationLog> findByRequestIdAndSendStatusOrderByIdAsc(UUID requestId, SendStatus sendStatus);
}
