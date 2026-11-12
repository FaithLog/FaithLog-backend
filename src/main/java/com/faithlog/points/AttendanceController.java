package com.faithlog.points;

import com.faithlog.global.response.ApiResponse;
import com.faithlog.global.security.AuthenticatedUser;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/points/me/attendance")
public class AttendanceController {
 private final AttendanceService service;
 public AttendanceController(AttendanceService service) {this.service=service;}
 @GetMapping public ApiResponse<AttendanceService.Result> status(@AuthenticationPrincipal AuthenticatedUser user) {
  return ApiResponse.success(service.status(user.userId()));
 }
 @PostMapping public ApiResponse<AttendanceService.Result> claim(@AuthenticationPrincipal AuthenticatedUser user) {
  return ApiResponse.success(service.claim(user.userId()));
 }
}
