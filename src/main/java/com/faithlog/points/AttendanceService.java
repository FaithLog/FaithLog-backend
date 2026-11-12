package com.faithlog.points;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AttendanceService {
 private static final ZoneId SEOUL=ZoneId.of("Asia/Seoul");
 private static final long POINTS=10;
 private final PointWalletService wallet;
 private final PointTransactionRepository transactions;
 private final Clock clock;
 public AttendanceService(PointWalletService wallet,PointTransactionRepository transactions,Clock clock) {
  this.wallet=wallet;this.transactions=transactions;this.clock=clock;
 }
 @Transactional public Result status(Long userId) {
  wallet.lockAccount(userId);
  LocalDate date=LocalDate.now(clock.withZone(SEOUL));
  boolean claimed=transactions.findByUserIdAndBusinessKey(userId,key(date)).isPresent();
  return new Result(date,POINTS,claimed,wallet.wallet(userId).balance());
 }
 @Transactional public Result claim(Long userId) {
  wallet.lockAccount(userId);
  LocalDate date=LocalDate.now(clock.withZone(SEOUL));
  wallet.settle(userId,POINTS,key(date));
  return new Result(date,POINTS,true,wallet.wallet(userId).balance());
 }
 private static String key(LocalDate date) {return "attendance:"+date;}
 public record Result(LocalDate date,long points,boolean claimed,long balance) {}
}
