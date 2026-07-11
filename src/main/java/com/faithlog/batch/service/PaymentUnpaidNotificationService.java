package com.faithlog.batch.service;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.type.ChargeStatus;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.service.NotificationLockKey;
import com.faithlog.notification.service.NotificationLockLease;
import com.faithlog.notification.service.NotificationLockService;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;

@Service
public class PaymentUnpaidNotificationService {

	private final CampusRepository campusRepository;
	private final CampusMemberRepository campusMemberRepository;
	private final ChargeItemRepository chargeItemRepository;
	private final NotificationRequestCommandService notificationRequestCommandService;
	private final NotificationLockService notificationLockService;

	public PaymentUnpaidNotificationService(
		CampusRepository campusRepository,
		CampusMemberRepository campusMemberRepository,
		ChargeItemRepository chargeItemRepository,
		NotificationRequestCommandService notificationRequestCommandService,
		NotificationLockService notificationLockService
	) {
		this.campusRepository = campusRepository;
		this.campusMemberRepository = campusMemberRepository;
		this.chargeItemRepository = chargeItemRepository;
		this.notificationRequestCommandService = notificationRequestCommandService;
		this.notificationLockService = notificationLockService;
	}

	public int sendPaymentUnpaidReminders(Instant now) {
		LocalDate businessDate = LocalDate.ofInstant(now, BatchTimeZone.SEOUL_ZONE);
		String scopeId = "payment:unpaid";
		return campusRepository.findByIsActiveTrueOrderByIdAsc()
			.stream()
			.mapToInt(campus -> runWithCampusLock(campus.id(), businessDate, scopeId))
			.sum();
	}

	private int runWithCampusLock(Long campusId, LocalDate businessDate, String scopeId) {
		Optional<NotificationLockLease> lease = notificationLockService.acquireScheduledLock(
			new NotificationLockKey("payment-unpaid", campusId, businessDate.toString())
		);
		if (lease.isEmpty()) {
			return 0;
		}
		try {
			return notificationRequestCommandService.requestAutomaticNotification(
				new AutomaticNotificationRequestCommand(
					campusId,
					NotificationType.PAYMENT_UNPAID,
					null,
					null,
					paymentUnpaidTargets(campusId),
					businessDate,
					scopeId,
					"미납 알림",
					"아직 납부하지 않은 청구가 있어요."
				)
			);
		} finally {
			notificationLockService.release(lease.get());
		}
	}

	private List<Long> paymentUnpaidTargets(Long campusId) {
		Set<Long> activeUserIds = campusMemberRepository
			.findByCampusIdAndStatusOrderByIdAsc(campusId, CampusMemberStatus.ACTIVE)
			.stream()
			.map(member -> member.userId())
			.collect(Collectors.toSet());
		LinkedHashSet<Long> unpaidUserIds = chargeItemRepository.findByCampusIdAndStatus(campusId, ChargeStatus.UNPAID)
			.stream()
			.map(ChargeItem::userId)
			.filter(activeUserIds::contains)
			.collect(Collectors.toCollection(LinkedHashSet::new));
		return List.copyOf(unpaidUserIds);
	}
}
