package com.faithlog.companion;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class GardenCareReminderScheduler {
    private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
    private final GardenCareReminderService reminders;
    private final Clock clock;

    public GardenCareReminderScheduler(GardenCareReminderService reminders, Clock clock) {
        this.reminders = reminders;
        this.clock = clock;
    }

    @Scheduled(cron = "0 0 19 * * *", zone = "Asia/Seoul")
    public void queueReminders() {
        LocalDate date = LocalDate.ofInstant(clock.instant(), SEOUL);
        long cursor = 0;
        do {
            cursor = reminders.queueBatch(date, cursor);
        } while (cursor != 0);
    }
}
