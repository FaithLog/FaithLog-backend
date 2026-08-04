package com.faithlog.announcement.infrastructure.adapter;

import com.faithlog.announcement.domain.entity.AnnouncementCategory;
import com.faithlog.announcement.service.port.AnnouncementCategoryRepositoryPort;
import com.faithlog.campus.service.port.CampusCreationSideEffectPort;
import org.springframework.stereotype.Component;

@Component
public class DefaultAnnouncementCategoryProvisioner implements CampusCreationSideEffectPort {

	private static final String DEFAULT_NAME = "일반";
	private static final String DEFAULT_COLOR = "#3B82F6";

	private final AnnouncementCategoryRepositoryPort categoryRepository;

	public DefaultAnnouncementCategoryProvisioner(AnnouncementCategoryRepositoryPort categoryRepository) {
		this.categoryRepository = categoryRepository;
	}

	@Override
	public void afterCampusCreated(Long campusId) {
		categoryRepository.save(AnnouncementCategory.create(campusId, DEFAULT_NAME, DEFAULT_COLOR, 0));
	}
}
