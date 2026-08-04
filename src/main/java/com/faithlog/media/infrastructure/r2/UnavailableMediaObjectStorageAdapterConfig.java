package com.faithlog.media.infrastructure.r2;

import com.faithlog.media.service.port.MediaObjectStoragePort;
import java.net.URI;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
class UnavailableMediaObjectStorageAdapterConfig {

	@Bean
	@ConditionalOnMissingBean(MediaObjectStoragePort.class)
	MediaObjectStoragePort unavailableMediaObjectStoragePort() {
		return new MediaObjectStoragePort() {
			private IllegalStateException unavailable() { return new IllegalStateException("Media storage is unavailable"); }
			@Override public PresignedUpload presignUpload(String key, String type, long size) { throw unavailable(); }
			@Override public StoredObject getObject(String key, long max) { throw unavailable(); }
			@Override public void putObject(String key, String type, byte[] content) { throw unavailable(); }
			@Override public void deleteObject(String key) { throw unavailable(); }
			@Override public URI presignDownload(String key) { throw unavailable(); }
		};
	}
}
