package com.faithlog.notification.infrastructure.jpa;

import com.faithlog.notification.domain.UserFcmToken;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface UserFcmTokenRepository extends JpaRepository<UserFcmToken, Long> {

	Optional<UserFcmToken> findByIdAndUserId(Long id, Long userId);

	Optional<UserFcmToken> findByUserIdAndClientInstanceIdAndTokenAndIsActiveTrue(
		Long userId,
		String clientInstanceId,
		String token
	);

	List<UserFcmToken> findByUserIdAndClientInstanceIdAndIsActiveTrue(Long userId, String clientInstanceId);

	List<UserFcmToken> findByUserIdAndTokenAndIsActiveTrue(Long userId, String token);

	List<UserFcmToken> findByTokenAndIsActiveTrue(String token);

	@Query("""
		select token
		from UserFcmToken token
		where token.isActive = true
			and (token.lastSeenAt < :staleThreshold or token.lastRefreshedAt < :staleThreshold)
		order by token.id asc
		""")
	List<UserFcmToken> findActiveStaleTokens(Instant staleThreshold);

	@Query("""
		select token
		from UserFcmToken token
		where token.userId = :userId
			and token.isActive = true
			and token.lastSeenAt >= :staleThreshold
			and token.lastRefreshedAt >= :staleThreshold
		order by token.id asc
		""")
	List<UserFcmToken> findActiveSendableTokens(Long userId, Instant staleThreshold);

	default List<UserFcmToken> findActiveSendableTokens(Long userId) {
		return findActiveSendableTokens(userId, Instant.now().minus(java.time.Duration.ofDays(90)));
	}
}
