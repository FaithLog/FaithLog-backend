package com.faithlog.campus.application.port;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface CampusUserLookupPort {

	Optional<CampusUserLookupResult> findCampusUserById(Long userId);

	default List<CampusUserLookupResult> findCampusUsersByIds(Collection<Long> userIds) {
		return userIds.stream()
			.map(this::findCampusUserById)
			.flatMap(Optional::stream)
			.toList();
	}
}
