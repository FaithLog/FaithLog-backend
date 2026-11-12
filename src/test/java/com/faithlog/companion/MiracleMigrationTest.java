package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import java.nio.file.*;
import org.junit.jupiter.api.Test;
class MiracleMigrationTest {
 @Test void adds_versioned_miracle_without_rewriting_history() throws Exception {
  String sql=Files.readString(Path.of("src/main/resources/db/migration/V31__add_jesus_miracle.sql"));
  assertThat(sql).contains("jesus:miracle", "standard-v2", "MIRACLE", "9999", "1300");
  var p=new DrawPolicy(java.util.List.of(new DrawPolicy.Entry("animals",129987000),new DrawPolicy.Entry("jesus:miracle",13000)));
  assertThat(p.select(129986999)).isEqualTo("animals");
  assertThat(p.select(129987000)).isEqualTo("jesus:miracle");
  assertThat(p.select(129999999)).isEqualTo("jesus:miracle");
 }
}
