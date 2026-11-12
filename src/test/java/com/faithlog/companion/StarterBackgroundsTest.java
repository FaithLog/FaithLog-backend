package com.faithlog.companion;

import static org.assertj.core.api.Assertions.*;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;

class StarterBackgroundsTest {
 @Test void grants_current_backgrounds_idempotently_but_not_future_draw_rewards() {
  var jdbc = new NamedParameterJdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:starter;MODE=PostgreSQL;DB_CLOSE_DELAY=-1", "sa", ""));
  jdbc.getJdbcTemplate().execute("CREATE TABLE backgrounds(background_id VARCHAR(40) PRIMARY KEY)");
  jdbc.getJdbcTemplate().execute("CREATE TABLE user_backgrounds(user_id BIGINT,background_id VARCHAR(40) REFERENCES backgrounds(background_id),acquired_at TIMESTAMP NOT NULL,PRIMARY KEY(user_id,background_id))");
  for (String id : new String[]{"default","meadow","olive","shore","starlight","future-reward"})
   jdbc.update("INSERT INTO backgrounds VALUES(:id)", Map.of("id",id));
  StarterBackgrounds.grant(jdbc, 1L);
  StarterBackgrounds.grant(jdbc, 1L);
  StarterBackgrounds.grant(jdbc, 2L);
  for (long user : new long[]{1L,2L}) {
   assertThat(jdbc.queryForList("SELECT background_id FROM user_backgrounds WHERE user_id=:user",Map.of("user",user),String.class))
    .containsExactlyInAnyOrder("default","meadow","olive","shore","starlight");
  }
 }
}
