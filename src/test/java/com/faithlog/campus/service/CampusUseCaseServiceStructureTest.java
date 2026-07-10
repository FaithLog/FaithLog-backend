package com.faithlog.campus.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class CampusUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/campus/service");

	@Test
	void campusUseCasesAreSeparatedIntoCohesiveServices() {
		Map<String, List<String>> responsibilities = Map.of(
			"CampusCreationService.java", List.of("createCampus"),
			"CampusJoinService.java", List.of("joinCampus"),
			"CampusQueryService.java", List.of("getMyCampuses", "getCampus"),
			"CampusUpdateService.java", List.of("updateCampus"),
			"CampusMemberManagementService.java", List.of("getCampusMembers", "deleteCampusMember", "changeCampusRole"),
			"CampusDutyAssignmentService.java", List.of(
				"getDutyAssignments",
				"getMyCoffeeDutyAssignment",
				"assignCoffeeDuty",
				"revokeCoffeeDuty"
			)
		);

		assertAll(responsibilities.entrySet().stream().map(entry -> () -> {
			Path source = SERVICE_ROOT.resolve(entry.getKey());
			assertTrue(Files.exists(source), entry.getKey() + "가 필요합니다.");
			String content = read(source);
			entry.getValue().forEach(method -> assertTrue(
				content.contains(" " + method + "("),
				entry.getKey() + "가 " + method + " 책임을 가져야 합니다."
			));
		}));
	}

	@Test
	void compatibilityFacadeDoesNotOwnRepositoriesOrTransactions() throws IOException {
		String content = Files.readString(SERVICE_ROOT.resolve("CampusService.java"));

		assertFalse(content.contains("RepositoryPort"));
		assertFalse(content.contains("@Transactional"));
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
