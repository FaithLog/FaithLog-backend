package com.faithlog.support;

import com.faithlog.user.service.port.AccessTokenBlacklistStore;
import com.faithlog.user.service.port.RefreshTokenStore;
import com.faithlog.user.support.InMemoryAccessTokenBlacklistStore;
import com.faithlog.user.support.InMemoryRefreshTokenStore;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

@Configuration
@Profile("test")
public class AuthTokenStoreTestConfig {

	@Bean
	RefreshTokenStore testRefreshTokenStore() {
		return new InMemoryRefreshTokenStore();
	}

	@Bean
	AccessTokenBlacklistStore testAccessTokenBlacklistStore() {
		return new InMemoryAccessTokenBlacklistStore();
	}
}
