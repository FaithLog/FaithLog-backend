package com.faithlog.companion;

import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

/** Existing backgrounds are free; future draw rewards must be acquired separately. */
final class StarterBackgrounds {
 private static final List<String> IDS = List.of("default", "meadow", "olive", "shore", "starlight");
 private StarterBackgrounds() {}
 static void grant(NamedParameterJdbcTemplate jdbc, Long user) {
  jdbc.update("INSERT INTO user_backgrounds(user_id,background_id,acquired_at) SELECT :user,background_id,CURRENT_TIMESTAMP FROM backgrounds WHERE background_id IN (:ids) ON CONFLICT DO NOTHING", Map.of("user", user, "ids", IDS));
 }
}
