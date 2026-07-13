package com.faithlog.devotion.service;

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

class DevotionUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/devotion/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void devotionUseCasesAreSeparatedIntoCohesiveTransactionalServices() {
		Map<String, List<TransactionalUseCase>> responsibilities = Map.of(
			"DailyDevotionCommandService.java", List.of(write("updateDailyCheck")),
			"WeeklyDevotionCommandService.java", List.of(write("updateWeeklyCheck")),
			"MyWeeklyDevotionQueryService.java", List.of(readOnly("getMyWeeklyCheck")),
			"MissingDevotionMemberQueryService.java", List.of(readOnly("getMissingMembers")),
			"AdminWeeklyDevotionQueryService.java", List.of(readOnly("getWeeklyMembers")),
			"DevotionMonthlySummaryQueryService.java", List.of(readOnly("getMyMonthlySummary")),
			"PenaltyRuleCommandService.java", List.of(
				write("createPenaltyRule"),
				write("updatePenaltyRule")
			),
			"PenaltyRuleQueryService.java", List.of(readOnly("listPenaltyRules"))
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(useCase -> assertTrue(
				useCase.pattern().matcher(content).find(),
				entry.getKey() + "의 " + useCase.method() + "가 직접 트랜잭션 경계를 가져야 합니다."
			));
		}));
	}

	@Test
	void eachDedicatedServiceOwnsOnlyItsApprovedPublicUseCases() {
		Map<String, List<String>> forbiddenMethods = Map.of(
			"DailyDevotionCommandService.java", List.of("updateWeeklyCheck(", "getMyWeeklyCheck(", "getMissingMembers("),
			"WeeklyDevotionCommandService.java", List.of("updateDailyCheck(", "getMyWeeklyCheck(", "getMissingMembers("),
			"MyWeeklyDevotionQueryService.java", List.of("updateDailyCheck(", "updateWeeklyCheck(", "getMissingMembers("),
			"MissingDevotionMemberQueryService.java", List.of("updateDailyCheck(", "updateWeeklyCheck(", "getMyWeeklyCheck("),
			"PenaltyRuleCommandService.java", List.of("listPenaltyRules("),
			"PenaltyRuleQueryService.java", List.of("createPenaltyRule(", "updatePenaltyRule(")
		);

		assertAll(forbiddenMethods.entrySet().stream().map(entry -> () -> {
			String content = read(SERVICE_ROOT.resolve(entry.getKey()));
			entry.getValue().forEach(method -> assertFalse(
				content.contains(method),
				entry.getKey() + "가 다른 유스케이스 " + method + "를 소유하면 안 됩니다."
			));
		}));
	}

	@Test
	void controllersUseDedicatedDevotionAndPenaltyRuleServices() {
		String devotionController = read(MAIN_ROOT.resolve("devotion/controller/DevotionController.java"));
		String adminDevotionController = read(MAIN_ROOT.resolve("devotion/controller/AdminDevotionController.java"));
		String penaltyRuleController = read(MAIN_ROOT.resolve("devotion/controller/PenaltyRuleController.java"));
		String adminPenaltyRuleController = read(
			MAIN_ROOT.resolve("devotion/controller/AdminPenaltyRuleController.java")
		);

		assertAll(
			() -> assertTrue(devotionController.contains("DailyDevotionCommandService")),
			() -> assertTrue(devotionController.contains("dailyDevotionCommandService.updateDailyCheck(")),
			() -> assertTrue(devotionController.contains("WeeklyDevotionCommandService")),
			() -> assertTrue(devotionController.contains("weeklyDevotionCommandService.updateWeeklyCheck(")),
			() -> assertTrue(devotionController.contains("MyWeeklyDevotionQueryService")),
			() -> assertTrue(devotionController.contains("myWeeklyDevotionQueryService.getMyWeeklyCheck(")),
			() -> assertTrue(devotionController.contains("DevotionMonthlySummaryQueryService")),
			() -> assertFalse(devotionController.contains("DevotionService")),
			() -> assertTrue(adminDevotionController.contains("MissingDevotionMemberQueryService")),
			() -> assertTrue(adminDevotionController.contains("missingDevotionMemberQueryService.getMissingMembers(")),
			() -> assertTrue(adminDevotionController.contains("AdminWeeklyDevotionQueryService")),
			() -> assertTrue(adminDevotionController.contains("adminWeeklyDevotionQueryService.getWeeklyMembers(")),
			() -> assertFalse(adminDevotionController.contains("DevotionService")),
			() -> assertTrue(penaltyRuleController.contains("PenaltyRuleQueryService")),
			() -> assertTrue(penaltyRuleController.contains("penaltyRuleQueryService.listPenaltyRules(")),
			() -> assertFalse(penaltyRuleController.contains("PenaltyRuleService")),
			() -> assertTrue(adminPenaltyRuleController.contains("PenaltyRuleCommandService")),
			() -> assertTrue(adminPenaltyRuleController.contains("penaltyRuleCommandService.createPenaltyRule(")),
			() -> assertTrue(adminPenaltyRuleController.contains("penaltyRuleCommandService.updatePenaltyRule(")),
			() -> assertFalse(adminPenaltyRuleController.contains("PenaltyRuleService"))
		);
	}

	@Test
	void compatibilityFacadesDoNotOwnRepositoriesTransactionsOrBusinessRules() {
		String devotionFacade = read(SERVICE_ROOT.resolve("DevotionService.java"));
		String penaltyRuleFacade = read(SERVICE_ROOT.resolve("PenaltyRuleService.java"));

		assertAll(
			() -> assertThinFacade(devotionFacade),
			() -> assertTrue(devotionFacade.contains("DailyDevotionCommandService")),
			() -> assertTrue(devotionFacade.contains("WeeklyDevotionCommandService")),
			() -> assertTrue(devotionFacade.contains("MyWeeklyDevotionQueryService")),
			() -> assertTrue(devotionFacade.contains("MissingDevotionMemberQueryService")),
			() -> assertThinFacade(penaltyRuleFacade),
			() -> assertTrue(penaltyRuleFacade.contains("PenaltyRuleCommandService")),
			() -> assertTrue(penaltyRuleFacade.contains("PenaltyRuleQueryService"))
		);
	}

	@Test
	void dedicatedServicesDoNotDependOnEachOtherOrCompatibilityFacades() {
		List<String> dedicatedServices = List.of(
			"DailyDevotionCommandService",
			"WeeklyDevotionCommandService",
			"MyWeeklyDevotionQueryService",
			"MissingDevotionMemberQueryService",
			"PenaltyRuleCommandService",
			"PenaltyRuleQueryService"
		);

		assertAll(dedicatedServices.stream().map(service -> () -> {
			String content = read(SERVICE_ROOT.resolve(service + ".java"));
			dedicatedServices.stream()
				.filter(other -> !other.equals(service))
				.forEach(other -> assertFalse(
					content.contains(other),
					service + "가 " + other + "에 의존하면 안 됩니다."
				));
			assertFalse(content.contains("DevotionService"), service + "가 DevotionService에 의존하면 안 됩니다.");
			assertFalse(content.contains("PenaltyRuleService"), service + "가 PenaltyRuleService에 의존하면 안 됩니다.");
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

	private void assertThinFacade(String content) {
		assertFalse(content.contains("Repository"));
		assertFalse(content.contains("@Transactional"));
		assertFalse(content.contains("BusinessException"));
		assertFalse(content.contains("ErrorCode"));
		assertFalse(content.contains("CampusRolePolicy"));
		assertFalse(content.contains("DevotionFineCalculator"));
		assertFalse(content.contains("PenaltyRule.create("));
		assertFalse(content.contains("WeeklyDevotionRecord.create("));
		assertFalse(content.contains("DevotionDailyCheck.create("));
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
