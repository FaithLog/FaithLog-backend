package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import com.faithlog.points.PointWalletService;
import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.TemporalAdjusters;
import java.util.List;
import java.util.UUID;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendGardenService {
    private static final int PAGE_SIZE = 20;
    private static final int WEEKLY_HELPER_GOAL = 5;
    private static final ZoneId KOREA = ZoneId.of("Asia/Seoul");
    private static final String ACTIVE_MEMBER = """
        SELECT 1 FROM campus_members cm
        JOIN users u ON u.id = cm.user_id AND u.is_active AND u.deleted_at IS NULL
        JOIN campuses c ON c.id = cm.campus_id AND c.is_active
        WHERE cm.campus_id = :campus AND cm.user_id = :user AND cm.status = 'ACTIVE'
        """;
    private static final String FRIENDS = """
        FROM friend_garden_visibility v
        JOIN campus_members cm ON cm.campus_id = v.campus_id AND cm.user_id = v.user_id AND cm.status = 'ACTIVE'
          AND cm.joined_at = v.membership_joined_at
        JOIN users u ON u.id = v.user_id AND u.is_active AND u.deleted_at IS NULL
        WHERE v.campus_id = :campus AND v.user_id <> :user
        """;

    private final NamedParameterJdbcTemplate jdbc;

    private final CompanionInstanceService instances;
    private final PointWalletService accounts;
    private final Clock clock;

    public FriendGardenService(NamedParameterJdbcTemplate jdbc, CompanionInstanceService instances,
                               PointWalletService accounts, Clock clock) {
        this.jdbc = jdbc;
        this.instances = instances;
        this.accounts = accounts;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Page list(long campus, long user, long after) {
        if (after < 0) throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
        var params = Map.of("campus", campus, "user", user, "after", after);
        requireMember(params);
        boolean visible = Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS (
                SELECT 1 FROM friend_garden_visibility v
                JOIN campus_members cm ON cm.campus_id = v.campus_id AND cm.user_id = v.user_id
                  AND cm.joined_at = v.membership_joined_at
                WHERE v.campus_id = :campus AND v.user_id = :user
            )
            """, params, Boolean.class));
        var rows = jdbc.query("""
            SELECT v.user_id, u.name
            """ + FRIENDS + " AND EXISTS (" + ACTIVE_MEMBER + ") AND v.user_id > :after ORDER BY v.user_id LIMIT " + (PAGE_SIZE + 1),
            params, (rs, n) -> new Friend(rs.getLong("user_id"), rs.getString("name")));
        var items = rows.stream().limit(PAGE_SIZE).toList();
        return new Page(visible, items, rows.size() > PAGE_SIZE ? items.getLast().userId() : null);
    }

    @Transactional
    public Visibility visibility(long campus, long user, boolean visible) {
        var params = Map.of("campus", campus, "user", user);
        requireMember(params);
        if (visible) {
            jdbc.update("""
                INSERT INTO friend_garden_visibility(campus_id, user_id, membership_joined_at)
                SELECT :campus, :user, cm.joined_at FROM campus_members cm
                WHERE cm.campus_id = :campus AND cm.user_id = :user AND EXISTS (
                """ + ACTIVE_MEMBER + ") ON CONFLICT (campus_id, user_id) DO UPDATE SET membership_joined_at = EXCLUDED.membership_joined_at", params);
        } else {
            jdbc.update("DELETE FROM friend_garden_visibility WHERE campus_id = :campus AND user_id = :user", params);
        }
        return new Visibility(visible);
    }

    @Transactional(readOnly = true)
    public Visit visit(long campus, long user, long target) {
        var params = Map.of("campus", campus, "user", user, "target", target);
        requireMember(params);
        // Authorize and read from the same statement snapshot. Public data omits instance IDs and private progress.
        var rows = jdbc.query("""
            SELECT friend.name, g.background_id, gp.slot, gp.design_id, gp.x, gp.y, ci.level
            FROM (
                SELECT v.user_id, u.name
                """ + FRIENDS + " AND v.user_id = :target AND EXISTS (" + ACTIVE_MEMBER + """
                )) friend
            LEFT JOIN user_gardens g ON g.user_id = friend.user_id
            LEFT JOIN garden_placements gp ON gp.user_id = g.user_id
            LEFT JOIN companion_instances ci ON ci.id = gp.instance_id AND ci.user_id = gp.user_id
            ORDER BY gp.slot
            """, params, (rs, n) -> new Row(rs.getString("name"), rs.getString("background_id"),
                rs.getString("design_id") == null ? null
                    : new Animal(rs.getInt("slot"), rs.getString("design_id"), rs.getDouble("x"), rs.getDouble("y"),
                        Math.max(1, rs.getInt("level")))));
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.COMPANION_FRIEND_GARDEN_NOT_FOUND);
        return new Visit(rows.getFirst().name(), rows.getFirst().backgroundId(),
            rows.stream().map(Row::animal).filter(java.util.Objects::nonNull).toList());
    }

    @Transactional
    public HelpResult help(long campus, long user, long owner, int slot, String requestedAction) {
        if (slot < 1 || slot > 5 || user == owner
                || !("FEED".equals(requestedAction) || "CLEAN".equals(requestedAction))) {
            throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
        }
        requireMember(Map.of("campus", campus, "user", user));
        accounts.lockAccount(owner);
        var params = Map.of("campus", campus, "user", user, "target", owner, "slot", slot);
        var animals = jdbc.query("""
            SELECT gp.instance_id FROM garden_placements gp
            WHERE gp.user_id = :target AND gp.slot = :slot AND gp.instance_id IS NOT NULL
              AND EXISTS (SELECT 1
            """ + FRIENDS + " AND v.user_id = :target FOR SHARE OF v, cm) AND EXISTS (" + ACTIVE_MEMBER + ")",
            params, (rs, n) -> rs.getObject(1, UUID.class));
        if (animals.isEmpty()) throw new BusinessException(ErrorCode.COMPANION_FRIEND_GARDEN_NOT_FOUND);

        var now = clock.instant();
        var date = LocalDate.ofInstant(now, KOREA);
        var animal = animals.getFirst();
        int inserted = jdbc.update("""
            INSERT INTO friend_garden_help(helper_id, help_date, animal_id)
            VALUES (:helper, :date, :animal) ON CONFLICT DO NOTHING
            """, Map.of("helper", user, "date", date, "animal", animal));
        if (inserted == 0) throw new BusinessException(ErrorCode.COMPANION_FRIEND_HELP_LIMIT);
        instances.applyFriendHelp(owner, animal, CompanionGrowthPolicy.CareAction.valueOf(requestedAction), now);
        jdbc.update("""
            INSERT INTO campus_garden_goal_contributions(campus_id, week_start, helper_id)
            VALUES (:campus, :week, :helper) ON CONFLICT DO NOTHING
            """, Map.of("campus", campus, "week", weekStart(date), "helper", user));
        return new HelpResult(true);
    }

    @Transactional(readOnly = true)
    public WeeklyGoal weeklyGoal(long campus, long user) {
        var start = weekStart(LocalDate.ofInstant(clock.instant(), KOREA));
        var params = Map.of("campus", campus, "user", user);
        requireMember(params);
        var progress = jdbc.queryForObject("""
            SELECT count(*), coalesce(bool_or(helper_id = :user), false)
            FROM campus_garden_goal_contributions
            WHERE campus_id = :campus AND week_start = :week AND EXISTS (
            """ + ACTIVE_MEMBER + ")",
            Map.of("campus", campus, "week", start, "user", user),
            (rs, n) -> new WeeklyGoal(start, start.plusDays(6), WEEKLY_HELPER_GOAL, rs.getLong(1), rs.getBoolean(2)));
        return java.util.Objects.requireNonNull(progress);
    }

    static LocalDate weekStart(LocalDate date) {
        return date.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }

    private void requireMember(Map<String, Long> params) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + ACTIVE_MEMBER + ")", params, Boolean.class))) {
            throw new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN);
        }
    }

    public record Friend(long userId, String name) {}
    public record Page(boolean visible, List<Friend> items, Long nextCursor) {}
    public record Visibility(boolean visible) {}
    public record WeeklyGoal(LocalDate startsOn, LocalDate endsOn, int target, long participants, boolean contributed) {}
    public record HelpResult(boolean helped) {}
    public record Animal(int slot, String designId, double x, double y, int level) {}
    public record Visit(String name, String backgroundId, List<Animal> animals) {}
    private record Row(String name, String backgroundId, Animal animal) {}
}
