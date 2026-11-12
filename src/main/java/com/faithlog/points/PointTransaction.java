package com.faithlog.points;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="point_transactions")
public class PointTransaction {
 @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
 @Column(name="user_id",nullable=false) private Long userId;
 @Column(nullable=false) private long delta;
 @Column(name="balance_after",nullable=false) private long balanceAfter;
 @Column(name="business_key",nullable=false,length=160) private String businessKey;
 @Column(name="created_at",nullable=false) private Instant createdAt;
 protected PointTransaction(){}
 public PointTransaction(Long userId,long delta,long balanceAfter,String key,Instant now){
  this.userId=userId;this.delta=delta;this.balanceAfter=balanceAfter;this.businessKey=key;this.createdAt=now;
 }
 public long delta(){return delta;}
 public long balanceAfter(){return balanceAfter;}
 public String businessKey(){return businessKey;}
 public Instant createdAt(){return createdAt;}
}
