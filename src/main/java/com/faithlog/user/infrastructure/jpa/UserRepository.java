package com.faithlog.user.infrastructure.jpa;

import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.campus.application.port.CampusUserLookupResult;
import com.faithlog.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long>, CampusUserLookupPort {

	@Override
	default Optional<CampusUserLookupResult> findCampusUserById(Long userId) {
		return findById(userId)
			.map(user -> new CampusUserLookupResult(user.id(), user.role().name(), user.isActive()));
	}

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
