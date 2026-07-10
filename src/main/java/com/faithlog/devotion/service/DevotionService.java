package com.faithlog.devotion.service;

import com.faithlog.devotion.service.command.UpdateDailyDevotionCommand;
import com.faithlog.devotion.service.command.UpdateWeeklyDevotionCommand;
import com.faithlog.devotion.service.query.GetMissingDevotionMembersQuery;
import com.faithlog.devotion.service.query.GetMyWeeklyDevotionQuery;
import com.faithlog.devotion.service.result.DailyDevotionResult;
import com.faithlog.devotion.service.result.MissingDevotionMemberResult;
import com.faithlog.devotion.service.result.WeeklyDevotionResult;
import java.util.List;
import org.springframework.stereotype.Service;

@Service
public class DevotionService {

	private final DailyDevotionCommandService dailyDevotionCommandService;
	private final WeeklyDevotionCommandService weeklyDevotionCommandService;
	private final MyWeeklyDevotionQueryService myWeeklyDevotionQueryService;
	private final MissingDevotionMemberQueryService missingDevotionMemberQueryService;

	public DevotionService(
		DailyDevotionCommandService dailyDevotionCommandService,
		WeeklyDevotionCommandService weeklyDevotionCommandService,
		MyWeeklyDevotionQueryService myWeeklyDevotionQueryService,
		MissingDevotionMemberQueryService missingDevotionMemberQueryService
	) {
		this.dailyDevotionCommandService = dailyDevotionCommandService;
		this.weeklyDevotionCommandService = weeklyDevotionCommandService;
		this.myWeeklyDevotionQueryService = myWeeklyDevotionQueryService;
		this.missingDevotionMemberQueryService = missingDevotionMemberQueryService;
	}

	public DailyDevotionResult updateDailyCheck(UpdateDailyDevotionCommand command) {
		return dailyDevotionCommandService.updateDailyCheck(command);
	}

	public WeeklyDevotionResult updateWeeklyCheck(UpdateWeeklyDevotionCommand command) {
		return weeklyDevotionCommandService.updateWeeklyCheck(command);
	}

	public WeeklyDevotionResult getMyWeeklyCheck(GetMyWeeklyDevotionQuery query) {
		return myWeeklyDevotionQueryService.getMyWeeklyCheck(query);
	}

	public List<MissingDevotionMemberResult> getMissingMembers(GetMissingDevotionMembersQuery query) {
		return missingDevotionMemberQueryService.getMissingMembers(query);
	}
}
