package com.faithlog.weeklymaterial.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.media.domain.entity.MediaAsset;
import com.faithlog.media.domain.type.MediaAssetStatus;
import com.faithlog.media.infrastructure.repository.MediaAssetRepository;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterial;
import com.faithlog.weeklymaterial.domain.entity.WeeklyMaterialNotificationOutbox;
import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialNotificationOutboxRepository;
import com.faithlog.weeklymaterial.infrastructure.repository.WeeklyMaterialRepository;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialNotificationOutboxRepositoryPort;
import com.faithlog.weeklymaterial.service.port.WeeklyMaterialRecipientPort;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(WeeklyMaterialOutboxPersistenceIntegrationTest.BarrierConfiguration.class)
class WeeklyMaterialOutboxPersistenceIntegrationTest {
	private static final LocalDate WEEK = LocalDate.of(2026, 5, 4);

	@Autowired private WeeklyMaterialRepository materials;
	@Autowired private WeeklyMaterialNotificationOutboxRepository outboxes;
	@Autowired private BarrierOutboxPort barrierOutboxes;
	@Autowired private MediaAssetRepository assets;
	@Autowired private WeeklyMaterialRetentionService retention;
	@Autowired private WeeklyMaterialFirstPublication firstPublication;
	@Autowired private WeeklyMaterialNotificationOutboxProcessor processor;
	@Autowired private PlatformTransactionManager transactionManager;
	@MockitoBean private WeeklyMaterialRecipientPort recipients;
	@MockitoBean private NotificationRequestCommandService notifications;

	@AfterEach
	void clean() {
		transaction().executeWithoutResult(status -> {
			outboxes.deleteAll();
			materials.deleteAll();
			assets.deleteAll();
		});
		barrierOutboxes.disarm();
		reset(recipients, notifications);
	}

	@Test
	void retentionSuppressesPendingOutboxAndProcessorNeverSendsDeletedPublication() {
		Fixture fixture = persistFixture("retention-suppress");

		assertThat(retention.deleteIfDue(fixture.materialId(), LocalDate.of(2026, 8, 4))).isTrue();

		assertThat(materials.findById(fixture.materialId())).isEmpty();
		assertThat(((org.springframework.data.repository.CrudRepository<WeeklyMaterialNotificationOutbox, Long>) outboxes)
			.findById(fixture.outboxId())).get()
			.extracting(WeeklyMaterialNotificationOutbox::isProcessed).isEqualTo(true);
		assertThat(assets.findById(fixture.assetId())).get()
			.extracting(MediaAsset::status).isEqualTo(MediaAssetStatus.ORPHANED);
		assertThat(processor.process(fixture.outboxId())).isFalse();
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}

	@Test
	void suppressionRollsBackWhenRetentionCannotOrphanInvalidMedia() {
		Fixture fixture = persistFixture("rollback");
		transaction().executeWithoutResult(status -> {
			MediaAsset asset = assets.findById(fixture.assetId()).orElseThrow();
			org.springframework.test.util.ReflectionTestUtils.setField(asset, "status", MediaAssetStatus.FAILED);
		});

		assertThatThrownBy(() -> retention.deleteIfDue(fixture.materialId(), LocalDate.of(2026, 8, 4)))
			.isInstanceOf(com.faithlog.global.exception.BusinessException.class);

		assertThat(materials.findById(fixture.materialId())).isPresent();
		assertThat(((org.springframework.data.repository.CrudRepository<WeeklyMaterialNotificationOutbox, Long>) outboxes)
			.findById(fixture.outboxId())).get()
			.extracting(WeeklyMaterialNotificationOutbox::isProcessed).isEqualTo(false);
	}

	@Test
	void physicalDeleteThenSameSlotRegistrationKeepsSingleHistoryAndCreatesNoNotification() {
		Fixture fixture = persistFixture("history");
		assertThat(retention.deleteIfDue(fixture.materialId(), LocalDate.of(2026, 8, 4))).isTrue();
		Long replacementAssetId = persistReadyPdf("history-replacement");

		Long replacementMaterialId = transaction().execute(status -> {
			WeeklyMaterial replacement = materials.saveAndFlush(WeeklyMaterial.create(
				1L, WEEK, WeeklyMaterialType.SHARING_SHEET, replacementAssetId, 101L));
			firstPublication.recordFirstRegistration(replacement, true);
			return replacement.id();
		});

		assertThat(replacementMaterialId).isNotNull();
		assertThat(outboxes.findAll()).hasSize(1)
			.first().extracting(WeeklyMaterialNotificationOutbox::weeklyMaterialId)
			.isEqualTo(fixture.materialId());
		verify(notifications, never()).requestRequiredAutomaticNotification(any());
	}

	@Test
	@Timeout(10)
	void processorAndRetentionSerializeAsSendBeforeDeleteWhenProcessorLocksFirst() throws Exception {
		Fixture fixture = persistFixture("concurrency");
		when(recipients.findActiveMemberUserIds(1L)).thenReturn(List.of(101L));
		CountDownLatch sendStarted = new CountDownLatch(1);
		CountDownLatch allowSend = new CountDownLatch(1);
		doAnswer(invocation -> {
			sendStarted.countDown();
			assertThat(allowSend.await(5, TimeUnit.SECONDS)).isTrue();
			return null;
		}).when(notifications).requestRequiredAutomaticNotification(any());

		var executor = Executors.newFixedThreadPool(2);
		try {
			var processing = executor.submit(() -> processor.process(fixture.outboxId()));
			assertThat(sendStarted.await(5, TimeUnit.SECONDS)).isTrue();
			var deleting = executor.submit(
				() -> retention.deleteIfDue(fixture.materialId(), LocalDate.of(2026, 8, 4)));
			allowSend.countDown();

			assertThat(processing.get(5, TimeUnit.SECONDS)).isTrue();
			assertThat(deleting.get(5, TimeUnit.SECONDS)).isTrue();
		} finally {
			allowSend.countDown();
			executor.shutdownNow();
		}

		verify(notifications).requestRequiredAutomaticNotification(any());
		assertThat(materials.findById(fixture.materialId())).isEmpty();
		assertThat(((org.springframework.data.repository.CrudRepository<WeeklyMaterialNotificationOutbox, Long>) outboxes)
			.findById(fixture.outboxId())).get()
			.extracting(WeeklyMaterialNotificationOutbox::isProcessed).isEqualTo(true);
	}

	@Test
	@Timeout(10)
	void concurrentProcessorsThatBothReadPendingSnapshotSendExactlyOnce() throws Exception {
		Fixture fixture = persistFixture("processor-race");
		when(recipients.findActiveMemberUserIds(1L)).thenReturn(List.of(101L));
		CountDownLatch bothSnapshotsRead = new CountDownLatch(2);
		barrierOutboxes.arm(fixture.outboxId(), bothSnapshotsRead);

		var executor = Executors.newFixedThreadPool(2);
		try {
			var first = executor.submit(() -> processor.process(fixture.outboxId()));
			var second = executor.submit(() -> processor.process(fixture.outboxId()));

			assertThat(List.of(first.get(5, TimeUnit.SECONDS), second.get(5, TimeUnit.SECONDS)))
				.containsExactlyInAnyOrder(true, false);
		} finally {
			executor.shutdownNow();
		}

		verify(notifications).requestRequiredAutomaticNotification(any());
		assertThat(((org.springframework.data.repository.CrudRepository<WeeklyMaterialNotificationOutbox, Long>) outboxes)
			.findById(fixture.outboxId())).get()
			.extracting(WeeklyMaterialNotificationOutbox::isProcessed).isEqualTo(true);
	}

	private Fixture persistFixture(String suffix) {
		Long assetId = persistReadyPdf(suffix);
		return transaction().execute(status -> {
			WeeklyMaterial material = materials.saveAndFlush(WeeklyMaterial.create(
				1L, WEEK, WeeklyMaterialType.SHARING_SHEET, assetId, 100L));
			WeeklyMaterialNotificationOutbox outbox = outboxes.saveAndFlush(
				WeeklyMaterialNotificationOutbox.create(1L, material.id(), WEEK, 100L));
			return new Fixture(material.id(), outbox.id(), assetId);
		});
	}

	private Long persistReadyPdf(String suffix) {
		return transaction().execute(status -> {
			MediaAsset asset = MediaAsset.reserve(1L, 100L, "application/pdf", 100,
				"a".repeat(64), "tmp/weekly-" + suffix,
				Instant.parse("2026-05-04T00:00:00Z"), suffix + ".pdf");
			asset.startProcessing();
			asset.completePdf("private/weekly-" + suffix, 100, "b".repeat(64));
			return assets.saveAndFlush(asset).id();
		});
	}

	private TransactionTemplate transaction() {
		return new TransactionTemplate(transactionManager);
	}

	private record Fixture(Long materialId, Long outboxId, Long assetId) {}

	@TestConfiguration
	static class BarrierConfiguration {
		@Bean
		@Primary
		BarrierOutboxPort barrierOutboxPort(WeeklyMaterialNotificationOutboxRepository repository) {
			return new BarrierOutboxPort(repository);
		}
	}

	static final class BarrierOutboxPort implements WeeklyMaterialNotificationOutboxRepositoryPort {
		private final WeeklyMaterialNotificationOutboxRepository delegate;
		private volatile Long barrierId;
		private volatile CountDownLatch barrier;

		BarrierOutboxPort(WeeklyMaterialNotificationOutboxRepository delegate) {
			this.delegate = delegate;
		}

		void arm(Long outboxId, CountDownLatch barrier) {
			this.barrierId = outboxId;
			this.barrier = barrier;
		}

		void disarm() {
			barrierId = null;
			barrier = null;
		}

		@Override
		public WeeklyMaterialNotificationOutbox save(WeeklyMaterialNotificationOutbox outbox) {
			return ((WeeklyMaterialNotificationOutboxRepositoryPort) delegate).save(outbox);
		}

		@Override
		public java.util.Optional<WeeklyMaterialNotificationOutbox> findById(Long id) {
			@SuppressWarnings("unchecked")
			var crud = (org.springframework.data.repository.CrudRepository<WeeklyMaterialNotificationOutbox, Long>) delegate;
			var result = crud.findById(id);
			CountDownLatch current = barrier;
			if (id.equals(barrierId) && current != null) {
				current.countDown();
				try {
					if (!current.await(5, TimeUnit.SECONDS)) throw new AssertionError("snapshot barrier timeout");
				} catch (InterruptedException exception) {
					Thread.currentThread().interrupt();
					throw new AssertionError(exception);
				}
			}
			return result;
		}

		@Override
		public java.util.Optional<WeeklyMaterialNotificationOutbox> findByIdForUpdate(Long id) {
			return delegate.findByIdForUpdate(id);
		}

		@Override
		public java.util.Optional<WeeklyMaterialNotificationOutbox> findSlotForUpdate(Long campusId,
			LocalDate weekStartDate, WeeklyMaterialType materialType) {
			return delegate.findSlotForUpdate(campusId, weekStartDate, materialType);
		}

		@Override
		public List<Long> findPendingIds(org.springframework.data.domain.Pageable pageable) {
			return delegate.findPendingIds(pageable);
		}
	}
}
