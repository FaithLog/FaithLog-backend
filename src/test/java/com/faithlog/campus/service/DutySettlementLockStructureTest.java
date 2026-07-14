package com.faithlog.campus.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class DutySettlementLockStructureTest {

	private static final Path MAIN = Path.of("src/main/java/com/faithlog");

	@Test
	void revoke_and_coffee_meal_settlement_share_pessimistic_duty_assignment_lookup() throws IOException {
		String repository = read("campus/infrastructure/repository/CampusDutyAssignmentRepository.java");
		String dutyService = read("campus/service/CampusDutyAssignmentService.java");
		String coffeeSettlement = read("poll/service/CoffeePollSettlementSupport.java");
		String mealAccess = read("campus/service/MealDutyAccessService.java");
		String mealSettlement = read("poll/service/MealPollSettlementService.java");

		assertThat(repository).contains(
			"@Lock(LockModeType.PESSIMISTIC_WRITE)",
			"findActiveByCampusIdAndDutyTypeAndUserIdForUpdate"
		);
		assertThat(dutyService).contains("findActiveByCampusIdAndDutyTypeAndUserIdForUpdate");
		assertThat(coffeeSettlement).contains("findActiveByCampusIdAndDutyTypeAndUserIdForUpdate");
		assertThat(mealAccess).contains("requireActiveMealDutyForUpdate");
		assertThat(mealSettlement).contains("requireActiveMealDutyForUpdate");
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(MAIN.resolve(relativePath));
	}
}
