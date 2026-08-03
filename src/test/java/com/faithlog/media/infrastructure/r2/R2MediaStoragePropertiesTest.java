package com.faithlog.media.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.time.Duration;
import org.junit.jupiter.api.Test;

class R2MediaStoragePropertiesTest {

	@Test
	void enabled_config_requires_https_endpoint_bucket_credentials_and_short_ttls() {
		assertThatThrownBy(() -> new R2MediaStorageProperties(
			true, URI.create("http://example.r2.cloudflarestorage.com"), "bucket", "access", "secret",
			Duration.ofMinutes(5), Duration.ofMinutes(5)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new R2MediaStorageProperties(
			true, URI.create("https://example.r2.cloudflarestorage.com"), "", "access", "secret",
			Duration.ofMinutes(5), Duration.ofMinutes(5)))
			.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new R2MediaStorageProperties(
			true, URI.create("https://example.r2.cloudflarestorage.com"), "bucket", "access", "secret",
			Duration.ofHours(2), Duration.ofMinutes(5)))
			.isInstanceOf(IllegalArgumentException.class);
	}
}
