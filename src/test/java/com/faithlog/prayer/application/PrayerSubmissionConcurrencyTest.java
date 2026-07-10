package com.faithlog.prayer.application;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.campus.service.result.CampusCreateResult;
import com.faithlog.campus.service.CampusService;
import com.faithlog.campus.service.command.CreateCampusCommand;
import com.faithlog.campus.service.command.JoinCampusCommand;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.prayer.domain.PrayerSubmission;
import com.faithlog.prayer.infrastructure.jpa.PrayerSubmissionRepository;
import com.faithlog.prayer.infrastructure.jpa.PrayerWeekRepository;
import com.faithlog.user.domain.User;
import com.faithlog.user.domain.UserRole;
import com.faithlog.user.infrastructure.jpa.UserRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class PrayerSubmissionConcurrencyTest {

	@Autowired
	private PrayerService prayerService;

	@Autowired
	private CampusService campusService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PrayerSubmissionRepository prayerSubmissionRepository;

	@Autowired
	private PrayerWeekRepository prayerWeekRepository;

	@Autowired
	private PlatformTransactionManager transactionManager;

	private final List<Long> cleanupPrayerWeekIds = new ArrayList<>();

	@AfterEach
	void cleanPrayerRowsCreatedByConcurrencyTest() {
		for (Long prayerWeekId : cleanupPrayerWeekIds) {
			prayerSubmissionRepository.findByPrayerWeekId(prayerWeekId)
				.forEach(prayerSubmissionRepository::delete);
			prayerWeekRepository.findById(prayerWeekId)
				.ifPresent(prayerWeekRepository::delete);
		}
		cleanupPrayerWeekIds.clear();
	}

	@Test
	void concurrent_transactions_updating_same_version_allow_only_one_conditional_update() throws Exception {
		PrayerFixture fixture = createFixture("concurrent-update");
		LocalDate weekStart = LocalDate.of(2026, 6, 22);
		prayerService.saveSubmissions(new SavePrayerSubmissionsCommand(
			fixture.campusId(),
			weekStart,
			fixture.manager().id(),
			List.of(new PrayerSubmissionCommand(fixture.memberA().id(), "처음 저장", 0))
		));
		Long prayerWeekId = prayerWeekRepository
			.findByCampusIdAndSeasonIdAndWeekStartDate(fixture.campusId(), fixture.seasonId(), weekStart)
			.orElseThrow()
			.id();
		cleanupPrayerWeekIds.add(prayerWeekId);
		Long submissionId = prayerSubmissionRepository.findByPrayerWeekId(prayerWeekId).getFirst().id();

		CountDownLatch bothTransactionsReadVersion = new CountDownLatch(2);
		CountDownLatch continueUpdates = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2, runnable -> {
			Thread thread = new Thread(runnable);
			thread.setName("prayer-concurrency-" + thread.threadId());
			return thread;
		});
		List<Future<SaveResult>> futures = List.of(
			executor.submit(() -> updateWithResult(
				submissionId,
				fixture.manager().id(),
				"동시 수정 A",
				bothTransactionsReadVersion,
				continueUpdates
			)),
			executor.submit(() -> updateWithResult(
				submissionId,
				fixture.manager().id(),
				"동시 수정 B",
				bothTransactionsReadVersion,
				continueUpdates
			))
		);

		assertThat(bothTransactionsReadVersion.await(5, TimeUnit.SECONDS)).isTrue();
		continueUpdates.countDown();
		List<SaveResult> results = futures.stream()
			.map(future -> {
				try {
					return future.get(5, TimeUnit.SECONDS);
				} catch (Exception exception) {
					throw new IllegalStateException(exception);
				}
			})
			.toList();
		executor.shutdown();
		assertThat(executor.awaitTermination(5, TimeUnit.SECONDS)).isTrue();

		assertThat(results).filteredOn(result -> result.errorCode() == null).hasSize(1);
		assertThat(results).filteredOn(result -> result.errorCode() == ErrorCode.PRAYER_SUBMISSION_CONFLICT).hasSize(1);
		PrayerSubmission saved = prayerSubmissionRepository.findById(submissionId).orElseThrow();
		assertThat(saved.version()).isEqualTo(2);
		assertThat(saved.content()).isIn("동시 수정 A", "동시 수정 B");
	}

	private SaveResult updateWithResult(
		Long submissionId,
		Long requesterId,
		String content,
		CountDownLatch bothTransactionsReadVersion,
		CountDownLatch continueUpdates
	) {
		TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
		return transactionTemplate.execute(status -> {
			PrayerSubmission submission = prayerSubmissionRepository.findById(submissionId).orElseThrow();
			assertThat(submission.version()).isEqualTo(1);
			bothTransactionsReadVersion.countDown();
			try {
				continueUpdates.await(5, TimeUnit.SECONDS);
			} catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
				throw new IllegalStateException(exception);
			}
			int updatedRows = prayerSubmissionRepository.updateContentIfVersionMatches(
				submission.id(),
				content,
				requesterId,
				Instant.now(),
				submission.version()
			);
			if (updatedRows == 0) {
				status.setRollbackOnly();
				return new SaveResult(ErrorCode.PRAYER_SUBMISSION_CONFLICT);
			}
			return new SaveResult(null);
		});
	}

	private PrayerFixture createFixture(String suffix) {
		User manager = saveUser("prayer-" + suffix + "-manager@example.com", UserRole.MANAGER);
		User memberA = saveUser("prayer-" + suffix + "-a@example.com", UserRole.USER);
		User memberB = saveUser("prayer-" + suffix + "-b@example.com", UserRole.USER);
		CampusCreateResult campus = campusService.createCampus(new CreateCampusCommand(
			manager.id(),
			"기도 " + suffix,
			"분당",
			"기도 동시성 테스트 캠퍼스"
		));
		campusService.joinCampus(new JoinCampusCommand(memberA.id(), campus.inviteCode()));
		campusService.joinCampus(new JoinCampusCommand(memberB.id(), campus.inviteCode()));
		PrayerSeasonResult season = prayerService.createSeason(new CreatePrayerSeasonCommand(
			campus.campusId(),
			manager.id(),
			"2026 여름",
			LocalDate.of(2026, 6, 1)
		));
		PrayerGroupResult group = prayerService.createGroup(new CreatePrayerGroupCommand(
			season.seasonId(),
			manager.id(),
			"1조",
			1
		));
		prayerService.replaceGroupMembers(new ReplacePrayerGroupMembersCommand(
			group.groupId(),
			manager.id(),
			List.of(memberA.id(), memberB.id())
		));
		return new PrayerFixture(campus.campusId(), season.seasonId(), manager, memberA);
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create(email.substring(0, email.indexOf('@')), email, "{noop}password");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private record SaveResult(ErrorCode errorCode) {
	}

	private record PrayerFixture(
		Long campusId,
		Long seasonId,
		User manager,
		User memberA
	) {
	}
}
