package com.faithlog.companion;
import java.security.SecureRandom;
import java.util.*;
/** Immutable policy snapshot. Caller persists policy version and results atomically with wallet debit. */
public final class DrawPolicy {
 private final List<Entry> entries;
 private final int totalWeight;
 public DrawPolicy(List<Entry> entries){
  if(entries==null||entries.isEmpty()||entries.stream().anyMatch(Objects::isNull))throw new IllegalArgumentException("Empty draw pool");
  this.entries=List.copyOf(entries);
  if(entries.stream().map(Entry::designId).distinct().count()!=entries.size())throw new IllegalArgumentException("Duplicate pool design");
  int sum=0;for(var entry:entries)sum=Math.addExact(sum,entry.weight());totalWeight=sum;
 }
 public String draw(SecureRandom random){return select(random.nextInt(totalWeight));}
 public String select(int ticket){
  if(ticket<0||ticket>=totalWeight)throw new IllegalArgumentException("Ticket outside pool");
  for(var entry:entries){if(ticket<entry.weight())return entry.designId();ticket-=entry.weight();}
  throw new IllegalStateException("Invalid pool weights");
 }
 public static long cost(int count){if(count!=1&&count!=10)throw new IllegalArgumentException("Draw count must be 1 or 10");return count*100L;}
 public record Entry(String designId,int weight){public Entry{if(designId==null||designId.isBlank()||weight<=0)throw new IllegalArgumentException("Invalid pool entry");}}
}
