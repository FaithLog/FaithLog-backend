package com.faithlog.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class TestDatabaseIsolationConfigTest {

	private static final Path TEST_CONFIG = Path.of("src/test/resources/application-test.yml");

	@Test
	void spring_test_contexts_use_unique_h2_database_names() throws IOException {
		String testConfig = Files.readString(TEST_CONFIG);

		assertThat(testConfig)
			.contains("jdbc:h2:mem:faithlog-test-${random.uuid}")
			.doesNotContain("url: jdbc:h2:mem:faithlog-test;");
	}
}
