package com.faithlog.admin.service;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class AdminUseCaseServiceStructureTest {

	private static final Path SERVICE_ROOT = Path.of("src/main/java/com/faithlog/admin/service");

	@Test
	void adminUseCasesAreSeparatedIntoCohesiveServices() {
		Map<String, List<String>> responsibilities = Map.of(
			"AdminUserManagementService.java", List.of("searchUsers", "getUser", "changeUserRole"),
			"AdminCampusManagementService.java", List.of("searchCampuses", "addCampusMember"),
			"AdminDashboardQueryService.java", List.of("getSummary")
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
	void compatibilityFacadesDoNotOwnRepositoriesOrTransactions() throws IOException {
		String managementFacade = Files.readString(SERVICE_ROOT.resolve("AdminManagementService.java"));
		String dashboardFacade = Files.readString(SERVICE_ROOT.resolve("AdminDashboardService.java"));

		assertAll(
			() -> assertFalse(managementFacade.contains("RepositoryPort")),
			() -> assertFalse(managementFacade.contains("@Transactional")),
			() -> assertFalse(dashboardFacade.contains("infrastructure.repository")),
			() -> assertFalse(dashboardFacade.contains("@Transactional"))
		);
	}

	private String read(Path source) {
		try {
			return Files.readString(source);
		} catch (IOException exception) {
			throw new IllegalStateException(exception);
		}
	}
}
