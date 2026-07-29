package com.faithlog.user.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.faithlog.user.infrastructure.email.AesGcmEmailDispatchCipher;
import com.faithlog.user.infrastructure.email.UnavailableEmailDispatchQueueAdapter;
import com.faithlog.user.infrastructure.email.UnavailableEmailDispatchStore;
import com.faithlog.user.service.port.EmailDispatchQueuePort;
import com.faithlog.user.service.port.EmailDispatchStore;
import com.google.cloud.tasks.v2.CloudTasksClient;
import java.io.IOException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("!test")
public class EmailDispatchConfiguration {

	@Bean
	@ConditionalOnExpression(
		"${faithlog.auth.email-dispatch.cloud-tasks-enabled:false} or "
			+ "${faithlog.auth.email-dispatch.worker-enabled:false}"
	)
	AesGcmEmailDispatchCipher emailDispatchCipher(
		@Value("${faithlog.auth.email-dispatch.encryption-key:}") String encryptionKey,
		ObjectMapper objectMapper
	) {
		return new AesGcmEmailDispatchCipher(encryptionKey, objectMapper);
	}

	@Bean(destroyMethod = "close")
	@ConditionalOnProperty(name = "faithlog.auth.email-dispatch.cloud-tasks-enabled", havingValue = "true")
	CloudTasksClient emailDispatchCloudTasksClient() throws IOException {
		return CloudTasksClient.create();
	}

	@Bean
	@ConditionalOnMissingBean(EmailDispatchQueuePort.class)
	EmailDispatchQueuePort unavailableEmailDispatchQueue() {
		return new UnavailableEmailDispatchQueueAdapter();
	}

	@Bean
	@ConditionalOnMissingBean(EmailDispatchStore.class)
	EmailDispatchStore unavailableEmailDispatchStore() {
		return new UnavailableEmailDispatchStore();
	}
}
