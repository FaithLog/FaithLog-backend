package com.faithlog.media.infrastructure.r2;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.faithlog.global.observability.ExternalService;
import com.faithlog.global.observability.OperationalEventPort;
import java.net.URI;
import java.time.Clock;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.core.exception.SdkClientException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

class R2MediaObjectStorageObservabilityTest {

	@Test
	void records_r2_sdk_failure_without_object_key_or_provider_message() {
		S3Client client = mock(S3Client.class);
		S3Presigner presigner = mock(S3Presigner.class);
		OperationalEventPort events = mock(OperationalEventPort.class);
		R2MediaStorageProperties properties = new R2MediaStorageProperties(
			true,
			URI.create("https://account.r2.cloudflarestorage.com"),
			"bucket",
			"access-key",
			"secret-key",
			Duration.ofMinutes(10),
			Duration.ofMinutes(10)
		);
		when(client.deleteObject(org.mockito.ArgumentMatchers.any(DeleteObjectRequest.class)))
			.thenThrow(SdkClientException.create("private provider response for object-key"));
		R2MediaObjectStorageAdapter adapter = new R2MediaObjectStorageAdapter(
			properties,
			client,
			presigner,
			Clock.systemUTC(),
			events
		);

		assertThatThrownBy(() -> adapter.deleteObject("private/object-key"))
			.isInstanceOf(SdkClientException.class);
		verify(events).externalServiceFailure(ExternalService.CLOUDFLARE_R2);
	}
}
