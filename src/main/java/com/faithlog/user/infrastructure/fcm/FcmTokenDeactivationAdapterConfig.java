package com.faithlog.user.infrastructure.fcm;

import com.faithlog.user.application.port.CurrentDeviceFcmTokenDeactivationPort;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class FcmTokenDeactivationAdapterConfig {

	@Bean
	@ConditionalOnMissingBean(CurrentDeviceFcmTokenDeactivationPort.class)
	public CurrentDeviceFcmTokenDeactivationPort currentDeviceFcmTokenDeactivationPort() {
		return new NoOpCurrentDeviceFcmTokenDeactivationAdapter();
	}
}
