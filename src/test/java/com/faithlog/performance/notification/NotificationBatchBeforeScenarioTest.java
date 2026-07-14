package com.faithlog.performance.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.campus.domain.type.CampusMemberStatus;
import com.faithlog.campus.infrastructure.repository.CampusMemberRepository;
import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.entity.UserFcmToken;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.domain.type.SendStatus;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.notification.service.FcmSendException;
import com.faithlog.notification.service.NotificationDeliveryWorker;
import com.faithlog.notification.service.NotificationRequestCommandService;
import com.faithlog.notification.service.NotificationRetryBackoff;
import com.faithlog.notification.service.command.AutomaticNotificationRequestCommand;
import com.faithlog.notification.service.port.FcmSendCommand;
import com.faithlog.notification.service.port.FcmSendFailureType;
import com.faithlog.notification.service.port.FcmSendPort;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import com.sun.management.OperatingSystemMXBean;
import jakarta.persistence.EntityManagerFactory;
import java.io.IOException;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(properties = {
	"faithlog.scheduler.enabled=false",
	"spring.jpa.properties.hibernate.generate_statistics=true",
	"logging.level.org.hibernate.SQL=warn"
})
@ActiveProfiles("local")
@EnabledIfEnvironmentVariable(named = "ALLOW_NOTIFICATION_BATCH_BASELINE", matches = "true")
class NotificationBatchBeforeScenarioTest {

	private static final String TOKEN_PREFIX = "PERFORMANCE_198_DUMMY:";
	private static final String FAILURE_REASON = "UNREGISTERED";

	@Autowired
	private NotificationRequestCommandService requestCommandService;

	@Autowired
	private NotificationDeliveryWorker deliveryWorker;

	@Autowired
	private CampusMemberRepository campusMemberRepository;

	@Autowired
	private UserFcmTokenRepository userFcmTokenRepository;

	@Autowired
	private NotificationLogRepository notificationLogRepository;

	@Autowired
	private CapturingNotificationDispatchPort dispatchPort;

	@Autowired
	private FakeFcmSendPort fakeFcmSendPort;

	@Autowired
	private EntityManagerFactory entityManagerFactory;

	@Autowired
	private ObjectMapper objectMapper;

	@Autowired
	private Environment environment;

	@Test
	void measures_current_automatic_request_and_delivery_worker_before_flow() throws IOException {
		ScenarioConfig config = ScenarioConfig.fromEnvironment();
		requireSafeRuntime(config);
		dispatchPort.reset();
		fakeFcmSendPort.reset();

		List<CampusMember> members = campusMemberRepository.findByCampusIdAndStatusOrderByIdAsc(
			config.campusId(),
			CampusMemberStatus.ACTIVE
		);
		assertThat(members).hasSize(config.memberCount());
		List<Long> targetUserIds = members.stream().map(CampusMember::userId).toList();
		Set<Long> targetUserIdSet = new LinkedHashSet<>(targetUserIds);
		assertThat(targetUserIdSet).hasSize(config.memberCount());

		String fixtureTokenPrefix = TOKEN_PREFIX + config.fixtureRunId() + ":";
		List<UserFcmToken> allTokensBefore = userFcmTokenRepository.findAll();
		List<UserFcmToken> fixtureTokensBefore = allTokensBefore.stream()
			.filter(token -> token.token().startsWith(fixtureTokenPrefix))
			.toList();
		assertFixtureContract(config, targetUserIdSet, fixtureTokensBefore);
		assertThat(allTokensBefore.stream()
			.filter(UserFcmToken::isActive)
			.filter(token -> targetUserIdSet.contains(token.userId()))
			.filter(token -> !token.token().startsWith(fixtureTokenPrefix)))
			.as("selected users must not have active tokens outside this fixtureRunId")
			.isEmpty();
		Map<Long, TokenSnapshot> nonFixtureTokensBefore = snapshotsOf(
			allTokensBefore.stream().filter(token -> !token.token().startsWith(fixtureTokenPrefix)).toList()
		);

		String title = "PERFORMANCE #198 " + config.fixtureRunId();
		AutomaticNotificationRequestCommand command = new AutomaticNotificationRequestCommand(
			config.campusId(),
			NotificationType.CUSTOM,
			null,
			null,
			targetUserIds,
			config.businessDate(),
			"issue-198:" + config.fixtureRunId(),
			title,
			"Issue #198 test-only fake notification; external FCM is disabled."
		);

		Statistics statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
		statistics.setStatisticsEnabled(true);
		statistics.clear();
		ResourceSnapshot creationStart = ResourceSnapshot.capture();
		long creationStartedAt = System.nanoTime();
		int createdCount = requestCommandService.requestAutomaticNotification(command);
		long creationDurationNanos = System.nanoTime() - creationStartedAt;
		long creationPreparedStatements = statistics.getPrepareStatementCount();
		ResourceDelta creationResources = creationStart.delta();

		UUID requestId = dispatchPort.singleRequestId();
		List<NotificationLog> createdLogs = notificationLogRepository.findByRequestIdOrderByIdAsc(requestId);
		Map<SendStatus, Long> creationStatuses = statusCounts(createdLogs);
		assertThat(createdCount).isEqualTo(config.memberCount());
		assertThat(createdLogs).hasSize(config.memberCount());
		assertThat(createdLogs).extracting(NotificationLog::userId).containsExactlyElementsOf(targetUserIds);
		assertThat(createdLogs).allMatch(log -> log.campusId().equals(config.campusId()));

		statistics.clear();
		long duplicateStartedAt = System.nanoTime();
		int duplicateCreatedCount = requestCommandService.requestAutomaticNotification(command);
		long duplicateDurationNanos = System.nanoTime() - duplicateStartedAt;
		long duplicatePreparedStatements = statistics.getPrepareStatementCount();

		statistics.clear();
		ResourceSnapshot deliveryStart = ResourceSnapshot.capture();
		long deliveryStartedAt = System.nanoTime();
		deliveryWorker.processRequest(requestId);
		long deliveryDurationNanos = System.nanoTime() - deliveryStartedAt;
		long deliveryPreparedStatements = statistics.getPrepareStatementCount();
		ResourceDelta deliveryResources = deliveryStart.delta();

		List<NotificationLog> deliveredLogs = notificationLogRepository.findByRequestIdOrderByIdAsc(requestId);
		Map<SendStatus, Long> deliveredStatuses = statusCounts(deliveredLogs);
		List<UserFcmToken> allTokensAfter = userFcmTokenRepository.findAll();
		List<UserFcmToken> fixtureTokensAfter = allTokensAfter.stream()
			.filter(token -> token.token().startsWith(fixtureTokenPrefix))
			.toList();
		Map<Long, TokenSnapshot> nonFixtureTokensAfter = snapshotsOf(
			allTokensAfter.stream().filter(token -> !token.token().startsWith(fixtureTokenPrefix)).toList()
		);

		long pendingExpected = config.successCount() + config.transientCount() + config.permanentCount();
		long skippedExpected = config.inactiveCount() + config.noTokenCount();
		long sentExpected = config.successCount() + config.transientCount();
		long failedExpected = config.permanentCount();
		long sendAttemptsExpected = config.successCount() + (config.transientCount() * 2L)
			+ config.permanentCount();
		long permanentDeactivated = fixtureTokensAfter.stream()
			.filter(token -> token.token().contains(":permanent:"))
			.filter(token -> !token.isActive())
			.filter(token -> FAILURE_REASON.equals(token.lastFailureReason()))
			.count();
		long nonFixtureTokenMutationCount = nonFixtureMutationCount(nonFixtureTokensBefore, nonFixtureTokensAfter);
		long crossCampusMutationCount = deliveredLogs.stream()
			.filter(log -> !log.campusId().equals(config.campusId()) || !targetUserIdSet.contains(log.userId()))
			.count();

		assertThat(creationStatuses.getOrDefault(SendStatus.PENDING, 0L)).isEqualTo(pendingExpected);
		assertThat(creationStatuses.getOrDefault(SendStatus.SKIPPED, 0L)).isEqualTo(skippedExpected);
		assertThat(deliveredStatuses.getOrDefault(SendStatus.SENT, 0L)).isEqualTo(sentExpected);
		assertThat(deliveredStatuses.getOrDefault(SendStatus.FAILED, 0L)).isEqualTo(failedExpected);
		assertThat(deliveredStatuses.getOrDefault(SendStatus.SKIPPED, 0L)).isEqualTo(skippedExpected);
		assertThat(deliveredStatuses.getOrDefault(SendStatus.PENDING, 0L)).isZero();
		assertThat(permanentDeactivated).isEqualTo(config.permanentCount());
		assertThat(fakeFcmSendPort.permanentFailureCount()).isEqualTo(config.permanentCount());
		assertThat(fakeFcmSendPort.transientRetryCount()).isEqualTo(config.transientCount());
		assertThat(fakeFcmSendPort.totalAttemptCount()).isEqualTo(sendAttemptsExpected);
		assertThat(duplicateCreatedCount).isZero();
		assertThat(dispatchPort.requestIds()).containsExactly(requestId);
		assertThat(crossCampusMutationCount).isZero();
		assertThat(nonFixtureTokenMutationCount).isZero();

		Map<String, Object> report = new LinkedHashMap<>();
		report.put("datasetId", config.datasetId());
		report.put("fixtureRunId", config.fixtureRunId());
		report.put("sampleKind", config.sampleKind());
		report.put("campusId", config.campusId());
		report.put("requestId", requestId.toString());
		report.put("externalFcmUsed", false);
		report.put("springProfile", "local");
		report.put("fcmAdapter", "deterministic-test-fake");
		report.put("dedupeKeyShape", "notificationType + campusId + scopeId + targetUserId + businessDate");
		report.put("creation", phaseReport(
			creationDurationNanos,
			config.memberCount(),
			creationPreparedStatements,
			config.memberCount(),
			creationResources,
			Map.of(
				"createdLogs", createdCount,
				"logInsertCount", createdCount,
				"pendingLogs", pendingExpected,
				"skippedLogs", skippedExpected,
				"tokenLookupCount", config.memberCount()
			)
		));
		report.put("delivery", phaseReport(
			deliveryDurationNanos,
			pendingExpected,
			deliveryPreparedStatements,
			config.memberCount(),
			deliveryResources,
			Map.of(
				"statusCounts", stringStatusCounts(deliveredStatuses),
				"logUpdateCount", pendingExpected,
				"tokenLookupCount", pendingExpected,
				"tokenUpdateCount", permanentDeactivated,
				"fakeSendAttemptCount", fakeFcmSendPort.totalAttemptCount()
			)
		));
		report.put("correctness", Map.of(
			"duplicateReplayCreatedCount", duplicateCreatedCount,
			"duplicateReplayDurationMs", nanosToMillis(duplicateDurationNanos),
			"duplicateReplayDbPreparedStatements", duplicatePreparedStatements,
			"crossCampusMutationCount", crossCampusMutationCount,
			"nonFixtureTokenMutationCount", nonFixtureTokenMutationCount,
			"partialFailureContinued", failedExpected > 0 && sentExpected > 0
		));
		report.put("capturedAt", Instant.now().toString());

		Path reportPath = config.reportDirectory().resolve("scenario-result.json");
		Files.createDirectories(reportPath.getParent());
		objectMapper.writerWithDefaultPrettyPrinter().writeValue(reportPath.toFile(), report);
	}

	private void requireSafeRuntime(ScenarioConfig config) {
		assertThat(environment.getActiveProfiles()).containsExactly("local");
		assertThat(environment.getProperty("spring.datasource.url"))
			.startsWith("jdbc:postgresql:");
		assertThat(config.composeProject()).doesNotContain("faithlog-latest");
		assertThat(requiredEnvironment("PERF_FCM_ADAPTER")).isEqualTo("fake");
		assertEnvironmentVariableBlank("FIREBASE_CONFIG_JSON");
		assertEnvironmentVariableBlank("FIREBASE_CONFIG_PATH");
	}

	private void assertEnvironmentVariableBlank(String name) {
		String value = System.getenv(name);
		assertThat(value == null || value.isBlank())
			.as("%s must be absent for the fake-only performance scenario", name)
			.isTrue();
	}

	private void assertFixtureContract(
		ScenarioConfig config,
		Set<Long> targetUserIds,
		List<UserFcmToken> fixtureTokens
	) {
		assertThat(config.memberCount()).isEqualTo(1000);
		assertThat(config.sampleKind()).isIn("warmup", "measured");
		assertThat(config.outcomeTotal()).isEqualTo(config.memberCount());
		assertThat(List.of(
			config.successCount(),
			config.transientCount(),
			config.permanentCount(),
			config.inactiveCount(),
			config.noTokenCount()
		)).allMatch(count -> count > 0);
		assertThat(fixtureTokens).hasSize(config.memberCount() - config.noTokenCount());
		assertThat(fixtureTokens).allMatch(token -> targetUserIds.contains(token.userId()));
		assertThat(fixtureTokens.stream().filter(UserFcmToken::isActive).count())
			.isEqualTo(config.successCount() + config.transientCount() + config.permanentCount());
		assertThat(fixtureTokens.stream().filter(token -> !token.isActive()).count())
			.isEqualTo(config.inactiveCount());
	}

	private Map<String, Object> phaseReport(
		long durationNanos,
		long throughputUnits,
		long dbPreparedStatements,
		long perUserDivisor,
		ResourceDelta resources,
		Map<String, Object> details
	) {
		Map<String, Object> report = new LinkedHashMap<>();
		double durationMs = nanosToMillis(durationNanos);
		report.put("durationMs", durationMs);
		report.put("throughputPerSecond", throughputUnits / (durationMs / 1000.0));
		report.put("dbPreparedStatements", dbPreparedStatements);
		report.put("perUserDbCalls", dbPreparedStatements / (double) perUserDivisor);
		report.put("processCpuDurationMs", nanosToMillis(resources.processCpuNanos()));
		report.put("heapUsedDeltaBytes", resources.heapUsedDeltaBytes());
		report.putAll(details);
		return report;
	}

	private Map<SendStatus, Long> statusCounts(List<NotificationLog> logs) {
		return logs.stream().collect(Collectors.groupingBy(
			NotificationLog::sendStatus,
			LinkedHashMap::new,
			Collectors.counting()
		));
	}

	private Map<String, Long> stringStatusCounts(Map<SendStatus, Long> statuses) {
		Map<String, Long> result = new LinkedHashMap<>();
		for (SendStatus status : SendStatus.values()) {
			result.put(status.name(), statuses.getOrDefault(status, 0L));
		}
		return result;
	}

	private Map<Long, TokenSnapshot> snapshotsOf(List<UserFcmToken> tokens) {
		return tokens.stream().collect(Collectors.toMap(
			UserFcmToken::id,
			TokenSnapshot::from,
			(left, right) -> left,
			LinkedHashMap::new
		));
	}

	private long nonFixtureMutationCount(
		Map<Long, TokenSnapshot> before,
		Map<Long, TokenSnapshot> after
	) {
		Set<Long> allIds = new LinkedHashSet<>(before.keySet());
		allIds.addAll(after.keySet());
		return allIds.stream().filter(id -> !java.util.Objects.equals(before.get(id), after.get(id))).count();
	}

	private static double nanosToMillis(long nanos) {
		return nanos / 1_000_000.0;
	}

	private static String requiredEnvironment(String name) {
		String value = System.getenv(name);
		if (value == null || value.isBlank()) {
			throw new IllegalStateException(name + " is required");
		}
		return value;
	}

	private record TokenSnapshot(boolean active, String failureReason, Instant updatedAt) {

		private static TokenSnapshot from(UserFcmToken token) {
			return new TokenSnapshot(token.isActive(), token.lastFailureReason(), token.updatedAt());
		}
	}

	private record ResourceSnapshot(long processCpuNanos, long heapUsedBytes) {

		private static ResourceSnapshot capture() {
			OperatingSystemMXBean operatingSystem = ManagementFactory.getPlatformMXBean(OperatingSystemMXBean.class);
			Runtime runtime = Runtime.getRuntime();
			return new ResourceSnapshot(
				operatingSystem.getProcessCpuTime(),
				runtime.totalMemory() - runtime.freeMemory()
			);
		}

		private ResourceDelta delta() {
			ResourceSnapshot current = capture();
			return new ResourceDelta(
				Math.max(0, current.processCpuNanos - processCpuNanos),
				current.heapUsedBytes - heapUsedBytes
			);
		}
	}

	private record ResourceDelta(long processCpuNanos, long heapUsedDeltaBytes) {
	}

	private record ScenarioConfig(
		String datasetId,
		String fixtureRunId,
		String sampleKind,
		Long campusId,
		int memberCount,
		int successCount,
		int transientCount,
		int permanentCount,
		int inactiveCount,
		int noTokenCount,
		LocalDate businessDate,
		String composeProject,
		Path reportDirectory
	) {

		private static ScenarioConfig fromEnvironment() {
			return new ScenarioConfig(
				requiredEnvironment("PERF_DATASET_ID"),
				requiredEnvironment("PERF_FIXTURE_RUN_ID"),
				requiredEnvironment("PERF_SAMPLE_KIND"),
				Long.valueOf(requiredEnvironment("PERF_CAMPUS_ID")),
				Integer.parseInt(requiredEnvironment("PERF_MEMBER_COUNT")),
				Integer.parseInt(requiredEnvironment("PERF_SUCCESS_COUNT")),
				Integer.parseInt(requiredEnvironment("PERF_TRANSIENT_COUNT")),
				Integer.parseInt(requiredEnvironment("PERF_PERMANENT_COUNT")),
				Integer.parseInt(requiredEnvironment("PERF_INACTIVE_COUNT")),
				Integer.parseInt(requiredEnvironment("PERF_NO_TOKEN_COUNT")),
				LocalDate.parse(requiredEnvironment("PERF_BUSINESS_DATE")),
				requiredEnvironment("PERF_COMPOSE_PROJECT"),
				Path.of(requiredEnvironment("PERF_REPORT_DIR"))
			);
		}

		private int outcomeTotal() {
			return successCount + transientCount + permanentCount + inactiveCount + noTokenCount;
		}
	}

	@TestConfiguration(proxyBeanMethods = false)
	static class FakeNotificationDeliveryConfig {

		@Bean
		@Primary
		FakeFcmSendPort fakeFcmSendPort() {
			return new FakeFcmSendPort();
		}

		@Bean
		@Primary
		NotificationRetryBackoff notificationRetryBackoff() {
			return retryNumber -> {
			};
		}

		@Bean
		@Primary
		CapturingNotificationDispatchPort capturingNotificationDispatchPort() {
			return new CapturingNotificationDispatchPort();
		}
	}

	static class CapturingNotificationDispatchPort implements NotificationDispatchPort {

		private final List<UUID> requestIds = new CopyOnWriteArrayList<>();

		@Override
		public void dispatch(UUID requestId) {
			requestIds.add(requestId);
		}

		UUID singleRequestId() {
			assertThat(requestIds).hasSize(1);
			return requestIds.get(0);
		}

		List<UUID> requestIds() {
			return List.copyOf(requestIds);
		}

		void reset() {
			requestIds.clear();
		}
	}

	static class FakeFcmSendPort implements FcmSendPort {

		private final Map<String, Integer> attempts = new ConcurrentHashMap<>();
		private final Set<String> transientRetriedTokens = ConcurrentHashMap.newKeySet();
		private final Set<String> permanentlyFailedTokens = ConcurrentHashMap.newKeySet();

		@Override
		public void send(FcmSendCommand command) {
			if (!command.token().startsWith(TOKEN_PREFIX)) {
				throw new IllegalStateException("Only Issue #198 dummy tokens may reach the fake sender");
			}
			int attempt = attempts.merge(command.token(), 1, Integer::sum);
			if (command.token().contains(":permanent:")) {
				permanentlyFailedTokens.add(command.token());
				throw new FcmSendException(FcmSendFailureType.PERMANENT, FAILURE_REASON);
			}
			if (command.token().contains(":transient:") && attempt == 1) {
				transientRetriedTokens.add(command.token());
				throw new FcmSendException(FcmSendFailureType.TRANSIENT, "TEST_ONLY_TIMEOUT");
			}
			if (!command.token().contains(":success:") && !command.token().contains(":transient:")) {
				throw new IllegalStateException("Unexpected non-dummy token reached the fake sender");
			}
		}

		long permanentFailureCount() {
			return permanentlyFailedTokens.size();
		}

		long transientRetryCount() {
			return transientRetriedTokens.size();
		}

		long totalAttemptCount() {
			return attempts.values().stream().mapToLong(Integer::longValue).sum();
		}

		void reset() {
			attempts.clear();
			transientRetriedTokens.clear();
			permanentlyFailedTokens.clear();
		}
	}
}
