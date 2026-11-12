package com.faithlog.points;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.user.domain.entity.User;
import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;
class PointWalletServiceTest {
 @Test void duplicate_settlement_does_not_change_balance_or_write_again(){
  var users=mock(UserRepository.class);var wallets=mock(PointWalletRepository.class);var tx=mock(PointTransactionRepository.class);var user=mock(User.class);
  when(user.isActive()).thenReturn(true);when(users.findByIdForUpdate(7L)).thenReturn(Optional.of(user));
  var wallet=new PointWallet(7L);wallet.apply(30);when(wallets.findById(7L)).thenReturn(Optional.of(wallet));
  when(tx.findByUserIdAndBusinessKey(7L,"daily:2026-11-11:PRAYER")).thenReturn(Optional.of(new PointTransaction(7L,30,30,"daily:2026-11-11:PRAYER",Instant.EPOCH)));
  var service=new PointWalletService(users,wallets,tx,Clock.systemUTC());
  service.settle(7L,30,"daily:2026-11-11:PRAYER");assertThat(wallet.balance()).isEqualTo(30);verify(tx,never()).saveAndFlush(any());
  assertThatThrownBy(()->service.settle(7L,60,"daily:2026-11-11:PRAYER")).isInstanceOf(IllegalArgumentException.class);
 }
 @Test void inactive_user_cannot_initialize_wallet(){
  var users=mock(UserRepository.class);var wallets=mock(PointWalletRepository.class);var tx=mock(PointTransactionRepository.class);
  when(users.findByIdForUpdate(7L)).thenReturn(Optional.empty());
  assertThatThrownBy(()->new PointWalletService(users,wallets,tx,Clock.systemUTC()).wallet(7L)).isInstanceOf(RuntimeException.class);
  verifyNoInteractions(wallets,tx);
 }
}
