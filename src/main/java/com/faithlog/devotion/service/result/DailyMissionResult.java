package com.faithlog.devotion.service.result;

import com.faithlog.devotion.domain.type.DailyMission;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record DailyMissionResult(LocalDate recordDate, Instant serverNow, long totalAwardedPoints, List<Item> missions) {
 public record Item(DailyMission mission, String status, int points) {}
}
