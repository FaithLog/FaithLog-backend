package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.infrastructure.repository.MediaAssetRepository;
import com.faithlog.user.domain.entity.User;
import com.faithlog.user.domain.type.UserRole;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialNotificationOutboxRepository;
import com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialRepository;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class WeeklyMaterialAdminPersistenceIntegrationTest {
	private static final LocalDate WEEK = LocalDate.of(2026, 8, 3);

	@Autowired private WeeklyMaterialAdminService admin;
	@MockitoSpyBean private WeeklyMaterialQueryService queries;
	@Autowired private WeeklyMaterialRepository materials;
	@Autowired private com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialGlobalLockRepository globalLocks;
	@Autowired private WeeklyMaterialNotificationOutboxRepository outboxes;
	@Autowired private MediaAssetRepository assets;
	@Autowired private CampusRepository campuses;
	@Autowired private CampusMemberRepository campusMembers;
	@Autowired private com.faithlog.weeklymaterial.infrastructure.adapter.WeeklyMaterialRecipientAdapter recipientAdapter;
	@Autowired private UserRepository users;
	@Autowired private PlatformTransactionManager transactionManager;

	@BeforeEach
	void setUpGlobalLock() {
		globalLocks.saveAndFlush(com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialGlobalLock.singleton());
	}

	@AfterEach
	void clean() {
		transaction().executeWithoutResult(status -> {
			outboxes.deleteAll();
			materials.deleteAll();
			assets.deleteAll();
			campusMembers.deleteAll();
			campuses.deleteAll();
			users.deleteAll();
		});
	}

	@Test
	void globalAdminWithoutMembershipPutsAndReceivesManagerResponseButPublicReadStaysForbidden() {
		Fixture fixture = persistFixture("success");

		var response = admin.putAndGet(fixture.campusId(), WEEK, WeeklyMaterialType.SHEPHERD_GUIDE,
			fixture.assetId(), fixture.adminId());

		assertThat(response.weekStartDate()).isEqualTo(WEEK);
		assertThat(response.shepherdGuide()).isNotNull();
		assertThat(response.shepherdGuide().assetId()).isEqualTo(fixture.assetId());
		assertThat(materials.findAll()).hasSize(1);
		assertThatThrownBy(() -> queries.getWeek(fixture.campusId(), fixture.adminId(), WEEK))
			.isInstanceOfSatisfying(BusinessException.class, exception ->
				assertThat(exception.errorCode()).isEqualTo(ErrorCode.WEEKLY_MATERIAL_ACCESS_FORBIDDEN));
	}

	@Test
	void managerResponseAssemblyFailureRollsBackCommandAndLeavesPdfReady() {
		Fixture fixture = persistFixture("rollback");
		doThrow(new IllegalStateException("response assembly failed")).when(queries)
			.getWeekForManager(fixture.campusId(), fixture.adminId(), WEEK);

		assertThatThrownBy(() -> admin.putAndGet(fixture.campusId(), WEEK,
			WeeklyMaterialType.SHEPHERD_GUIDE, fixture.assetId(), fixture.adminId()))
			.isInstanceOf(IllegalStateException.class);

		assertThat(materials.findAll()).isEmpty();
		assertThat(assets.findById(fixture.assetId())).get()
			.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.READY);
	}

	@Test
	@Timeout(10)
	void differentCampusManagersConcurrentFirstSundayPutSerializeToOneGlobalSlotAndOneOutbox() throws Exception {
		Fixture left = persistFixture("global-race-left");
		Fixture right = persistFixture("global-race-right");
		CountDownLatch start = new CountDownLatch(1);
		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return admin.putAndGet(left.campusId(), WEEK, WeeklyMaterialType.SUNDAY_SHARING_SHEET,
					left.assetId(), left.adminId());
			});
			var second = executor.submit(() -> {
				start.await(5, TimeUnit.SECONDS);
				return admin.putAndGet(right.campusId(), WEEK, WeeklyMaterialType.SUNDAY_SHARING_SHEET,
					right.assetId(), right.adminId());
			});
			start.countDown();
			assertThat(first.get(5, TimeUnit.SECONDS).weekStartDate()).isEqualTo(WEEK);
			assertThat(second.get(5, TimeUnit.SECONDS).weekStartDate()).isEqualTo(WEEK);
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(materials.findAll()).hasSize(1);
		assertThat(outboxes.findAll()).hasSize(1);
		assertThat(assets.findAll()).extracting(MediaAsset::status)
			.containsExactlyInAnyOrder(MediaAssetStatus.READY, MediaAssetStatus.ORPHANED);
	}

	@Test
	void globalRecipientsAreDistinctActiveUsersWithDeterministicValidCampusContext() {
		Fixture left = persistFixture("recipient-left");
		Fixture right = persistFixture("recipient-right");
		campusMembers.saveAndFlush(CampusMember.createMember(left.campusId(), left.adminId()));
		campusMembers.saveAndFlush(CampusMember.createMember(right.campusId(), left.adminId()));
		campusMembers.saveAndFlush(CampusMember.createMember(right.campusId(), right.adminId()));

		assertThat(recipientAdapter.findAllActiveRecipients())
			.extracting(recipient -> List.of(recipient.userId(), recipient.campusId()))
			.containsExactly(
				List.of(left.adminId(), Math.min(left.campusId(), right.campusId())),
				List.of(right.adminId(), right.campusId()));
	}

	private Fixture persistFixture(String suffix) {
		return transaction().execute(status -> {
			User user = User.create("관리자", suffix + "@faithlog.test", "encoded");
			user.changeRole(UserRole.ADMIN);
			user = users.saveAndFlush(user);
			Campus campus = campuses.saveAndFlush(Campus.create(
				"테스트 캠퍼스", "서울", "", "weekly-" + suffix));
			MediaAsset asset = MediaAsset.reserve(campus.id(), user.id(), "application/pdf", 100,
				"a".repeat(64), "tmp/admin-" + suffix, Instant.parse("2026-08-05T00:00:00Z"),
				suffix + ".pdf");
			asset.startProcessing();
			asset.completePdf("private/admin-" + suffix, 100, "b".repeat(64));
			asset = assets.saveAndFlush(asset);
			return new Fixture(campus.id(), user.id(), asset.id());
		});
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private record Fixture(Long campusId, Long adminId, Long assetId) {}
}
