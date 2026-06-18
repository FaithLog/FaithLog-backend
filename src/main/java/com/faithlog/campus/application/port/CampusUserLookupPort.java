package com.faithlog.campus.application.port;

import com.faithlog.user.domain.User;
import java.util.Optional;

public interface CampusUserLookupPort {

	Optional<User> findCampusUserById(Long userId);
}
