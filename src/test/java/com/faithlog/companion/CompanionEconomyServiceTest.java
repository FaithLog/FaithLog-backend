package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.faithlog.points.PointWalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import java.time.Clock;
import org.junit.jupiter.api.Test;
class CompanionEconomyServiceTest {
 @Test void rejects_invalid_requests_before_any_balance_or_inventory_access(){
  var wallet=mock(PointWalletService.class);var jdbc=mock(NamedParameterJdbcTemplate.class);
  var service=new CompanionEconomyService(wallet,jdbc,new ObjectMapper(),Clock.systemUTC());
  assertThatThrownBy(()->service.draw(1L,"key",new CompanionEconomyService.DrawRequest("standard-v1",2))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->service.draw(1L,"bad key",new CompanionEconomyService.DrawRequest("standard-v1",1))).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->service.sell(1L,"key",new CompanionEconomyService.SaleRequest("david-lamb:base",0))).isInstanceOf(RuntimeException.class);
  verifyNoInteractions(wallet,jdbc);
 }
}
