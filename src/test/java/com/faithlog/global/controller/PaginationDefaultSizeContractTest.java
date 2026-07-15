package com.faithlog.global.controller;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

class PaginationDefaultSizeContractTest {

	private static final Path CONTROLLER_ROOT = Path.of("src/main/java/com/faithlog");
	@Test
	void adminEndpointsDefaultToTwentyAndMemberOrDutyEndpointsDefaultToTen() {
		assertDefaultSize("admin/controller/AdminManagementController.java", 20, 2);
		assertDefaultSize("billing/controller/AdminBillingController.java", 20, 3);
		assertDefaultSize("notification/controller/AdminNotificationController.java", 20, 1);
		assertDefaultSize("billing/controller/BillingController.java", 10, 1);
		assertDefaultSize("billing/controller/MealBillingController.java", 10, 1);
		assertDefaultSize("poll/controller/MealPollController.java", 10, 1);
		assertThat(PageSortRequestValidator.DEFAULT_SIZE).isEqualTo(20);
		assertThat(PageSortRequestValidator.MAX_SIZE).isEqualTo(100);
	}

	@Test
	void archivedDataIsAnExplicitOptInOnChargeAndMealPollLists() {
		String billingControllers = List.of(
			"billing/controller/AdminBillingController.java",
			"billing/controller/BillingController.java",
			"billing/controller/MealBillingController.java"
		).stream().map(CONTROLLER_ROOT::resolve).map(PaginationDefaultSizeContractTest::read)
			.reduce("", String::concat);
		String mealPollController = read(CONTROLLER_ROOT.resolve("poll/controller/MealPollController.java"));

		assertThat(occurrences(billingControllers, "defaultValue = \"false\") boolean includeArchived"))
			.isEqualTo(5);
		assertThat(occurrences(mealPollController, "defaultValue = \"false\") boolean includeArchived"))
			.isEqualTo(1);
	}

	private static void assertDefaultSize(String relativePath, int expectedSize, long expectedCount) {
		String source = read(CONTROLLER_ROOT.resolve(relativePath));
		assertThat(occurrences(source, "defaultValue = \"" + expectedSize + "\") int size"))
			.as(relativePath)
			.isEqualTo(expectedCount);
	}

	private static long occurrences(String source, String marker) {
		return source.lines().filter(line -> line.contains(marker)).count();
	}

	private static String read(Path path) {
		try {
			return Files.readString(path);
		} catch (IOException exception) {
			throw new IllegalStateException(path + " 파일을 읽지 못했습니다.", exception);
		}
	}
}
