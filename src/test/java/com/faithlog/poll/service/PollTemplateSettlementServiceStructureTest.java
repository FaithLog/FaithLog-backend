package com.faithlog.poll.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class PollTemplateSettlementServiceStructureTest {

	private static final Path POLL_SERVICE_ROOT = Path.of("src/main/java/com/faithlog/poll/service");
	private static final Path BATCH_SERVICE_ROOT = Path.of("src/main/java/com/faithlog/batch/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void pollTemplateCommandsAndQueriesOwnTheirTransactionsDirectly() {
		Map<String, List<TransactionalUseCase>> responsibilities = Map.of(
			"PollTemplateCommandService.java", List.of(
				write("createTemplate"),
				write("updateTemplate"),
				write("deactivateTemplate")
			),
			"PollTemplateQueryService.java", List.of(
				readOnly("listTemplates"),
				readOnly("getTemplate")
			)
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = POLL_SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(useCase -> assertTrue(
				useCase.pattern().matcher(content).find(),
				entry.getKey() + "의 " + useCase.method() + "가 기존 트랜잭션 경계를 직접 가져야 합니다."
			));
		}));
	}

	@Test
	void pollTemplateControllerUsesDedicatedServicesAndFacadeIsThin() {
		String controller = read(MAIN_ROOT.resolve("poll/controller/AdminPollTemplateController.java"));
		String facade = read(POLL_SERVICE_ROOT.resolve("PollTemplateService.java"));

		assertAll(
			() -> assertTrue(controller.contains("PollTemplateCommandService")),
			() -> assertTrue(controller.contains("PollTemplateQueryService")),
			() -> assertFalse(controller.contains("PollTemplateService")),
			() -> assertFalse(facade.contains("Repository")),
			() -> assertFalse(facade.contains("@Transactional")),
			() -> assertFalse(facade.contains("BusinessException")),
			() -> assertFalse(facade.contains("ErrorCode")),
			() -> assertTrue(facade.contains("PollTemplateCommandService")),
			() -> assertTrue(facade.contains("PollTemplateQueryService"))
		);
	}

	@Test
	void pollTemplateOptionSnapshotAssemblyAndPersistenceHaveOneSupportBoundary() {
		Path supportSource = POLL_SERVICE_ROOT.resolve("PollTemplateOptionSupport.java");
		assertTrue(Files.exists(supportSource), "PollTemplateOptionSupport.java가 필요합니다.");
		String support = read(supportSource);
		String command = read(POLL_SERVICE_ROOT.resolve("PollTemplateCommandService.java"));
		String query = read(POLL_SERVICE_ROOT.resolve("PollTemplateQueryService.java"));

		assertAll(
			() -> assertTrue(support.contains("PollOptionSnapshotResolver")),
			() -> assertTrue(support.contains("PollTemplateOptionRepository")),
			() -> assertTrue(command.contains("PollTemplateOptionSupport")),
			() -> assertTrue(query.contains("PollTemplateOptionSupport")),
			() -> assertFalse(command.contains("PollTemplateOptionRepository")),
			() -> assertFalse(query.contains("PollTemplateOptionRepository"))
		);
	}

	@Test
	void scheduledPollCreationOwnsTemplateCopyOrchestrationAndFacadeIsThin() {
		String automation = read(BATCH_SERVICE_ROOT.resolve("PollAutomationService.java"));
		String creation = read(BATCH_SERVICE_ROOT.resolve("ScheduledPollCreationService.java"));
		Path factorySource = BATCH_SERVICE_ROOT.resolve("ScheduledPollFactory.java");

		assertAll(
			() -> assertTrue(Files.exists(factorySource), "ScheduledPollFactory.java가 필요합니다."),
			() -> assertTrue(creation.contains("ScheduledPollFactory")),
			() -> assertTrue(creation.contains("TransactionTemplate")),
			() -> assertTrue(creation.contains("NotificationLockService")),
			() -> assertFalse(creation.contains("PollTemplateOptionRepository")),
			() -> assertFalse(creation.contains("PollOptionRepository")),
			() -> assertFalse(creation.contains("Poll.create(")),
			() -> assertFalse(creation.contains("PollOption.create(")),
			() -> assertTrue(automation.contains("ScheduledPollCreationService")),
			() -> assertFalse(automation.contains("Repository")),
			() -> assertFalse(automation.contains("TransactionTemplate")),
			() -> assertFalse(automation.contains("NotificationLockService"))
		);
	}

	@Test
	void coffeeSettlementCommandOwnsTransactionAndFacadeIsThin() {
		Path commandSource = POLL_SERVICE_ROOT.resolve("CoffeePollSettlementCommandService.java");
		Path supportSource = POLL_SERVICE_ROOT.resolve("CoffeePollSettlementSupport.java");
		assertTrue(Files.exists(commandSource), "CoffeePollSettlementCommandService.java가 필요합니다.");
		assertTrue(Files.exists(supportSource), "CoffeePollSettlementSupport.java가 필요합니다.");
		String command = read(commandSource);
		String support = read(supportSource);
		String facade = read(POLL_SERVICE_ROOT.resolve("CoffeePollSettlementService.java"));

		assertAll(
			() -> assertTrue(write("settleClosedCoffeePoll").pattern().matcher(command).find()),
			() -> assertTrue(command.contains("CoffeePollSettlementSupport")),
			() -> assertTrue(command.contains("CoffeePollChargePort")),
			() -> assertTrue(support.contains("PollRepository")),
			() -> assertTrue(support.contains("PollResponseRepository")),
			() -> assertTrue(support.contains("PollResponseOptionRepository")),
			() -> assertTrue(support.contains("PollOptionRepository")),
			() -> assertFalse(facade.contains("Repository")),
			() -> assertFalse(facade.contains("@Transactional")),
			() -> assertFalse(facade.contains("BusinessException")),
			() -> assertFalse(facade.contains("ErrorCode")),
			() -> assertTrue(facade.contains("CoffeePollSettlementCommandService"))
		);
	}

	@Test
	void dedicatedServicesDoNotDependOnCompatibilityFacadesOrEachOther() {
		Map<Path, List<String>> forbiddenDependencies = Map.of(
			POLL_SERVICE_ROOT.resolve("PollTemplateCommandService.java"),
			List.of("PollTemplateService", "PollTemplateQueryService", "CoffeePollSettlementService"),
			POLL_SERVICE_ROOT.resolve("PollTemplateQueryService.java"),
			List.of("PollTemplateService", "PollTemplateCommandService", "CoffeePollSettlementService"),
			POLL_SERVICE_ROOT.resolve("CoffeePollSettlementCommandService.java"),
			List.of("CoffeePollSettlementService", "PollTemplateCommandService", "PollTemplateQueryService")
		);

		assertAll(forbiddenDependencies.entrySet().stream().map(entry -> () -> {
			String content = read(entry.getKey());
			entry.getValue().forEach(forbidden -> assertFalse(
				content.contains(forbidden),
				entry.getKey().getFileName() + "가 " + forbidden + "에 의존하면 안 됩니다."
			));
		}));
	}

	private TransactionalUseCase write(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\s+public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\("
		));
	}

	private TransactionalUseCase readOnly(String method) {
		return new TransactionalUseCase(method, Pattern.compile(
			"@Transactional\\(readOnly\\s*=\\s*true\\)\\s+public\\s+[^\\n{]+\\s+"
				+ Pattern.quote(method)
				+ "\\s*\\("
		));
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}

	private record TransactionalUseCase(String method, Pattern pattern) {
	}
}
