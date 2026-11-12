package com.faithlog.points;
import com.faithlog.user.infrastructure.repository.UserRepository;
import com.faithlog.global.exception.*;
import java.time.Clock;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.data.domain.PageRequest;
@Service
public class PointWalletService {
 private final UserRepository users;
 private final PointWalletRepository wallets;
 private final PointTransactionRepository transactions;
 private final Clock clock;
 public PointWalletService(UserRepository users,PointWalletRepository wallets,PointTransactionRepository transactions,Clock clock){
  this.users=users;this.wallets=wallets;this.transactions=transactions;this.clock=clock;
 }
 @Transactional public void lockAccount(Long id){
  users.findByIdForUpdate(id).filter(u->u.isActive()).orElseThrow(()->new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
 }
 private PointWallet lock(Long id){
  users.findByIdForUpdate(id).filter(u->u.isActive()).orElseThrow(()->new BusinessException(ErrorCode.AUTH_UNAUTHORIZED));
  return wallets.findById(id).orElseGet(()->wallets.saveAndFlush(new PointWallet(id)));
 }
 @Transactional public WalletResult wallet(Long id){var w=lock(id);return new WalletResult(w.balance(),w.version());}
 /** Internal settlement boundary only. No client endpoint accepts deltas or business keys. */
 @Transactional public void settle(Long id,long delta,String businessKey){
  if(businessKey==null||businessKey.isBlank()||businessKey.length()>160||delta==0)throw new IllegalArgumentException("Invalid settlement");
  var wallet=lock(id);
  var old=transactions.findByUserIdAndBusinessKey(id,businessKey);
  if(old.isPresent()){
   if(old.get().delta()!=delta)throw new IllegalArgumentException("Settlement key reused with different amount");
   return;
  }
  wallet.apply(delta);
  transactions.saveAndFlush(new PointTransaction(id,delta,wallet.balance(),businessKey,clock.instant()));
 }
 @Transactional public List<TransactionResult> history(Long id,int page){
  lock(id);
  if(page<0||page>10000)throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
  return transactions.findByUserIdOrderByIdDesc(id,PageRequest.of(page,20)).stream().map(t->new TransactionResult(t.delta(),t.balanceAfter(),t.createdAt())).toList();
 }
 public record WalletResult(long balance,long version){}
 public record TransactionResult(long delta,long balanceAfter,java.time.Instant createdAt){}
}
