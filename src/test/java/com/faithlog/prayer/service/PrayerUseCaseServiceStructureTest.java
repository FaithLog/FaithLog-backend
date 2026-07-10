package com.faithlog.prayer.service;

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

class PrayerUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/prayer/service");
	private static final Path MAIN_ROOT = Path.of("src/main/java/com/faithlog");

	@Test
	void prayerUseCasesAreSeparatedIntoCohesiveTransactionalServices() {
		Map<String, List<TransactionalUseCase>> responsibilities = Map.of(
			"PrayerSeasonCommandService.java", List.of(write("createSeason"), write("closeSeason")),
			"PrayerSeasonQueryService.java", List.of(readOnly("getCurrentSeason")),
			"PrayerGroupCommandService.java", List.of(
				write("createGroup"),
				write("updateGroup"),
				write("replaceGroupMembers")
			),
			"PrayerGroupQueryService.java", List.of(
				readOnly("getSeasonGroups"),
				readOnly("getAssignableMembers")
			),
			"PrayerWeekBoardQueryService.java", List.of(readOnly("getWeeklyBoard")),
			"AdminPrayerSubmissionCommandService.java", List.of(write("saveSubmissions")),
			"MyPrayerSubmissionCommandService.java", List.of(write("saveMySubmission"))
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(useCase -> assertTrue(
				useCase.pattern().matcher(content).find(),
				entry.getKey() + "의 " + useCase.method() + "가 기존 트랜잭션 경계를 직접 가져야 합니다."
			));
		}));
	}

	@Test
	void eachDedicatedServiceOwnsOnlyItsApprovedPublicUseCases() {
		Map<String, List<String>> approvedMethods = Map.of(
			"PrayerSeasonCommandService.java", List.of("createSeason", "closeSeason"),
			"PrayerSeasonQueryService.java", List.of("getCurrentSeason"),
			"PrayerGroupCommandService.java", List.of("createGroup", "updateGroup", "replaceGroupMembers"),
			"PrayerGroupQueryService.java", List.of("getSeasonGroups", "getAssignableMembers"),
			"PrayerWeekBoardQueryService.java", List.of("getWeeklyBoard"),
			"AdminPrayerSubmissionCommandService.java", List.of("saveSubmissions"),
			"MyPrayerSubmissionCommandService.java", List.of("saveMySubmission")
		);

		List<String> allMethods = approvedMethods.values().stream().flatMap(List::stream).distinct().toList();
		assertAll(approvedMethods.entrySet().stream().map(entry -> () -> {
			String content = read(SERVICE_ROOT.resolve(entry.getKey()));
			allMethods.stream()
				.filter(method -> !entry.getValue().contains(method))
				.forEach(method -> assertFalse(
					publicMethod(method).matcher(content).find(),
					entry.getKey() + "가 다른 유스케이스 " + method + "를 소유하면 안 됩니다."
				));
		}));
	}

	@Test
	void controllersUseDedicatedPrayerServices() {
		String adminController = read(MAIN_ROOT.resolve("prayer/controller/AdminPrayerController.java"));
		String prayerController = read(MAIN_ROOT.resolve("prayer/controller/PrayerController.java"));

		assertAll(
			() -> assertTrue(adminController.contains("PrayerSeasonCommandService")),
			() -> assertTrue(adminController.contains("PrayerSeasonQueryService")),
			() -> assertTrue(adminController.contains("PrayerGroupCommandService")),
			() -> assertTrue(adminController.contains("PrayerGroupQueryService")),
			() -> assertFalse(adminController.contains("PrayerService")),
			() -> assertTrue(prayerController.contains("PrayerWeekBoardQueryService")),
			() -> assertTrue(prayerController.contains("AdminPrayerSubmissionCommandService")),
			() -> assertTrue(prayerController.contains("MyPrayerSubmissionCommandService")),
			() -> assertFalse(prayerController.contains("PrayerService"))
		);
	}

	@Test
	void compatibilityFacadeDoesNotOwnRepositoriesTransactionsOrBusinessRules() {
		String facade = read(SERVICE_ROOT.resolve("PrayerService.java"));

		assertAll(
			() -> assertFalse(facade.contains("Repository")),
			() -> assertFalse(facade.contains("@Transactional")),
			() -> assertFalse(facade.contains("BusinessException")),
			() -> assertFalse(facade.contains("ErrorCode")),
			() -> assertFalse(facade.contains("PrayerSeason.create(")),
			() -> assertFalse(facade.contains("PrayerGroup.create(")),
			() -> assertFalse(facade.contains("PrayerWeek.create(")),
			() -> assertFalse(facade.contains("PrayerSubmission.create(")),
			() -> assertTrue(facade.contains("PrayerSeasonCommandService")),
			() -> assertTrue(facade.contains("PrayerSeasonQueryService")),
			() -> assertTrue(facade.contains("PrayerGroupCommandService")),
			() -> assertTrue(facade.contains("PrayerGroupQueryService")),
			() -> assertTrue(facade.contains("PrayerWeekBoardQueryService")),
			() -> assertTrue(facade.contains("AdminPrayerSubmissionCommandService")),
			() -> assertTrue(facade.contains("MyPrayerSubmissionCommandService"))
		);
	}

	@Test
	void dedicatedServicesDoNotFormDependencyCyclesOrUseCompatibilityFacade() {
		List<String> services = List.of(
			"PrayerSeasonCommandService",
			"PrayerSeasonQueryService",
			"PrayerGroupCommandService",
			"PrayerGroupQueryService",
			"PrayerWeekBoardQueryService",
			"AdminPrayerSubmissionCommandService",
			"MyPrayerSubmissionCommandService"
		);

		assertAll(services.stream().map(service -> () -> {
			String content = read(SERVICE_ROOT.resolve(service + ".java"));
			assertFalse(content.contains("PrayerService"), service + "가 PrayerService에 의존하면 안 됩니다.");
			services.stream()
				.filter(other -> !other.equals(service))
				.forEach(other -> assertFalse(
					content.contains(other),
					service + "가 다른 전용 서비스 " + other + "에 의존하면 안 됩니다."
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

	private Pattern publicMethod(String method) {
		return Pattern.compile("public\\s+[^\\n{]+\\s+" + Pattern.quote(method) + "\\s*\\(");
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
