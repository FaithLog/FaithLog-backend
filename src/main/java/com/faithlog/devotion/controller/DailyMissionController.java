package com.faithlog.devotion.controller;

import com.faithlog.devotion.domain.type.DailyMission;
import com.faithlog.devotion.service.CalendarDailyRewardService;
import com.faithlog.devotion.service.result.DailyMissionResult;
import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/devotions/me/daily-missions")
public class DailyMissionController {
 private final CalendarDailyRewardService service;
 public DailyMissionController(CalendarDailyRewardService service) {this.service=service;}
 @GetMapping
 public ApiResponse<DailyMissionResult> today(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long campusId) {
  return ApiResponse.success(service.getToday(campusId,user.userId()));
 }
 @PostMapping("/{mission}/claims")
 public ApiResponse<DailyMissionResult> claim(@AuthenticationPrincipal AuthenticatedUser user,@PathVariable Long campusId,@PathVariable DailyMission mission) {
  return ApiResponse.success(service.claim(campusId,user.userId(),mission));
 }
}
