package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;

import com.faithlog.campus.domain.entity.Campus;
import com.faithlog.campus.infrastructure.repository.CampusRepository;
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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
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
	@Autowired private WeeklyMaterialNotificationOutboxRepository outboxes;
	@Autowired private MediaAssetRepository assets;
	@Autowired private CampusRepository campuses;
	@Autowired private UserRepository users;
	@Autowired private PlatformTransactionManager transactionManager;

	@AfterEach
	void clean() {
		transaction().executeWithoutResult(status -> {
			outboxes.deleteAll();
			materials.deleteAll();
			assets.deleteAll();
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
