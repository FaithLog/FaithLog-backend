package com.faithlog.notification.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.service.port.ChargeItemRepositoryPort;
import com.faithlog.billing.service.port.PaymentAccountRepositoryPort;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.DutyType;
import com.faithlog.campus.service.port.CampusDutyAssignmentRepositoryPort;
import com.faithlog.campus.service.port.CampusMemberRepositoryPort;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.notification.service.command.NotificationDeduplicationCommand;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import com.faithlog.notification.service.result.SendNotificationResult;
import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ChargeReminderService {

	private static final ZoneId SEOUL_ZONE = ZoneId.of("Asia/Seoul");

	private final CampusMemberRepositoryPort campusMemberRepository;
	private final CampusDutyAssignmentRepositoryPort dutyAssignmentRepository;
	private final PaymentAccountRepositoryPort paymentAccountRepository;
	private final ChargeItemRepositoryPort chargeItemRepository;
	private final UserFcmTokenRepository userFcmTokenRepository;
	private final NotificationLogRepository notificationLogRepository;
	private final NotificationDispatchPort notificationDispatchPort;
	private final NotificationDeduplicationService notificationDeduplicationService;
	private final NotificationLockService notificationLockService;
	private final Clock clock;

	public ChargeReminderService(
		CampusMemberRepositoryPort campusMemberRepository,
		CampusDutyAssignmentRepositoryPort dutyAssignmentRepository,
		PaymentAccountRepositoryPort paymentAccountRepository,
		ChargeItemRepositoryPort chargeItemRepository,
		UserFcmTokenRepository userFcmTokenRepository,
		NotificationLogRepository notificationLogRepository,
		NotificationDispatchPort notificationDispatchPort,
		NotificationDeduplicationService notificationDeduplicationService,
		NotificationLockService notificationLockService,
		Clock clock
	) {
		this.campusMemberRepository = campusMemberRepository;
		this.dutyAssignmentRepository = dutyAssignmentRepository;
		this.paymentAccountRepository = paymentAccountRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.userFcmTokenRepository = userFcmTokenRepository;
		this.notificationLogRepository = notificationLogRepository;
		this.notificationDispatchPort = notificationDispatchPort;
		this.notificationDeduplicationService = notificationDeduplicationService;
		this.notificationLockService = notificationLockService;
		this.clock = clock;
	}

	@Transactional
	public SendNotificationResult requestCoffeeReminders(Long campusId, Long requesterId) {
		return requestReminders(campusId, requesterId, PaymentCategory.COFFEE, DutyType.COFFEE);
	}

	@Transactional
	public SendNotificationResult requestMealReminders(Long campusId, Long requesterId) {
		return requestReminders(campusId, requesterId, PaymentCategory.MEAL, DutyType.MEAL);
	}

	private SendNotificationResult requestReminders(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory,
		DutyType dutyType
	) {
		requireActiveDuty(campusId, requesterId, dutyType);
		NotificationLockLease lease = notificationLockService.acquireManualLock(
			NotificationLockKey.chargeReminder(campusId, requesterId, paymentCategory.name())
		);
		try {
			return requestWithLock(campusId, requesterId, paymentCategory);
		} finally {
			notificationLockService.release(lease);
		}
	}

	private SendNotificationResult requestWithLock(
		Long campusId,
		Long requesterId,
		PaymentCategory paymentCategory
	) {
		List<PaymentAccount> accounts = paymentAccountRepository
			.findByCampusIdAndOwnerUserIdAndAccountTypeAndDeletedAtIsNullOrderByIdAsc(
				campusId, requesterId, paymentCategory);
		Map<Long, PaymentAccount> accountsById = accounts.stream()
			.collect(Collectors.toMap(PaymentAccount::id, account -> account));
		Set<Long> accountIds = accountsById.keySet();
		Map<Long, Map<Long, List<ChargeItem>>> chargesByAccountAndUser = chargeItemRepository
			.findByCampusIdAndPaymentCategoryAndStatus(campusId, paymentCategory, ChargeStatus.UNPAID)
			.stream()
			.filter(charge -> accountIds.contains(charge.paymentAccountId()))
			.collect(Collectors.groupingBy(
				ChargeItem::paymentAccountId,
				LinkedHashMap::new,
				Collectors.groupingBy(ChargeItem::userId, LinkedHashMap::new, Collectors.toList())
			));

		UUID requestId = UUID.randomUUID();
		LocalDate businessDate = LocalDate.ofInstant(clock.instant(), SEOUL_ZONE);
		int queuedCount = 0;
		int skippedCount = 0;
		List<NotificationDeduplicationCommand> reservations = new ArrayList<>();
		try {
			for (PaymentAccount account : accounts) {
				Map<Long, List<ChargeItem>> chargesByUser = chargesByAccountAndUser.getOrDefault(account.id(), Map.of());
				for (Map.Entry<Long, List<ChargeItem>> entry : chargesByUser.entrySet()) {
					Long targetUserId = entry.getKey();
					NotificationDeduplicationCommand deduplicationCommand = new NotificationDeduplicationCommand(
						NotificationType.PAYMENT_UNPAID,
						campusId,
						"charge-reminder:" + paymentCategory.name() + ":account:" + account.id(),
						targetUserId,
						businessDate
					);
					boolean reserved = notificationDeduplicationService
						.reserveDailyRequiredNotification(deduplicationCommand);
					if (!reserved) {
						skippedCount++;
						continue;
					}
					reservations.add(deduplicationCommand);
					String title = title(paymentCategory);
					String body = body(paymentCategory, entry.getValue());
					if (userFcmTokenRepository.findActiveSendableTokens(targetUserId).isEmpty()) {
						notificationLogRepository.save(NotificationLog.skipped(
							requestId, targetUserId, campusId, NotificationType.PAYMENT_UNPAID,
							null, account.id(), title, body, "NO_ACTIVE_FCM_TOKEN"
						));
						skippedCount++;
						continue;
					}
					notificationLogRepository.save(NotificationLog.pending(
						requestId, targetUserId, campusId, NotificationType.PAYMENT_UNPAID,
						null, account.id(), title, body
					));
					queuedCount++;
				}
			}
			if (queuedCount > 0) {
				notificationDispatchPort.dispatch(requestId);
			}
			return new SendNotificationResult(requestId, queuedCount, skippedCount);
		} catch (RuntimeException exception) {
			reservations.forEach(notificationDeduplicationService::releaseRequiredNotification);
			throw exception;
		}
	}

	private void requireActiveDuty(Long campusId, Long requesterId, DutyType dutyType) {
		ErrorCode errorCode = dutyType == DutyType.COFFEE
			? ErrorCode.NOTIFICATION_COFFEE_CHARGE_REMINDER_FORBIDDEN
			: ErrorCode.NOTIFICATION_MEAL_CHARGE_REMINDER_FORBIDDEN;
		campusMemberRepository.findByCampusIdAndUserId(campusId, requesterId)
			.filter(CampusMember::isActive)
			.orElseThrow(() -> new BusinessException(errorCode));
		if (dutyAssignmentRepository.findByCampusIdAndDutyTypeAndUserIdAndIsActiveTrue(
			campusId, dutyType, requesterId).isEmpty()) {
			throw new BusinessException(errorCode);
		}
	}

	private String title(PaymentCategory paymentCategory) {
		return paymentCategory == PaymentCategory.COFFEE ? "커피 미납 청구 안내" : "밥 미납 청구 안내";
	}

	private String body(PaymentCategory paymentCategory, List<ChargeItem> charges) {
		long totalAmount = charges.stream().mapToLong(ChargeItem::amount).sum();
		String category = paymentCategory == PaymentCategory.COFFEE ? "커피" : "밥";
		return category + " 미납 금액은 총 " + totalAmount + "원입니다. 확인 후 납부해 주세요.";
	}
}
