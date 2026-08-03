package com.faithlog.media.infrastructure.r2;

import java.net.URI;
import java.time.Duration;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "faithlog.media.r2")
public record R2MediaStorageProperties(
	boolean enabled,
	URI endpoint,
	String bucket,
	String accessKey,
	String secretKey,
	Duration uploadUrlTtl,
	Duration downloadUrlTtl
) {
	private static final Pattern BUCKET = Pattern.compile("^[a-z0-9][a-z0-9.-]{1,61}[a-z0-9]$");
	private static final Duration MAX_URL_TTL = Duration.ofMinutes(15);

	public R2MediaStorageProperties {
		if (enabled) {
			if (endpoint == null || !"https".equalsIgnoreCase(endpoint.getScheme())
				|| endpoint.getHost() == null || !endpoint.getHost().endsWith(".r2.cloudflarestorage.com")
				|| endpoint.getRawUserInfo() != null || endpoint.getRawQuery() != null || endpoint.getRawFragment() != null) {
				throw new IllegalArgumentException("R2 endpoint must be an HTTPS Cloudflare R2 endpoint");
			}
			if (bucket == null || !BUCKET.matcher(bucket).matches()) {
				throw new IllegalArgumentException("R2 bucket is invalid");
			}
			if (accessKey == null || accessKey.isBlank() || secretKey == null || secretKey.isBlank()) {
				throw new IllegalArgumentException("R2 credentials are required");
			}
			requireShortPositiveTtl(uploadUrlTtl, "uploadUrlTtl");
			requireShortPositiveTtl(downloadUrlTtl, "downloadUrlTtl");
		}
	}

	private static void requireShortPositiveTtl(Duration value, String field) {
		if (value == null || value.isZero() || value.isNegative() || value.compareTo(MAX_URL_TTL) > 0) {
			throw new IllegalArgumentException(field + " must be between 1 second and 15 minutes");
		}
	}
}
