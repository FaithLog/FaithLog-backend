package com.faithlog.points;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class PointWalletTest {
 @Test void starts_empty_and_applies_credit_and_debit() {
  var wallet=new PointWallet(7L);
  assertThat(wallet.balance()).isZero();
  wallet.apply(100);wallet.apply(-70);
  assertThat(wallet.balance()).isEqualTo(30);
 }
 @Test void rejects_insufficient_balance_without_mutation() {
  var wallet=new PointWallet(7L);wallet.apply(30);
  assertThatThrownBy(()->wallet.apply(-31)).isInstanceOf(IllegalArgumentException.class);
  assertThat(wallet.balance()).isEqualTo(30);
 }
 @Test void rejects_zero_and_overflow_without_mutation() {
  var wallet=new PointWallet(7L);
  assertThatThrownBy(()->wallet.apply(0)).isInstanceOf(IllegalArgumentException.class);
  wallet.apply(Long.MAX_VALUE);
  assertThatThrownBy(()->wallet.apply(1)).isInstanceOf(ArithmeticException.class);
  assertThat(wallet.balance()).isEqualTo(Long.MAX_VALUE);
 }
}
