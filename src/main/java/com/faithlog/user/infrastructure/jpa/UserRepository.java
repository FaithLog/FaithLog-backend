package com.faithlog.user.infrastructure.jpa;

import com.faithlog.campus.application.port.CampusUserLookupPort;
import com.faithlog.user.domain.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long>, CampusUserLookupPort {

	@Override
	default Optional<User> findCampusUserById(Long userId) {
		return findById(userId);
	}

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
