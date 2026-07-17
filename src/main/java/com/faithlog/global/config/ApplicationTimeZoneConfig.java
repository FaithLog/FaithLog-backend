package com.faithlog.global.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.time.Clock;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Bean;

@Configuration
public class ApplicationTimeZoneConfig {

	private final String timeZone;

	public ApplicationTimeZoneConfig(@Value("${app.time-zone:Asia/Seoul}") String timeZone) {
		this.timeZone = timeZone;
	}

	@PostConstruct
	public void configureDefaultTimeZone() {
		TimeZone.setDefault(TimeZone.getTimeZone(ZoneId.of(timeZone)));
	}

	@Bean
	public Clock applicationClock() {
		return Clock.systemUTC();
	}
}
