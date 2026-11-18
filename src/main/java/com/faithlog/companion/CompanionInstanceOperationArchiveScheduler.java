package com.faithlog.companion;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Opt-in only after staging/backups validate data retention.
 * Global faithlog.scheduler.enabled gating still applies to scheduling.
 */
@Component
@ConditionalOnProperty(prefix="faithlog.companion.operation-archive",
        name="enabled",havingValue="true")
public class CompanionInstanceOperationArchiveScheduler {
    private final CompanionInstanceOperationArchiveService service;

    public CompanionInstanceOperationArchiveScheduler(
            CompanionInstanceOperationArchiveService service) {
        this.service=service;
    }

    @Scheduled(cron="${faithlog.companion.operation-archive.cron:0 20 4 * * *}",
            zone="Asia/Seoul")
    public void archive() {
        // Never monopolize a database: at most 5 short, SKIP LOCKED batches.
        for (int i=0;i<5;i++) {
            if (service.archiveExpired(200)<200) break;
        }
    }
}
