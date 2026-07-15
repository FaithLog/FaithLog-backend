package com.faithlog.global.exception;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class GlobalExceptionHandlerScopeTest {

	@Test
	void stale_only_validation_does_not_change_every_argument_type_mismatch_contract() throws Exception {
		String globalHandler = Files.readString(Path.of(
			"src/main/java/com/faithlog/global/exception/GlobalExceptionHandler.java"));
		String adminCampusController = Files.readString(Path.of(
			"src/main/java/com/faithlog/campus/controller/AdminCampusController.java"));

		assertThat(globalHandler).doesNotContain("MethodArgumentTypeMismatchException");
		assertThat(adminCampusController).contains(
			"@RequestParam(defaultValue = \"false\") String staleOnly",
			"parseStaleOnly(staleOnly)"
		);
	}
}
