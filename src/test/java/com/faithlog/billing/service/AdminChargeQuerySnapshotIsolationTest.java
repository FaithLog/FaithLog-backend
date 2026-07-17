package com.faithlog.billing.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.billing.domain.entity.ChargeItem;
import com.faithlog.billing.domain.entity.PaymentAccount;
import com.faithlog.billing.domain.type.ChargeSourceType;
import com.faithlog.billing.domain.type.PaymentCategory;
import com.faithlog.billing.infrastructure.repository.ChargeItemRepository;
import com.faithlog.billing.infrastructure.repository.PaymentAccountRepository;
import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import java.lang.reflect.Method;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
class AdminChargeQuerySnapshotIsolationTest {

	private static final AtomicLong SEQUENCE = new AtomicLong();

	@Autowired
	private PlatformTransactionManager transactionManager;
	@Autowired
	private JdbcTemplate jdbcTemplate;
	@Autowired
	private UserRepository userRepository;
	@Autowired
	private CampusRepository campusRepository;
	@Autowired
	private CampusMemberRepository campusMemberRepository;
	@Autowired
	private PaymentAccountRepository paymentAccountRepository;
	@Autowired
	private ChargeItemRepository chargeItemRepository;

	@ParameterizedTest
	@MethodSource("campusAggregateMethods")
	void campusAggregateQueriesUseOneRepeatableReadSnapshot(String methodName) throws Exception {
		Fixture fixture = createFixture();
		Method method = Stream.of(AdminChargeQueryService.class.getDeclaredMethods())
			.filter(candidate -> candidate.getName().equals(methodName))
			.findFirst()
			.orElseThrow();
		Transactional transactional = AnnotatedElementUtils.findMergedAnnotation(method, Transactional.class);
		assertThat(transactional).isNotNull();
		assertThat(transactional.readOnly()).isTrue();

		TransactionTemplate reader = new TransactionTemplate(transactionManager);
		reader.setReadOnly(true);
		reader.setIsolationLevel(transactional.isolation().value());
		CountDownLatch summaryRead = new CountDownLatch(1);
		CountDownLatch concurrentInsertCommitted = new CountDownLatch(1);
		var executor = Executors.newSingleThreadExecutor();
		try {
			var response = executor.submit(() -> reader.execute(status -> {
				long summaryTotal = aggregateTotal(fixture.campusId());
				summaryRead.countDown();
				await(concurrentInsertCommitted);
				long memberTotal = aggregateTotal(fixture.campusId());
				long memberCount = aggregateMemberCount(fixture.campusId());
				return new SnapshotResult(summaryTotal, memberTotal, memberCount);
			}));

			assertThat(summaryRead.await(5, TimeUnit.SECONDS)).isTrue();
			new TransactionTemplate(transactionManager).executeWithoutResult(status ->
				chargeItemRepository.saveAndFlush(charge(
					fixture.campusId(), fixture.secondMemberId(), fixture.account(), fixture.sourceId() + 1, 2000
				))
			);
			concurrentInsertCommitted.countDown();

			SnapshotResult result = response.get(5, TimeUnit.SECONDS);
			assertThat(result.summaryTotal()).isEqualTo(result.memberTotal());
			assertThat(result.memberCount()).isEqualTo(1L);
			assertThat(transactional.isolation()).isEqualTo(Isolation.REPEATABLE_READ);
		} finally {
			concurrentInsertCommitted.countDown();
			executor.shutdownNow();
		}
	}

	private Fixture createFixture() {
		long sequence = SEQUENCE.incrementAndGet();
		TransactionTemplate writer = new TransactionTemplate(transactionManager);
		return writer.execute(status -> {
			User manager = saveUser("snapshot-manager-" + sequence + "@example.com", UserRole.MANAGER);
			User firstMember = saveUser("snapshot-first-" + sequence + "@example.com", UserRole.USER);
			User secondMember = saveUser("snapshot-second-" + sequence + "@example.com", UserRole.USER);
			Campus campus = campusRepository.saveAndFlush(Campus.create(
				"snapshot-" + sequence,
				"분당",
				"snapshot address",
				"SNAP" + sequence
			));
			campusMemberRepository.saveAndFlush(CampusMember.createMinister(campus.id(), manager.id()));
			campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), firstMember.id()));
			campusMemberRepository.saveAndFlush(CampusMember.createMember(campus.id(), secondMember.id()));
			PaymentAccount account = paymentAccountRepository.saveAndFlush(PaymentAccount.create(
				campus.id(),
				PaymentCategory.PENALTY,
				"snapshot account",
				"테스트은행",
				"SNAP-" + sequence,
				"테스트",
				manager.id()
			));
			long sourceId = sequence * 100;
			chargeItemRepository.saveAndFlush(charge(campus.id(), firstMember.id(), account, sourceId, 1000));
			return new Fixture(campus.id(), secondMember.id(), account, sourceId);
		});
	}

	private User saveUser(String email, UserRole role) {
		User user = User.create("snapshot user", email, "encoded");
		ReflectionTestUtils.setField(user, "role", role);
		return userRepository.saveAndFlush(user);
	}

	private ChargeItem charge(
		Long campusId,
		Long userId,
		PaymentAccount account,
		long sourceId,
		int amount
	) {
		return ChargeItem.create(
			campusId,
			userId,
			PaymentCategory.PENALTY,
			account.id(),
			account.bankName(),
			account.accountNumber(),
			account.accountHolder(),
			ChargeSourceType.DEVOTION_RECORD,
			sourceId,
			"snapshot charge",
			null,
			amount,
			null
		);
	}

	private long aggregateTotal(Long campusId) {
		return jdbcTemplate.queryForObject("""
			select coalesce(sum(charge.amount), 0)
			from charge_items charge
			join campus_members member
				on member.campus_id = charge.campus_id
				and member.user_id = charge.user_id
				and member.status = 'ACTIVE'
			join users user_account
				on user_account.id = charge.user_id
				and user_account.is_active = true
			where charge.campus_id = ?
			""", Long.class, campusId);
	}

	private long aggregateMemberCount(Long campusId) {
		return jdbcTemplate.queryForObject("""
			select count(distinct charge.user_id)
			from charge_items charge
			where charge.campus_id = ?
			""", Long.class, campusId);
	}

	private void await(CountDownLatch latch) {
		try {
			if (!latch.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent insert did not commit in time");
			}
		} catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException(exception);
		}
	}

	private static Stream<String> campusAggregateMethods() {
		return Stream.of("listAdminCampusCharges", "listAdminCampusChargesForMyAccounts");
	}

	private record Fixture(Long campusId, Long secondMemberId, PaymentAccount account, long sourceId) {
	}

	private record SnapshotResult(long summaryTotal, long memberTotal, long memberCount) {
	}
}
