package com.faithlog.announcement.service;

import com.faithlog.announcement.service.policy.AnnouncementAccessPolicy;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.announcement.service.result.AnnouncementCategoryResult;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class AnnouncementCategoryQueryService {

	private final AnnouncementCategoryRepositoryPort categoryRepository;
	private final AnnouncementAccessPolicy accessPolicy;

	public AnnouncementCategoryQueryService(
		AnnouncementCategoryRepositoryPort categoryRepository,
		AnnouncementAccessPolicy accessPolicy
	) {
		this.categoryRepository = categoryRepository;
		this.accessPolicy = accessPolicy;
	}

	public List<AnnouncementCategoryResult> getCategories(Long campusId, Long requesterId) {
		accessPolicy.requireActiveMember(campusId, requesterId);
		return categoryRepository.findByCampusIdOrderByDisplayOrderAscNameAscIdAsc(campusId)
			.stream()
			.map(AnnouncementCategoryResult::from)
			.toList();
	}
}
