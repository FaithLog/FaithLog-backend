package com.faithlog.media.infrastructure.r2;

import com.faithlog.global.observability.ExternalService;
import com.faithlog.global.observability.OperationalEventPort;
import com.faithlog.media.service.port.MediaObjectStoragePort;
import java.net.URI;
import java.time.Clock;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import org.springframework.http.ContentDisposition;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.core.sync.ResponseTransformer;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.GetObjectPresignRequest;
import software.amazon.awssdk.services.s3.presigner.model.PutObjectPresignRequest;

@Component
@ConditionalOnProperty(prefix = "faithlog.media.r2", name = "enabled", havingValue = "true")
public class R2MediaObjectStorageAdapter implements MediaObjectStoragePort {

	private final R2MediaStorageProperties properties;
	private final S3Client client;
	private final S3Presigner presigner;
	private final Clock clock;
	private final OperationalEventPort operationalEvents;

	public R2MediaObjectStorageAdapter(
		R2MediaStorageProperties properties,
		S3Client client,
		S3Presigner presigner,
		Clock clock,
		OperationalEventPort operationalEvents
	) {
		this.properties = properties;
		this.client = client;
		this.presigner = presigner;
		this.clock = clock;
		this.operationalEvents = operationalEvents;
	}

	@Override
	public PresignedUpload presignUpload(String objectKey, String contentType, long byteSize) {
		return observeStorageFailure(() -> {
			PutObjectRequest request = PutObjectRequest.builder()
				.bucket(properties.bucket()).key(objectKey).contentType(contentType).contentLength(byteSize).build();
			var presigned = presigner.presignPutObject(PutObjectPresignRequest.builder()
				.signatureDuration(properties.uploadUrlTtl()).putObjectRequest(request).build());
			var headers = new LinkedHashMap<String, String>();
			presigned.signedHeaders().forEach((name, values) -> {
				if (!name.equalsIgnoreCase("host")) {
					headers.put(name, String.join(",", values));
				}
			});
			return new PresignedUpload(
				URI.create(presigned.url().toString()), headers, clock.instant().plus(properties.uploadUrlTtl()));
		});
	}

	@Override
	public StoredObject getObject(String objectKey, long maximumBytes) {
		return observeStorageFailure(() -> {
			var metadata = client.headObject(HeadObjectRequest.builder()
				.bucket(properties.bucket()).key(objectKey).build());
			if (metadata.contentLength() == null || metadata.contentLength() < 1 || metadata.contentLength() > maximumBytes) {
				throw new IllegalArgumentException("stored object size is invalid");
			}
			var object = client.getObject(
				GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build(), ResponseTransformer.toBytes());
			if (object.asByteArray().length != metadata.contentLength()) {
				throw new IllegalArgumentException("stored object changed while reading");
			}
			return new StoredObject(metadata.contentType(), object.asByteArray());
		});
	}

	@Override
	public void putObject(String objectKey, String contentType, byte[] content) {
		observeStorageFailure(() -> client.putObject(PutObjectRequest.builder()
				.bucket(properties.bucket()).key(objectKey).contentType(contentType).contentLength((long) content.length).build(),
			RequestBody.fromBytes(content)));
	}

	@Override
	public void deleteObject(String objectKey) {
		observeStorageFailure(() -> client.deleteObject(
			DeleteObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build()));
	}

	@Override
	public URI presignDownload(String objectKey) {
		return observeStorageFailure(() -> {
			var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(properties.downloadUrlTtl())
				.getObjectRequest(GetObjectRequest.builder().bucket(properties.bucket()).key(objectKey).build())
				.build());
			return URI.create(presigned.url().toString());
		});
	}

	@Override
	public URI presignDownload(String objectKey, String fileName, String contentType) {
		return observeStorageFailure(() -> {
			String disposition = ContentDisposition.attachment()
				.filename(fileName, StandardCharsets.UTF_8)
				.build()
				.toString();
			var request = GetObjectRequest.builder()
				.bucket(properties.bucket())
				.key(objectKey)
				.responseContentType(contentType)
				.responseContentDisposition(disposition)
				.build();
			var presigned = presigner.presignGetObject(GetObjectPresignRequest.builder()
				.signatureDuration(properties.downloadUrlTtl())
				.getObjectRequest(request)
				.build());
			return URI.create(presigned.url().toString());
		});
	}

	private <T> T observeStorageFailure(StorageOperation<T> operation) {
		try {
			return operation.run();
		} catch (SdkException exception) {
			operationalEvents.externalServiceFailure(ExternalService.CLOUDFLARE_R2);
			throw exception;
		}
	}

	@FunctionalInterface
	private interface StorageOperation<T> {
		T run();
	}
}
