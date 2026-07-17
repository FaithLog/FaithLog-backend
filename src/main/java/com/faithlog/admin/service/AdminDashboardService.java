package com.faithlog.admin.service;

import com.faithlog.admin.service.result.AdminDashboardSummaryResult;
import java.time.LocalDate;
import org.springframework.stereotype.Service;

@Service
public class AdminDashboardService {

	private final AdminDashboardQueryService adminDashboardQueryService;

	public AdminDashboardService(AdminDashboardQueryService adminDashboardQueryService) {
		this.adminDashboardQueryService = adminDashboardQueryService;
	}

	public AdminDashboardSummaryResult getSummary(Long campusId, Long requesterId, LocalDate weekStartDate) {
		return adminDashboardQueryService.getSummary(campusId, requesterId, weekStartDate);
	}
}
