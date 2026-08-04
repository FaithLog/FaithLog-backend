package com.faithlog.media.infrastructure.r2;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

@Configuration
@EnableConfigurationProperties(R2MediaStorageProperties.class)
@ConditionalOnProperty(prefix = "faithlog.media.r2", name = "enabled", havingValue = "true")
class R2MediaStorageConfig {

	@Bean
	S3Client r2S3Client(R2MediaStorageProperties properties) {
		return S3Client.builder()
			.endpointOverride(properties.endpoint())
			.region(Region.of("auto"))
			.credentialsProvider(credentials(properties))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
	}

	@Bean
	S3Presigner r2S3Presigner(R2MediaStorageProperties properties) {
		return S3Presigner.builder()
			.endpointOverride(properties.endpoint())
			.region(Region.of("auto"))
			.credentialsProvider(credentials(properties))
			.serviceConfiguration(S3Configuration.builder().pathStyleAccessEnabled(true).build())
			.build();
	}

	private StaticCredentialsProvider credentials(R2MediaStorageProperties properties) {
		return StaticCredentialsProvider.create(AwsBasicCredentials.create(properties.accessKey(), properties.secretKey()));
	}
}
