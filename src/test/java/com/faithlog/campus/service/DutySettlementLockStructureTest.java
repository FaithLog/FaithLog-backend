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

	@Test
	void every_duty_gated_command_uses_the_same_pessimistic_duty_lock() throws IOException {
		String pollAccess = read("poll/service/PollAccessService.java");
		String pollCreation = read("poll/service/PollCreationCommandService.java");
		String templateCommand = read("poll/service/PollTemplateCommandService.java");
		String accountCommand = read("billing/service/PaymentAccountCommandService.java");
		String chargeStatus = read("billing/service/ChargeStatusCommandService.java");
		String reminder = read("notification/service/ChargeReminderService.java");
		String mealAccount = read("billing/service/MealPaymentAccountService.java");
		String mealPoll = read("poll/service/MealPollService.java");

		assertThat(pollAccess).contains(
			"requirePollCreatorForUpdate",
			"requireCoffeeTemplateManagerForUpdate",
			"requireCoffeePollOwnerForUpdate",
			"findActiveByCampusIdAndDutyTypeAndUserIdForUpdate"
		);
		assertThat(pollCreation).contains("requirePollCreatorForUpdate");
		assertThat(templateCommand).contains("requireCoffeeTemplateManagerForUpdate");
		assertThat(accountCommand).contains("requireActiveCoffeeDutyForUpdate");
		assertThat(chargeStatus).contains("requireActiveCoffeeDutyForUpdate");
		assertThat(reminder).contains("findActiveByCampusIdAndDutyTypeAndUserIdForUpdate");
		assertThat(mealAccount).contains("requireActiveMealDutyForUpdate");
		assertThat(mealPoll).contains("requireActiveMealDutyForUpdate");
	}

	@Test
	void manual_and_due_coffee_close_use_duty_then_poll_lock_order() throws IOException {
		String pollStatus = read("poll/service/PollStatusCommandService.java");
		String coffeeSettlement = read("poll/service/CoffeePollSettlementSupport.java");
		String dueClosure = read("batch/service/DueCoffeePollClosureService.java");

		assertBefore(pollStatus, "requireCoffeePollOwnerForUpdate", "getPollInCampusForUpdate");
		assertBefore(
			coffeeSettlement,
			"findActiveByCampusIdAndDutyTypeAndUserIdForUpdate",
			"findByIdAndCampusIdForUpdate"
		);
		assertBefore(
			dueClosure,
			"findActiveByCampusIdAndDutyTypeAndUserIdForUpdate",
			"findByIdAndCampusIdForUpdate"
		);
	}

	private void assertBefore(String source, String first, String second) {
		assertThat(source.indexOf(first))
			.as("%s 호출이 %s 호출보다 먼저여야 합니다.", first, second)
			.isGreaterThanOrEqualTo(0)
			.isLessThan(source.indexOf(second));
	}

	private String read(String relativePath) throws IOException {
		return Files.readString(MAIN.resolve(relativePath));
	}
}
