package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class CompanionQueryTest {
 @Test void defaults_to_owned_and_bounded_page(){var q=new CompanionQuery(null,null,null,null,0,20);assertThat(q.filter()).isEqualTo("OWNED");assertThat(q.sort()).isEqualTo("CATALOG");}
 @Test void rejects_unknown_enums_and_unbounded_pages(){
  assertThatThrownBy(()->new CompanionQuery("sql",null,null,null,0,20)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new CompanionQuery(null,null,null,null,0,101)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new CompanionQuery(null,"LEGEND",null,null,0,20)).isInstanceOf(RuntimeException.class);
 }
 @Test void search_treats_sql_wildcards_as_literals(){var q=new CompanionQuery(null,null,"%_\\",null,0,20);assertThat(q.searchPattern()).isEqualTo("%\\%\\_\\\\%");}
}
