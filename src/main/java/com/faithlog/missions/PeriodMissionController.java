package com.faithlog.missions;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/campuses/{campusId}/missions/me")
public class PeriodMissionController {
 private final PeriodMissionService service;
 public PeriodMissionController(PeriodMissionService service) {this.service=service;}
 @GetMapping public ApiResponse<PeriodMissionService.Snapshot> get(@PathVariable Long campusId,@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="false") boolean includePrayerTopic) {
  return ApiResponse.success(forClient(service.get(campusId,user.userId()),includePrayerTopic));
 }
 @PostMapping("/{mission}/claims") public ApiResponse<PeriodMissionService.Snapshot> claim(@PathVariable Long campusId,@PathVariable PeriodMission mission,@AuthenticationPrincipal AuthenticatedUser user,@RequestParam(defaultValue="false") boolean includePrayerTopic) {
  return ApiResponse.success(forClient(service.claim(campusId,user.userId(),mission),includePrayerTopic));
 }
 private PeriodMissionService.Snapshot forClient(PeriodMissionService.Snapshot snapshot,boolean includePrayerTopic) {
  if(includePrayerTopic)return snapshot;
  return new PeriodMissionService.Snapshot(snapshot.serverNow(),snapshot.balance(),
    snapshot.missions().stream().filter(item->item.mission()!=PeriodMission.WEEKLY_PRAYER_TOPIC).toList());
 }
}
