package com.faithlog.user.infrastructure.adapter;

import com.faithlog.global.security.AccessTokenVersionChecker;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.springframework.stereotype.Component;

@Component
public class UserAccessTokenVersionChecker implements AccessTokenVersionChecker {

	private final UserRepository userRepository;

	public UserAccessTokenVersionChecker(UserRepository userRepository) {
		this.userRepository = userRepository;
	}

	@Override
	public boolean matchesCurrentVersion(Long userId, long tokenVersion) {
		return userRepository.findById(userId)
			.filter(user -> user.isActive() && user.tokenVersion() == tokenVersion)
			.isPresent();
	}
}
