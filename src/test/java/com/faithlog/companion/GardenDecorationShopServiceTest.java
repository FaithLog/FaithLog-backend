package com.faithlog.companion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.faithlog.global.exception.BusinessException;
import com.faithlog.points.PointTransaction;
import com.faithlog.points.PointTransactionRepository;
import com.faithlog.points.PointWalletService;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class GardenDecorationShopServiceTest {
    private static final long USER = 7L;
    private static final String ITEM = "seed-dish";
    private static final String KEY = "garden-decoration:" + ITEM;
    private final PointWalletService wallet = mock(PointWalletService.class);
    private final PointTransactionRepository ledger = mock(PointTransactionRepository.class);
    private final GardenDecorationShopService service = new GardenDecorationShopService(wallet, ledger);

    @Test
    void chargesOnceAndRestoresOwnershipOnRetry() {
        when(wallet.wallet(USER)).thenReturn(
            new PointWalletService.WalletResult(150, 0),
            new PointWalletService.WalletResult(50, 1)
        );
        when(ledger.findByUserIdAndBusinessKey(USER, KEY))
            .thenReturn(Optional.empty(), Optional.of(mock(PointTransaction.class)));
        when(ledger.findBusinessKeysByUserIdAndBusinessKeyIn(eq(USER), anyCollection()))
            .thenReturn(List.of(KEY));

        var first = service.buy(USER, ITEM, 100);
        var retry = service.buy(USER, ITEM, 100);

        assertThat(first.balance()).isEqualTo(50);
        assertThat(retry).isEqualTo(first);
        assertThat(first.items()).hasSize(50).contains(new GardenDecorationShopService.Item(ITEM, 100, true));
        verify(wallet).settle(USER, -100, KEY);
    }

    @Test
    void rejectsIncorrectQuoteAndInsufficientFundsBeforeWriting() {
        when(wallet.wallet(USER)).thenReturn(new PointWalletService.WalletResult(50, 0));
        assertThatThrownBy(() -> service.buy(USER, ITEM, 200)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.buy(USER, ITEM, 100)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.buy(USER, "flower", 100)).isInstanceOf(BusinessException.class);
        verify(wallet, never()).settle(anyLong(), anyLong(), anyString());
    }
}
