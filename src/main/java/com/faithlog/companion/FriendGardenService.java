package com.faithlog.companion;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.global.exception.ErrorCode;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendGardenService {
    private static final int PAGE_SIZE = 20;
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

    public FriendGardenService(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
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
            SELECT friend.name, g.background_id, gp.design_id, gp.x, gp.y, ci.level
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
                    : new Animal(rs.getString("design_id"), rs.getDouble("x"), rs.getDouble("y"),
                        Math.max(1, rs.getInt("level")))));
        if (rows.isEmpty()) throw new BusinessException(ErrorCode.COMPANION_FRIEND_GARDEN_NOT_FOUND);
        return new Visit(rows.getFirst().name(), rows.getFirst().backgroundId(),
            rows.stream().map(Row::animal).filter(java.util.Objects::nonNull).toList());
    }

    private void requireMember(Map<String, Long> params) {
        if (!Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS (" + ACTIVE_MEMBER + ")", params, Boolean.class))) {
            throw new BusinessException(ErrorCode.CAMPUS_VIEW_FORBIDDEN);
        }
    }

    public record Friend(long userId, String name) {}
    public record Page(boolean visible, List<Friend> items, Long nextCursor) {}
    public record Visibility(boolean visible) {}
    public record Animal(String designId, double x, double y, int level) {}
    public record Visit(String name, String backgroundId, List<Animal> animals) {}
    private record Row(String name, String backgroundId, Animal animal) {}
}
