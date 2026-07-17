package com.faithlog.poll.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class SettlementBatchStructureTest {

	private static final Path MAIN = Path.of("src/main/java/com/faithlog");

	@Test
	void coffee_and_meal_settlement_delegate_one_collection_to_batch_charge_creation() throws IOException {
		String coffee = Files.readString(MAIN.resolve("poll/service/CoffeePollSettlementCommandService.java"));
		String adapter = Files.readString(MAIN.resolve("poll/infrastructure/adapter/BillingCoffeePollChargeAdapter.java"));
		String meal = Files.readString(MAIN.resolve("poll/service/MealPollSettlementService.java"));

		assertThat(coffee).contains("createOrUpdateCoffeeCharges(");
		assertThat(coffee).doesNotContain("createOrUpdateCoffeeCharge(settlementSupport");
		assertThat(adapter).contains("createOrUpdateCoffeeCharges(");
		assertThat(meal).contains("createMealCharges(");
		assertThat(meal).doesNotContain("createMealCharge(new CreateMealChargeCommand");
	}

	@Test
	void batch_repository_contract_uses_one_locked_source_set_query_and_one_collection_save() throws IOException {
		String port = Files.readString(MAIN.resolve("billing/service/port/ChargeItemRepositoryPort.java"));
		String repository = Files.readString(MAIN.resolve("billing/infrastructure/repository/ChargeItemRepository.java"));

		assertThat(port).contains("findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(");
		assertThat(port).contains("saveAllCharges(");
		assertThat(repository).contains("@Lock(LockModeType.PESSIMISTIC_WRITE)");
		assertThat(repository).contains("findByCampusIdAndPaymentCategoryAndSourceTypeAndSourceIdInForUpdate(");
	}
}
