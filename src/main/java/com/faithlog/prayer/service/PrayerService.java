package com.faithlog.prayer.service;

import com.faithlog.prayer.service.command.ClosePrayerSeasonCommand;
import com.faithlog.prayer.service.command.CreatePrayerGroupCommand;
import com.faithlog.prayer.service.command.CreatePrayerSeasonCommand;
import com.faithlog.prayer.service.command.ReplacePrayerGroupMembersCommand;
import com.faithlog.prayer.service.command.SaveMyPrayerSubmissionCommand;
import com.faithlog.prayer.service.command.SavePrayerSubmissionsCommand;
import com.faithlog.prayer.service.command.UpdatePrayerGroupCommand;
import com.faithlog.prayer.service.result.PrayerAssignableMemberResult;
import com.faithlog.prayer.service.result.PrayerGroupResult;
import com.faithlog.prayer.service.result.PrayerSeasonResult;
import com.faithlog.prayer.service.result.PrayerWeekBoardResult;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class PrayerService {

	private final PrayerSeasonCommandService seasonCommandService;
	private final PrayerSeasonQueryService seasonQueryService;
	private final PrayerGroupCommandService groupCommandService;
	private final PrayerGroupQueryService groupQueryService;
	private final PrayerWeekBoardQueryService weekBoardQueryService;
	private final AdminPrayerSubmissionCommandService adminSubmissionCommandService;
	private final MyPrayerSubmissionCommandService mySubmissionCommandService;

	public PrayerService(
		PrayerSeasonCommandService seasonCommandService,
		PrayerSeasonQueryService seasonQueryService,
		PrayerGroupCommandService groupCommandService,
		PrayerGroupQueryService groupQueryService,
		PrayerWeekBoardQueryService weekBoardQueryService,
		AdminPrayerSubmissionCommandService adminSubmissionCommandService,
		MyPrayerSubmissionCommandService mySubmissionCommandService
	) {
		this.seasonCommandService = seasonCommandService;
		this.seasonQueryService = seasonQueryService;
		this.groupCommandService = groupCommandService;
		this.groupQueryService = groupQueryService;
		this.weekBoardQueryService = weekBoardQueryService;
		this.adminSubmissionCommandService = adminSubmissionCommandService;
		this.mySubmissionCommandService = mySubmissionCommandService;
	}

	public PrayerSeasonResult createSeason(CreatePrayerSeasonCommand command) {
		return seasonCommandService.createSeason(command);
	}

	public PrayerSeasonResult closeSeason(ClosePrayerSeasonCommand command) {
		return seasonCommandService.closeSeason(command);
	}

	public PrayerGroupResult createGroup(CreatePrayerGroupCommand command) {
		return groupCommandService.createGroup(command);
	}

	public PrayerGroupResult updateGroup(UpdatePrayerGroupCommand command) {
		return groupCommandService.updateGroup(command);
	}

	public PrayerGroupResult replaceGroupMembers(ReplacePrayerGroupMembersCommand command) {
		return groupCommandService.replaceGroupMembers(command);
	}

	public PrayerSeasonResult getCurrentSeason(Long campusId, Long requesterId) {
		return seasonQueryService.getCurrentSeason(campusId, requesterId);
	}

	public List<PrayerGroupResult> getSeasonGroups(Long seasonId, Long requesterId) {
		return groupQueryService.getSeasonGroups(seasonId, requesterId);
	}

	public List<PrayerAssignableMemberResult> getAssignableMembers(Long seasonId, Long requesterId) {
		return groupQueryService.getAssignableMembers(seasonId, requesterId);
	}

	public PrayerWeekBoardResult getWeeklyBoard(Long campusId, LocalDate weekStartDate, Long requesterId) {
		return weekBoardQueryService.getWeeklyBoard(campusId, weekStartDate, requesterId);
	}

	public PrayerWeekBoardResult saveSubmissions(SavePrayerSubmissionsCommand command) {
		return adminSubmissionCommandService.saveSubmissions(command);
	}

	public PrayerWeekBoardResult saveMySubmission(SaveMyPrayerSubmissionCommand command) {
		return mySubmissionCommandService.saveMySubmission(command);
	}
}
