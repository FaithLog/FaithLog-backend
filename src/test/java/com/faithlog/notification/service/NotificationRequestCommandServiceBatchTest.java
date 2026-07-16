package com.faithlog.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.devotion.infrastructure.repository.WeeklyDevotionRecordRepository;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.domain.type.DeviceType;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.domain.type.SendStatus;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.notification.service.command.NotificationDeduplicationCommand;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.poll.infrastructure.repository.PollResponseRepository;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationRequestCommandServiceBatchTest {

	@Mock NotificationLogRepository notificationLogRepository;
	@Mock UserFcmTokenRepository userFcmTokenRepository;
	@Mock UserRepository userRepository;
	@Mock CampusMemberRepository campusMemberRepository;
	@Mock WeeklyDevotionRecordRepository weeklyDevotionRecordRepository;
	@Mock PollRepository pollRepository;
	@Mock PollResponseRepository pollResponseRepository;
	@Mock ChargeItemRepository chargeItemRepository;
	@Mock NotificationDispatchPort notificationDispatchPort;
	@Mock NotificationDeduplicationService notificationDeduplicationService;
	@Mock NotificationLockService notificationLockService;

	@Test
	void automatic_request_bulk_loads_tokens_after_dedupe_and_preserves_log_semantics() {
		NotificationRequestCommandService service = new NotificationRequestCommandService(
			notificationLogRepository, userFcmTokenRepository, userRepository, campusMemberRepository,
			weeklyDevotionRecordRepository, pollRepository, pollResponseRepository, chargeItemRepository,
			notificationDispatchPort, notificationDeduplicationService, notificationLockService
		);
		when(notificationDeduplicationService.reserveDailyAutomaticNotification(any(
			NotificationDeduplicationCommand.class))).thenReturn(true, false, true);
		when(userFcmTokenRepository.findActiveSendableTokensByUserIdIn(Set.of(11L, 13L)))
			.thenReturn(List.of(UserFcmToken.create(11L, "token-11", "client-11", DeviceType.IOS, "1.0")));
		when(notificationLogRepository.save(any(NotificationLog.class)))
			.thenAnswer(invocation -> invocation.getArgument(0));

		int created = service.requestAutomaticNotification(new AutomaticNotificationRequestCommand(
			7L, NotificationType.PAYMENT_UNPAID, null, 99L, List.of(11L, 12L, 13L),
			LocalDate.of(2026, 7, 17), "scope-99", "미납", "확인"
		));

		assertThat(created).isEqualTo(2);
		verify(userFcmTokenRepository).findActiveSendableTokensByUserIdIn(Set.of(11L, 13L));
		verify(userFcmTokenRepository, never()).findActiveSendableTokens(any(Long.class));
		ArgumentCaptor<NotificationLog> logs = ArgumentCaptor.forClass(NotificationLog.class);
		verify(notificationLogRepository, org.mockito.Mockito.times(2)).save(logs.capture());
		assertThat(logs.getAllValues()).extracting(NotificationLog::userId).containsExactly(11L, 13L);
		assertThat(logs.getAllValues()).extracting(NotificationLog::campusId).containsOnly(7L);
		assertThat(logs.getAllValues()).extracting(NotificationLog::sendStatus)
			.containsExactly(SendStatus.PENDING, SendStatus.SKIPPED);
		assertThat(logs.getAllValues().get(1).failureReason()).isEqualTo("NO_ACTIVE_FCM_TOKEN");
		verify(notificationDispatchPort).dispatch(logs.getAllValues().get(0).requestId());
	}
}
