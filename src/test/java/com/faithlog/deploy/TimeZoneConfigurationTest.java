package com.faithlog.deploy;

import static org.assertj.core.api.Assertions.assertThat;

import com.faithlog.global.config.ApplicationTimeZoneConfig;
import java.time.ZoneId;
import java.util.TimeZone;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;

class TimeZoneConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withInitializer(new ConfigDataApplicationContextInitializer());

	@Test
	void application_configures_asia_seoul_for_json_hibernate_and_database_session() {
		contextRunner.run(context -> {
			assertThat(context.getEnvironment().getProperty("app.time-zone")).isEqualTo("Asia/Seoul");
			assertThat(context.getEnvironment().getProperty("spring.jackson.time-zone")).isEqualTo("Asia/Seoul");
			assertThat(context.getEnvironment().getProperty("spring.jpa.properties.hibernate.jdbc.time_zone"))
				.isEqualTo("Asia/Seoul");
			assertThat(context.getEnvironment().getProperty("spring.datasource.hikari.connection-init-sql"))
				.isEqualTo("SET TIME ZONE 'Asia/Seoul'");
		});
	}

	@Test
	void application_sets_jvm_default_timezone_from_configured_timezone() {
		TimeZone previous = TimeZone.getDefault();
		try {
			TimeZone.setDefault(TimeZone.getTimeZone("UTC"));

			new ApplicationTimeZoneConfig("Asia/Seoul").configureDefaultTimeZone();

			assertThat(TimeZone.getDefault().toZoneId()).isEqualTo(ZoneId.of("Asia/Seoul"));
		} finally {
			TimeZone.setDefault(previous);
		}
	}
}
