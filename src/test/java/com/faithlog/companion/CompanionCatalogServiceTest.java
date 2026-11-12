package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.faithlog.user.infrastructure.repository.UserRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.util.Optional;
import org.junit.jupiter.api.Test;
class CompanionCatalogServiceTest {
 @Test void refuses_missing_user_before_catalog_or_inventory_queries(){
  var users=mock(UserRepository.class);var jdbc=mock(NamedParameterJdbcTemplate.class);
  when(users.findById(9L)).thenReturn(Optional.empty());var service=new CompanionCatalogService(users,jdbc);
  assertThatThrownBy(()->service.catalog(9L)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->service.inventory(9L,new CompanionQuery(null,null,null,null,0,20))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->service.backgrounds(9L)).isInstanceOf(RuntimeException.class);
  verifyNoInteractions(jdbc);
 }
}
