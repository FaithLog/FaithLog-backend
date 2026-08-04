package com.faithlog.batch.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doThrow;

import com.faithlog.batch.service.port.YearlyRecapArchivePort;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.poll.domain.entity.Poll;
import com.faithlog.poll.domain.type.ChargeGenerationType;
import com.faithlog.poll.domain.type.PollType;
import com.faithlog.poll.domain.type.SelectionType;
import com.faithlog.poll.infrastructure.repository.PollRepository;
import com.faithlog.support.NotificationConcurrencyTestConfig.InMemoryNotificationConcurrencyPort;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.infrastructure.repository.UserRepository;
import jakarta.persistence.EntityManager;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.UUID;
import java.util.function.Supplier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class DataRetentionArchiveRollbackIntegrationTest {

	private static final Instant NOW = ZonedDateTime.of(
		2027, 2, 2, 4, 30, 0, 0, ZoneId.of("Asia/Seoul")).toInstant();

	@Autowired
	private DataRetentionCleanupService cleanupService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private CampusRepository campusRepository;

	@Autowired
	private PollRepository pollRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private PlatformTransactionManager transactionManager;

	@Autowired
	private InMemoryNotificationConcurrencyPort notificationConcurrencyPort;

	@MockitoBean
	private YearlyRecapArchivePort archivePort;

	@AfterEach
	void resetLockPort() {
		notificationConcurrencyPort.reset();
	}

	@Test
	void archive_failure_rolls_back_prior_log_delete_and_poll_cleanup_in_a_fresh_transaction() {
		RollbackGraph graph = inNewTransaction(() -> {
			User user = userRepository.saveAndFlush(
				User.create("archive rollback", "archive-rollback@example.com", "{noop}password"));
			Campus campus = campusRepository.saveAndFlush(
				Campus.create("archive rollback", "서울", "테스트", "ARC-RB-238"));
			Poll poll = Poll.create(
				campus.id(),
				null,
				"archive rollback poll",
				PollType.CUSTOM,
				SelectionType.SINGLE,
				false,
				false,
				ChargeGenerationType.NONE,
				null,
				null,
				NOW.minusSeconds(32L * 24 * 60 * 60),
				NOW.minusSeconds(31L * 24 * 60 * 60),
				null
			);
			poll.open();
			poll = pollRepository.saveAndFlush(poll);
			NotificationLog log = notificationLogRepository.saveAndFlush(NotificationLog.skipped(
				UUID.randomUUID(),
				user.id(),
				campus.id(),
				NotificationType.CUSTOM,
				null,
				null,
				"archive rollback",
				"archive rollback",
				"TEST"
			));
			jdbcTemplate.update(
				"update notification_logs set created_at = ? where id = ?",
				NOW.minusSeconds(15L * 24 * 60 * 60),
				log.id()
			);
			return new RollbackGraph(user.id(), campus.id(), poll.id(), log.id());
		});
		doThrow(new IllegalStateException("archive unavailable"))
			.when(archivePort).archiveExpiredPolls(anyList());

		try {
			assertThatThrownBy(() -> cleanupService.cleanupDaily(NOW))
				.isInstanceOf(IllegalStateException.class)
				.hasMessage("archive unavailable");

			inNewTransaction(() -> {
				entityManager.clear();
				assertThat(notificationLogRepository.findById(graph.notificationLogId())).isPresent();
				assertThat(pollRepository.findById(graph.pollId())).isPresent();
				return null;
			});
		} finally {
			inNewTransaction(() -> {
				jdbcTemplate.update("delete from notification_logs where id = ?", graph.notificationLogId());
				jdbcTemplate.update("delete from polls where id = ?", graph.pollId());
				jdbcTemplate.update("delete from campuses where id = ?", graph.campusId());
				jdbcTemplate.update("delete from users where id = ?", graph.userId());
				return null;
			});
		}
	}

	private <T> T inNewTransaction(Supplier<T> supplier) {
		TransactionTemplate template = new TransactionTemplate(transactionManager);
		template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return template.execute(status -> supplier.get());
	}

	private record RollbackGraph(Long userId, Long campusId, Long pollId, Long notificationLogId) {
	}
}
