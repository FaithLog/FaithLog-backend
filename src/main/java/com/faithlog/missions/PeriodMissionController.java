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
 @GetMapping public ApiResponse<PeriodMissionService.Snapshot> get(@PathVariable Long campusId,@AuthenticationPrincipal AuthenticatedUser user) {
  return ApiResponse.success(service.get(campusId,user.userId()));
 }
 @PostMapping("/{mission}/claims") public ApiResponse<PeriodMissionService.Snapshot> claim(@PathVariable Long campusId,@PathVariable PeriodMission mission,@AuthenticationPrincipal AuthenticatedUser user) {
  return ApiResponse.success(service.claim(campusId,user.userId(),mission));
 }
}
