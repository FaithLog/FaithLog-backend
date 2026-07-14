package com.faithlog.user.service;

import com.faithlog.campus.domain.entity.CampusMember;
import com.faithlog.user.domain.entity.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
class AccountSoftDeletionSupport {

	private static final String DELETED_USER_NAME = "탈퇴한 사용자";

	private final PasswordEncoder passwordEncoder;

	AccountSoftDeletionSupport(PasswordEncoder passwordEncoder) {
		this.passwordEncoder = passwordEncoder;
	}

	boolean passwordMatches(String rawPassword, String passwordHash) {
		return passwordEncoder.matches(rawPassword, passwordHash);
	}

	void deactivateCampusMemberships(List<CampusMember> memberships) {
		for (CampusMember member : memberships) {
			member.deactivate();
		}
	}

	void softDelete(User user, Instant deletedAt) {
		user.deleteAccount(
			anonymizedEmail(user.id()),
			DELETED_USER_NAME,
			passwordEncoder.encode(UUID.randomUUID().toString()),
			deletedAt
		);
	}

	private String anonymizedEmail(Long userId) {
		return "deleted_user_%d@deleted.faithlog.local".formatted(userId);
	}
}
