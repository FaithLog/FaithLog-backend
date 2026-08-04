package com.faithlog.user.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.entity.YearlyRecapSnapshot;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.infrastructure.repository.YearlyRecapSnapshotRepository;
import com.faithlog.user.service.policy.YearlyRecapPolicy;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;

@SpringBootTest
@ActiveProfiles("test")
class YearlyRecapConcurrencyIntegrationTest {

	@Autowired private UserRepository userRepository;
	@Autowired private CampusRepository campusRepository;
	@Autowired private CampusMemberRepository campusMemberRepository;
	@Autowired private YearlyRecapSnapshotRepository snapshotRepository;
	@Autowired private YearlyRecapQueryService queryService;
	@Autowired private YearlyRecapPresentationCommandService presentationCommandService;
	@Autowired private YearlyRecapPolicy policy;
	@Autowired private JdbcTemplate jdbcTemplate;

	@AfterEach
	void resetArchiveCoverage() {
		jdbcTemplate.update(
			"update yearly_recap_archive_coverage set complete_from_year = ?",
			policy.previousPeriod().recapYear() + 2
		);
	}

	@Test
	void concurrent_first_get_and_presented_create_one_snapshot_and_preserve_one_first_time() throws Exception {
		User user = userRepository.saveAndFlush(User.create(
			"동시 회고", "recap-concurrent@example.com", "hash"
		));
		Campus campus = campusRepository.saveAndFlush(Campus.create(
			"동시 회고 캠퍼스", "서울", "동시성", "RECAP-CONCURRENT-236"
		));
		CampusMember membership = CampusMember.createMember(campus.id(), user.id());
		ReflectionTestUtils.setField(membership, "joinedAt", Instant.parse("2020-01-01T00:00:00Z"));
		campusMemberRepository.saveAndFlush(membership);
		int recapYear = policy.previousPeriod().recapYear();
		markArchiveCoverageComplete(recapYear);

		runConcurrently(8, () -> queryService.getPrevious(user.id()));

		assertThat(snapshotRepository.findAll().stream()
			.filter(snapshot -> snapshotRepository
				.findByUserIdAndRecapYear(user.id(), recapYear)
				.map(candidate -> candidate.id().equals(snapshot.id()))
				.orElse(false)))
			.hasSize(1);

		runConcurrently(8, () -> {
			presentationCommandService.markPresented(user.id(), recapYear);
			return null;
		});

		YearlyRecapSnapshot snapshot = snapshotRepository
			.findByUserIdAndRecapYear(user.id(), recapYear)
			.orElseThrow();
		assertThat(snapshot.firstPresentedAt()).isNotNull();
		assertThat(queryService.getPrevious(user.id()).presentation().shouldAutoPresent()).isFalse();
	}

	private <T> List<T> runConcurrently(int taskCount, ThrowingTask<T> task) throws Exception {
		ExecutorService executor = Executors.newFixedThreadPool(taskCount);
		CountDownLatch ready = new CountDownLatch(taskCount);
		CountDownLatch start = new CountDownLatch(1);
		try {
			List<Future<T>> futures = new ArrayList<>();
			for (int index = 0; index < taskCount; index++) {
				futures.add(executor.submit(() -> {
					ready.countDown();
					start.await();
					return task.run();
				}));
			}
			ready.await();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> future : futures) {
				results.add(future.get());
			}
			return results;
		} finally {
			executor.shutdownNow();
		}
	}

	private void markArchiveCoverageComplete(int recapYear) {
		for (String factType : new String[]{
			"COMMENT", "PRAYER", "DEVOTION_DAILY", "DEVOTION_WEEKLY", "PENALTY"}) {
			jdbcTemplate.update("""
				merge into yearly_recap_archive_coverage (fact_type, complete_from_year)
				key (fact_type) values (?, ?)
				""", factType, recapYear);
		}
	}

	@FunctionalInterface
	private interface ThrowingTask<T> {
		T run() throws Exception;
	}
}
