package com.faithlog.notification.infrastructure.fcm;

import com.faithlog.notification.application.port.FcmSendPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class NoOpFcmSendAdapterConfig {

	@Bean
	@ConditionalOnMissingBean(FcmSendPort.class)
	FcmSendPort fcmSendPort() {
		return command -> {
		};
	}
}
