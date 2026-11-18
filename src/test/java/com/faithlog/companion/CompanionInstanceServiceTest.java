package com.faithlog.companion;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.faithlog.points.PointWalletService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

class CompanionInstanceServiceTest {
    @Test void validation_never_calls_wallet_or_database() {
        var wallet=mock(PointWalletService.class);
        var jdbc=mock(NamedParameterJdbcTemplate.class);
        var service=new CompanionInstanceService(wallet,jdbc,new ObjectMapper(),Clock.systemUTC());
        UUID id=UUID.randomUUID();
        assertThatThrownBy(() -> service.care(1L,id,"invalid key",
                new CompanionInstanceService.CareRequest("FEED",0))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.care(1L,id,"valid-key",
                new CompanionInstanceService.CareRequest("CHEAT",0))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.care(1L,id,"valid-key",
                new CompanionInstanceService.CareRequest("FEED",-1))).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(() -> service.evolve(1L,id,"valid-key",
                new CompanionInstanceService.EvolveRequest(-1))).isInstanceOf(RuntimeException.class);
        verifyNoInteractions(wallet,jdbc);
    }
}
