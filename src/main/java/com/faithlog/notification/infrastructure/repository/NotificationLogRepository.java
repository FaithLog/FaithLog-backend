package com.faithlog.notification.infrastructure.repository;

import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.SendStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface NotificationLogRepository extends JpaRepository<NotificationLog, Long>, JpaSpecificationExecutor<NotificationLog> {

	List<NotificationLog> findByRequestIdOrderByIdAsc(UUID requestId);

	List<NotificationLog> findByRequestIdAndSendStatusOrderByIdAsc(UUID requestId, SendStatus sendStatus);

	List<NotificationLog> findBySendStatusAndCreatedAtLessThanEqualOrderByIdAsc(SendStatus sendStatus, Instant createdAt);

	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Query("delete from NotificationLog log where log.createdAt < :createdAt")
	int deleteByCreatedAtBefore(@Param("createdAt") Instant createdAt);
}
