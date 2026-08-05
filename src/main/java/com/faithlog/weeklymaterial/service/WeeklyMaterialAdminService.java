package com.faithlog.weeklymaterial.service;

import com.faithlog.weeklymaterial.domain.type.WeeklyMaterialType;
import com.faithlog.weeklymaterial.service.result.WeeklyMaterialWeekResult;
import java.time.LocalDate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WeeklyMaterialAdminService {
	private final WeeklyMaterialCommandService commands;
	private final WeeklyMaterialQueryService queries;

	public WeeklyMaterialAdminService(WeeklyMaterialCommandService commands, WeeklyMaterialQueryService queries) {
		this.commands = commands;
		this.queries = queries;
	}

	@Transactional
	public WeeklyMaterialWeekResult putAndGet(Long campusId, LocalDate weekStartDate,
		WeeklyMaterialType materialType, Long mediaAssetId, Long requesterId) {
		commands.put(campusId, weekStartDate, materialType, mediaAssetId, requesterId);
		return queries.getWeekForManager(campusId, requesterId, weekStartDate);
	}
}
