package com.faithlog.points;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.time.*;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class AttendanceServiceTest {
 @Test void claim_uses_seoul_date_and_server_amount() {
  var wallet=mock(PointWalletService.class);
  var transactions=mock(PointTransactionRepository.class);
  var clock=Clock.fixed(Instant.parse("2026-11-11T15:00:00Z"),ZoneOffset.UTC);
  when(wallet.wallet(7L)).thenReturn(new PointWalletService.WalletResult(10,1));
  var result=new AttendanceService(wallet,transactions,clock).claim(7L);
  assertThat(result.date()).isEqualTo(LocalDate.of(2026,11,12));
  assertThat(result.claimed()).isTrue();
  assertThat(result.points()).isEqualTo(10);
  verify(wallet).settle(7L,10,"attendance:2026-11-12");
 }
 @Test void status_checks_only_current_account_and_day() {
  var wallet=mock(PointWalletService.class);
  var transactions=mock(PointTransactionRepository.class);
  var clock=Clock.fixed(Instant.parse("2026-11-11T14:59:59Z"),ZoneOffset.UTC);
  when(wallet.wallet(7L)).thenReturn(new PointWalletService.WalletResult(10,1));
  when(transactions.findByUserIdAndBusinessKey(7L,"attendance:2026-11-11"))
   .thenReturn(Optional.of(new PointTransaction(7L,10,10,"attendance:2026-11-11",clock.instant())));
  var result=new AttendanceService(wallet,transactions,clock).status(7L);
  assertThat(result.claimed()).isTrue();
  assertThat(result.date()).isEqualTo(LocalDate.of(2026,11,11));
  verify(wallet,never()).settle(any(),anyLong(),anyString());
 }
}
