package com.faithlog.devotion.domain.type;

import com.faithlog.devotion.domain.entity.DevotionDailyCheck;

public enum DailyMission {
 QUIET_TIME, PRAYER, BIBLE;
 public boolean checked(DevotionDailyCheck check) {
  if (check == null) return false;
  return switch (this) {
   case QUIET_TIME -> check.quietTimeChecked();
   case PRAYER -> check.prayerChecked();
   case BIBLE -> check.bibleReadingChecked();
  };
 }
}
