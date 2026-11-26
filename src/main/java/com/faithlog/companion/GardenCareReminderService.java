package com.faithlog.companion;

import com.faithlog.notification.domain.entity.NotificationLog;
import com.faithlog.notification.domain.type.NotificationType;
import com.faithlog.notification.infrastructure.repository.NotificationLogRepository;
import com.faithlog.notification.infrastructure.repository.UserFcmTokenRepository;
import com.faithlog.notification.service.port.NotificationDispatchPort;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GardenCareReminderService {
    private final NamedParameterJdbcTemplate jdbc;
    private final CompanionInstanceService instances;
    private final UserFcmTokenRepository tokens;
    private final NotificationLogRepository logs;
    private final NotificationDispatchPort dispatch;

    public GardenCareReminderService(NamedParameterJdbcTemplate jdbc, CompanionInstanceService instances,
            UserFcmTokenRepository tokens, NotificationLogRepository logs, NotificationDispatchPort dispatch) {
        this.jdbc = jdbc;
        this.instances = instances;
        this.tokens = tokens;
        this.logs = logs;
        this.dispatch = dispatch;
    }

    @Transactional(readOnly = true)
    public boolean enabled(long userId) {
        return jdbc.queryForList("SELECT enabled FROM garden_care_reminders WHERE user_id = :user",
                Map.of("user", userId), Boolean.class).stream().findFirst().orElse(false);
    }

    @Transactional
    public boolean setEnabled(long userId, boolean enabled) {
        jdbc.update("""
                INSERT INTO garden_care_reminders (user_id, enabled) VALUES (:user, :enabled)
                ON CONFLICT (user_id) DO UPDATE SET enabled = EXCLUDED.enabled
                """, Map.of("user", userId, "enabled", enabled));
        return enabled;
    }

    @Transactional
    public long queueBatch(LocalDate date, long cursor) {
        var candidates = jdbc.query("""
                SELECT r.user_id, MIN(cm.campus_id) AS campus_id
                FROM garden_care_reminders r
                JOIN users u ON u.id = r.user_id AND u.is_active AND u.deleted_at IS NULL
                JOIN campus_members cm ON cm.user_id = r.user_id AND cm.status = 'ACTIVE'
                JOIN campuses c ON c.id = cm.campus_id AND c.is_active
                WHERE r.enabled AND r.user_id > :cursor
                  AND (r.last_queued_on IS NULL OR r.last_queued_on < :date)
                  AND EXISTS (SELECT 1 FROM garden_placements gp
                              WHERE gp.user_id = r.user_id AND gp.instance_id IS NOT NULL)
                GROUP BY r.user_id ORDER BY r.user_id LIMIT 100
                """, Map.of("cursor", cursor, "date", date),
                (row, index) -> new Candidate(row.getLong("user_id"), row.getLong("campus_id")));
        if (candidates.isEmpty()) return 0;
        var userIds = candidates.stream().map(Candidate::userId).collect(Collectors.toSet());
        var reachable = tokens.findActiveSendableTokensByUserIdIn(userIds).stream()
                .map(token -> token.userId()).collect(Collectors.toSet());
        for (Candidate candidate : candidates) {
            if (!reachable.contains(candidate.userId())) continue;
            long count = instances.gardenInstances(candidate.userId()).stream()
                    .map(CompanionInstanceService.View::stats)
                    .filter(stats -> stats.health() < 25 || stats.satiety() < 25 || stats.cleanliness() < 25)
                    .count();
            if (count > 0) queue(candidate, date, count);
        }
        return candidates.getLast().userId();
    }

    private void queue(Candidate candidate, LocalDate date, long count) {
        int claimed = jdbc.update("""
                UPDATE garden_care_reminders SET last_queued_on = :date
                WHERE user_id = :user AND enabled
                  AND (last_queued_on IS NULL OR last_queued_on < :date)
                """, Map.of("user", candidate.userId(), "date", date));
        if (claimed == 0) return;
        UUID requestId = UUID.randomUUID();
        logs.save(NotificationLog.pending(requestId, candidate.userId(), candidate.campusId(),
                NotificationType.GARDEN_CARE_REMINDER, null, null, "정원 친구들을 돌봐주세요",
                "돌봄이 필요한 동물이 " + count + "마리 있어요.",
                Map.of("eventType", "GARDEN_CARE_REMINDER")));
        dispatch.dispatch(requestId);
    }

    private record Candidate(long userId, long campusId) {}
}
