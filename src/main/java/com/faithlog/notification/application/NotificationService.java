package com.faithlog.notification.application;

import com.faithlog.billing.domain.ChargeItem;
import com.faithlog.billing.domain.ChargeStatus;
import com.faithlog.billing.infrastructure.jpa.ChargeItemRepository;
import com.faithlog.campus.application.policy.CampusRolePolicy;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.domain.CampusMemberStatus;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.devotion.domain.WeeklyDevotionRecord;
import com.faithlog.devotion.infrastructure.jpa.WeeklyDevotionRecordRepository;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.application.port.NotificationDispatchPort;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import com.faithlog.poll.domain.Poll;
import com.faithlog.poll.infrastructure.jpa.PollRepository;
import com.faithlog.poll.infrastructure.jpa.PollResponseRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NotificationService {

	private final NotificationLogRepository notificationLogRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final UserRepository userRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;
	private final PollRepository pollRepository;
	private final PollResponseRepository pollResponseRepository;
	private final ChargeItemRepository chargeItemRepository;
	private final NotificationDispatchPort notificationDispatchPort;
	private final NotificationLockService notificationLockService;

	public NotificationService(
		NotificationLogRepository notificationLogRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		UserRepository userRepository,
		CampusMemberRepository campusMemberRepository,
		WeeklyDevotionRecordRepository weeklyDevotionRecordRepository,
		PollRepository pollRepository,
		PollResponseRepository pollResponseRepository,
		ChargeItemRepository chargeItemRepository,
		NotificationDispatchPort notificationDispatchPort,
		NotificationLockService notificationLockService
	) {
		this.notificationLogRepository = notificationLogRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.userRepository = userRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.weeklyDevotionRecordRepository = weeklyDevotionRecordRepository;
		this.pollRepository = pollRepository;
		this.pollResponseRepository = pollResponseRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.notificationDispatchPort = notificationDispatchPort;
		this.notificationLockService = notificationLockService;
	}

	@Transactional
	public SendNotificationResult requestNotification(SendNotificationCommand command) {
		requireNotificationManager(command.campusId(), command.requesterId(), ErrorCode.NOTIFICATION_SEND_FORBIDDEN);
		NotificationLockLease lease = notificationLockService.acquireManualLock(
			NotificationLockKey.manualAdminNotification(command.campusId(), command.requesterId())
		);
		try {
			return requestNotificationWithLock(command);
		} finally {
			notificationLockService.release(lease);
		}
	}

	private SendNotificationResult requestNotificationWithLock(SendNotificationCommand command) {
		List<Long> targetUserIds = resolveTargets(command);
		UUID requestId = UUID.randomUUID();
		int queuedCount = 0;
		int skippedCount = 0;

		for (Long targetUserId : targetUserIds) {
			if (userFcmTokenRepository.findActiveSendableTokens(targetUserId).isEmpty()) {
				notificationLogRepository.save(NotificationLog.skipped(
					requestId,
					targetUserId,
					command.campusId(),
					command.notificationType(),
					command.targetWeekStartDate(),
					command.targetId(),
					command.title(),
					command.body(),
					"NO_ACTIVE_FCM_TOKEN"
				));
				skippedCount++;
				continue;
			}
			notificationLogRepository.save(NotificationLog.pending(
				requestId,
				targetUserId,
				command.campusId(),
				command.notificationType(),
				command.targetWeekStartDate(),
				command.targetId(),
				command.title(),
				command.body()
			));
			queuedCount++;
		}

		if (queuedCount > 0) {
			notificationDispatchPort.dispatch(requestId);
		}
		return new SendNotificationResult(requestId, queuedCount, skippedCount);
	}

	private List<Long> resolveTargets(SendNotificationCommand command) {
		List<CampusMember> activeMembers = campusMemberRepository
			.findByCampusIdAndStatusOrderByIdAsc(command.campusId(), CampusMemberStatus.ACTIVE);
		Map<Long, CampusMember> activeMembersByUserId = activeMembers.stream()
			.collect(Collectors.toMap(CampusMember::userId, Function.identity(), (left, right) -> left));

		if (command.targetUserIds() != null && !command.targetUserIds().isEmpty()) {
			return command.targetUserIds().stream()
				.distinct()
				.filter(activeMembersByUserId::containsKey)
				.toList();
		}

		return switch (command.notificationType()) {
			case CUSTOM -> throw new BusinessException(ErrorCode.NOTIFICATION_TARGET_REQUIRED);
			case DEVOTION_MISSING -> devotionMissingTargets(command, activeMembers);
			case WED_POLL_MISSING, SATURDAY_POLL_MISSING, COFFEE_POLL_MISSING -> pollMissingTargets(command, activeMembers);
			case PAYMENT_UNPAID -> paymentUnpaidTargets(command, activeMembersByUserId.keySet());
			default -> throw new BusinessException(ErrorCode.NOTIFICATION_TARGET_REQUIRED);
		};
	}

	private List<Long> devotionMissingTargets(SendNotificationCommand command, List<CampusMember> activeMembers) {
		if (command.targetWeekStartDate() == null) {
			throw new BusinessException(ErrorCode.NOTIFICATION_TARGET_FIELD_REQUIRED);
		}
		Map<Long, WeeklyDevotionRecord> recordsByUserId = weeklyDevotionRecordRepository
			.findByCampusIdAndWeekStartDate(command.campusId(), command.targetWeekStartDate())
			.stream()
			.collect(Collectors.toMap(WeeklyDevotionRecord::userId, Function.identity()));
		return activeMembers.stream()
			.filter(member -> {
				WeeklyDevotionRecord record = recordsByUserId.get(member.userId());
				return record == null || record.submittedAt() == null;
			})
			.map(CampusMember::userId)
			.toList();
	}

	private List<Long> pollMissingTargets(SendNotificationCommand command, List<CampusMember> activeMembers) {
		if (command.targetId() == null) {
			throw new BusinessException(ErrorCode.NOTIFICATION_TARGET_FIELD_REQUIRED);
		}
		Poll poll = pollRepository.findByIdAndCampusId(command.targetId(), command.campusId())
			.orElseThrow(() -> new BusinessException(ErrorCode.POLL_NOT_FOUND));
		Set<Long> respondedUserIds = pollResponseRepository.findByPollIdOrderByIdAsc(poll.id())
			.stream()
			.map(response -> response.userId())
			.collect(Collectors.toSet());
		return activeMembers.stream()
			.filter(member -> !respondedUserIds.contains(member.userId()))
			.map(CampusMember::userId)
			.toList();
	}

	private List<Long> paymentUnpaidTargets(SendNotificationCommand command, Set<Long> activeUserIds) {
		LinkedHashSet<Long> unpaidUserIds = chargeItemRepository.findByCampusIdAndStatus(command.campusId(), ChargeStatus.UNPAID)
			.stream()
			.map(ChargeItem::userId)
			.filter(activeUserIds::contains)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return List.copyOf(unpaidUserIds);
	}

	private void requireNotificationManager(Long campusId, Long requesterId, ErrorCode errorCode) {
		User requester = userRepository.findById(requesterId)
			.filter(User::isActive)
			.orElseThrow(() -> new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
		if (requester.isAdmin()) {
			return;
		}
		CampusMember requesterMembership = campusMemberRepository.findByCampusIdAndUserId(campusId, requester.id())
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		CampusRolePolicy.requireCampusManager(requesterMembership, errorCode);
	}
}
