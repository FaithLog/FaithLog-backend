package com.faithlog.notification.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.campus.domain.Campus;
import com.faithlog.campus.domain.CampusMember;
import com.faithlog.campus.infrastructure.jpa.CampusMemberRepository;
import com.faithlog.campus.infrastructure.jpa.CampusRepository;
import com.faithlog.notification.application.port.FcmSendCommand;
import com.faithlog.notification.application.port.FcmSendFailureType;
import com.faithlog.notification.application.port.FcmSendPort;
import com.faithlog.notification.application.port.NotificationDispatchPort;
import com.faithlog.notification.domain.DeviceType;
import com.faithlog.notification.domain.NotificationLog;
import com.faithlog.notification.domain.NotificationType;
import com.faithlog.notification.domain.SendStatus;
import com.faithlog.notification.infrastructure.jpa.NotificationLogRepository;
import com.faithlog.notification.infrastructure.jpa.UserFcmTokenRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@SpringBootTest
@ActiveProfiles("test")
@Transactional
class NotificationDeliveryWorkerTest {

	@Autowired
	private NotificationService notificationService;

	@Autowired
	private NotificationDeliveryWorker worker;

	@Autowired
	private FcmTokenService fcmTokenService;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private FakeFcmSendPort fakeFcmSendPort;

	@Autowired
	private RecordingRetryBackoff recordingRetryBackoff;

	@Autowired
	private NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@BeforeEach
	void resetFake() {
		fakeFcmSendPort.reset();
		recordingRetryBackoff.reset();
		notificationConcurrencyPort.reset();
	}

	@Test
	void worker_retries_transient_failure_marks_success_and_isolates_permanent_failure() {
		Campus campus = saveCampus("알림캠C");
		User minister = saveUser("notification-worker-minister@example.com", UserRole.USER);
		User transientUser = saveUser("notification-worker-transient@example.com", UserRole.USER);
		User permanentUser = saveUser("notification-worker-permanent@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), transientUser.id());
		saveMember(campus.id(), permanentUser.id());
		registerToken(transientUser, "transient-token", "transient-client");
		registerToken(permanentUser, "permanent-token", "permanent-client");
		fakeFcmSendPort.failTransientThenSucceed("transient-token", 2);
		fakeFcmSendPort.failPermanent("permanent-token");
		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			List.of(transientUser.id(), permanentUser.id()),
			null,
			null,
			"공지",
			"알림 본문"
		));

		worker.processRequest(result.notificationRequestId());

		List<NotificationLog> logs = notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId());
		assertThat(logs).extracting(NotificationLog::sendStatus).containsExactly(SendStatus.SENT, SendStatus.FAILED);
		assertThat(fakeFcmSendPort.attempts("transient-token")).isEqualTo(3);
		assertThat(fakeFcmSendPort.attempts("permanent-token")).isEqualTo(1);
		assertThat(userFcmTokenRepository.findActiveSendableTokens(permanentUser.id())).isEmpty();
	}

	@Test
	void worker_marks_failed_when_transient_failure_exhausts_retries() {
		Campus campus = saveCampus("알림캠E");
		User minister = saveUser("notification-worker-fail-minister@example.com", UserRole.USER);
		User target = saveUser("notification-worker-fail-target@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		registerToken(target, "always-transient-token", "always-transient-client");
		fakeFcmSendPort.failTransientThenSucceed("always-transient-token", 10);
		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			List.of(target.id()),
			null,
			null,
			"공지",
			"알림 본문"
		));

		worker.processRequest(result.notificationRequestId());

		List<NotificationLog> logs = notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId());
		assertThat(logs).extracting(NotificationLog::sendStatus).containsExactly(SendStatus.FAILED);
		assertThat(fakeFcmSendPort.attempts("always-transient-token")).isEqualTo(4);
		assertThat(userFcmTokenRepository.findActiveSendableTokens(target.id())).hasSize(1);
	}

	@Test
	@Transactional(propagation = Propagation.NOT_SUPPORTED)
	void worker_does_not_hold_database_transaction_during_fcm_send_or_retry_sleep() {
		Campus campus = saveCampus("알림캠F");
		User minister = saveUser("notification-worker-tx-minister@example.com", UserRole.USER);
		User target = saveUser("notification-worker-tx-target@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		registerToken(target, "tx-boundary-token", "tx-boundary-client");
		fakeFcmSendPort.failTransientThenSucceed("tx-boundary-token", 1);
		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			List.of(target.id()),
			null,
			null,
			"공지",
			"알림 본문"
		));

		worker.processRequest(result.notificationRequestId());

		assertThat(fakeFcmSendPort.transactionActiveDuringSend()).containsOnly(false);
		assertThat(recordingRetryBackoff.transactionActiveDuringSleep()).containsOnly(false);
	}

	@Test
	void worker_does_not_dispatch_when_same_request_lock_is_already_held() {
		Campus campus = saveCampus("알림캠H");
		User minister = saveUser("notification-worker-lock-minister@example.com", UserRole.USER);
		User target = saveUser("notification-worker-lock-target@example.com", UserRole.USER);
		saveMinister(campus.id(), minister.id());
		saveMember(campus.id(), target.id());
		registerToken(target, "lock-held-token", "lock-held-client");
		SendNotificationResult result = notificationService.requestNotification(new SendNotificationCommand(
			campus.id(),
			minister.id(),
			NotificationType.CUSTOM,
			List.of(target.id()),
			null,
			null,
			"공지",
			"알림 본문"
		));
		notificationConcurrencyPort.acquire(
			NotificationLockKey.dispatch(campus.id(), result.notificationRequestId()),
			Duration.ofMinutes(10)
		);

		worker.processRequest(result.notificationRequestId());

		List<NotificationLog> logs = notificationLogRepository.findByRequestIdOrderByIdAsc(result.notificationRequestId());
		assertThat(logs).extracting(NotificationLog::sendStatus).containsExactly(SendStatus.PENDING);
		assertThat(fakeFcmSendPort.attempts("lock-held-token")).isZero();
	}

	@TestConfiguration
	static class FakeFcmConfig {

		@Bean
		@Primary
		FakeFcmSendPort fakeFcmSendPort() {
			return new FakeFcmSendPort();
		}

		@Bean
		@Primary
		NotificationRetryBackoff notificationRetryBackoff() {
			return new RecordingRetryBackoff();
		}

		@Bean
		@Primary
		NotificationDispatchPort notificationDispatchPort() {
			return requestId -> {
			};
		}
	}

	static class FakeFcmSendPort implements FcmSendPort {

		private final Map<String, Integer> transientFailuresBeforeSuccess = new HashMap<>();
		private final List<String> permanentFailures = new ArrayList<>();
		private final Map<String, Integer> attempts = new HashMap<>();
		private final List<Boolean> transactionActiveDuringSend = new ArrayList<>();

		@Override
		public void send(FcmSendCommand command) {
			transactionActiveDuringSend.add(TransactionSynchronizationManager.isActualTransactionActive());
			attempts.merge(command.token(), 1, Integer::sum);
			if (permanentFailures.contains(command.token())) {
				throw new FcmSendException(FcmSendFailureType.PERMANENT, "UNREGISTERED");
			}
			int remainingTransientFailures = transientFailuresBeforeSuccess.getOrDefault(command.token(), 0);
			if (remainingTransientFailures > 0) {
				transientFailuresBeforeSuccess.put(command.token(), remainingTransientFailures - 1);
				throw new FcmSendException(FcmSendFailureType.TRANSIENT, "timeout");
			}
		}

		void failTransientThenSucceed(String token, int failures) {
			transientFailuresBeforeSuccess.put(token, failures);
		}

		void failPermanent(String token) {
			permanentFailures.add(token);
		}

		int attempts(String token) {
			return attempts.getOrDefault(token, 0);
		}

		List<Boolean> transactionActiveDuringSend() {
			return transactionActiveDuringSend;
		}

		void reset() {
			transientFailuresBeforeSuccess.clear();
			permanentFailures.clear();
			attempts.clear();
			transactionActiveDuringSend.clear();
		}
	}

	static class RecordingRetryBackoff implements NotificationRetryBackoff {

		private final List<Boolean> transactionActiveDuringSleep = new ArrayList<>();

		@Override
		public void sleepBeforeRetry(int retryNumber) {
			transactionActiveDuringSleep.add(TransactionSynchronizationManager.isActualTransactionActive());
		}

		List<Boolean> transactionActiveDuringSleep() {
			return transactionActiveDuringSleep;
		}

		void reset() {
			transactionActiveDuringSleep.clear();
		}
	}

	private void registerToken(User user, String token, String clientInstanceId) {
		fcmTokenService.registerToken(new RegisterFcmTokenCommand(
			user.id(),
			token,
			clientInstanceId,
			DeviceType.IOS,
			"1.0.0"
		));
	}

	private Campus saveCampus(String name) {
		return campusRepository.saveAndFlush(Campus.create(name, "분당", name, "INV-" + name));
	}

	private CampusMember saveMinister(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMinister(campusId, userId));
	}

	private CampusMember saveMember(Long campusId, Long userId) {
		return campusMemberRepository.saveAndFlush(CampusMember.createMember(campusId, userId));
	}

	private User saveUser(String email, UserRole role) {
		User user = userRepository.save(User.create(email.substring(0, email.indexOf('@')), email, "{noop}password"));
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}
}
