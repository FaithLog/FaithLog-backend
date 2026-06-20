package com.faithlog.notification.application;

import com.faithlog.campus.application.policy.CampusRolePolicy;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import jakarta.persistence.criteria.Predicate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationLogQueryService {

	private final NotificationLogRepository notificationLogRepository;
	private final UserRepository userRepository;
	private final CampusMemberRepository campusMemberRepository;

	public NotificationLogQueryService(
		NotificationLogRepository notificationLogRepository,
		UserRepository userRepository,
		CampusMemberRepository campusMemberRepository
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.userRepository = userRepository;
		this.campusMemberRepository = campusMemberRepository;
	}

	@Transactional(readOnly = true)
	public Page<NotificationLogItemResult> searchLogs(
		Long requesterId,
		NotificationLogSearchCriteria criteria,
		Pageable pageable
	) {
		requireNotificationLogViewer(criteria.campusId(), requesterId);
		return notificationLogRepository.findAll((root, query, criteriaBuilder) -> {
			List<Predicate> predicates = new ArrayList<>();
			predicates.add(criteriaBuilder.equal(root.get("campusId"), criteria.campusId()));
			if (criteria.notificationType() != null) {
				predicates.add(criteriaBuilder.equal(root.get("notificationType"), criteria.notificationType()));
			}
			if (criteria.sendStatus() != null) {
				predicates.add(criteriaBuilder.equal(root.get("sendStatus"), criteria.sendStatus()));
			}
			if (criteria.targetWeekStartDate() != null) {
				predicates.add(criteriaBuilder.equal(root.get("targetWeekStartDate"), criteria.targetWeekStartDate()));
			}
			if (criteria.targetId() != null) {
				predicates.add(criteriaBuilder.equal(root.get("targetId"), criteria.targetId()));
			}
			if (criteria.requestId() != null) {
				predicates.add(criteriaBuilder.equal(root.get("requestId"), criteria.requestId()));
			}
			if (criteria.startDate() != null) {
				predicates.add(criteriaBuilder.greaterThanOrEqualTo(
					root.get("createdAt"),
					criteria.startDate().atStartOfDay().toInstant(ZoneOffset.UTC)
				));
			}
			if (criteria.endDate() != null) {
				predicates.add(criteriaBuilder.lessThan(
					root.get("createdAt"),
					criteria.endDate().plusDays(1).atStartOfDay().toInstant(ZoneOffset.UTC)
				));
			}
			return criteriaBuilder.and(predicates.toArray(Predicate[]::new));
		}, pageable).map(log -> NotificationLogItemResult.of(log, getUserOrThrow(log)));
	}

	private void requireNotificationLogViewer(Long campusId, Long requesterId) {
		User requester = userRepository.findById(requesterId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.id())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.NOTIFICATION_LOG_LIST_FORBIDDEN));
		CampusRolePolicy.requireCampusManager(requesterMembership, ErrorCode.NOTIFICATION_LOG_LIST_FORBIDDEN);
	}

	private User getUserOrThrow(NotificationLog log) {
		return userRepository.findById(log.userId())
			.orElseThrow(() -> new BusinessException(ErrorCode.ADMIN_USER_NOT_FOUND));
	}
}
