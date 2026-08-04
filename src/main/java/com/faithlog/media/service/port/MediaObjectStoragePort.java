package com.faithlog.media.service.port;

import java.net.URI;
import java.time.Instant;
import java.util.Map;

public interface MediaObjectStoragePort {

	PresignedUpload presignUpload(String objectKey, String contentType, long byteSize);

	StoredObject getObject(String objectKey, long maximumBytes);

	void putObject(String objectKey, String contentType, byte[] content);

	void deleteObject(String objectKey);

	URI presignDownload(String objectKey);

	record PresignedUpload(URI url, Map<String, String> requiredHeaders, Instant expiresAt) {
		public PresignedUpload {
			requiredHeaders = Map.copyOf(requiredHeaders);
		}
	}

	record StoredObject(String contentType, byte[] content) {
		public StoredObject { content = content.clone(); }
		@Override public byte[] content() { return content.clone(); }
	}
}
