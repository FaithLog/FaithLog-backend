package com.faithlog.companion;
/** Server unit price only; callers must lock inventory and wallet before applying the quote. */
public record SaleQuote(long quantity,long unitPrice,long totalPoints){
 public static SaleQuote calculate(long owned,long quantity,long unitPrice){
  if(owned<1||quantity<1||quantity>=owned||unitPrice<=0)throw new IllegalArgumentException("Keep at least one companion");
  return new SaleQuote(quantity,unitPrice,Math.multiplyExact(quantity,unitPrice));
 }
 public SaleQuote{if(quantity<=0||unitPrice<=0||totalPoints!=Math.multiplyExact(quantity,unitPrice))throw new IllegalArgumentException("Invalid quote");}
}
