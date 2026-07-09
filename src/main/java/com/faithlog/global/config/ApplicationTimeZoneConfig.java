package com.faithlog.global.config;

import jakarta.annotation.PostConstruct;
import java.time.ZoneId;
import java.util.TimeZone;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

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
}
