package com.faithlog.campus.application.port;

import java.util.Optional;

public interface CampusUserLookupPort {

	Optional<CampusUserLookupResult> findCampusUserById(Long userId);
}
