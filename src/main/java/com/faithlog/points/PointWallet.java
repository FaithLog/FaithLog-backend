package com.faithlog.points;
import jakarta.persistence.*;
@Entity @Table(name="point_wallets")
public class PointWallet {
 @Id @Column(name="user_id") private Long userId;
 @Column(nullable=false) private long balance;
 @Version private long version;
 protected PointWallet() {}
 public PointWallet(Long userId) {this.userId=userId;}
 public long balance(){return balance;}
 public long version(){return version;}
 public void apply(long delta){
  if(delta==0)throw new IllegalArgumentException("Point delta must not be zero");
  long next=Math.addExact(balance,delta);
  if(next<0)throw new IllegalArgumentException("Insufficient points");
  balance=next;
 }
}
