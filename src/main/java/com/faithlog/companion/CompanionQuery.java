package com.faithlog.companion;
import java.util.Set;
import com.faithlog.global.exception.*;
public record CompanionQuery(String filter,String rarity,String query,String sort,int page,int size){
 public CompanionQuery{
  filter=filter==null?"OWNED":filter;sort=sort==null?"CATALOG":sort;query=query==null?"":query.trim();
  if(!Set.of("OWNED","DUPLICATES","ALL","UNEQUIPPED").contains(filter)||!Set.of("CATALOG","RARITY","COUNT").contains(sort)||rarity!=null&&!Set.of("COMMON","UNCOMMON","RARE","EPIC","UNIQUE","MIRACLE").contains(rarity)||page<0||page>10000||size<1||size>100||query.length()>100)throw new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);
 }
 public String searchPattern(){return "%"+query.replace("\\","\\\\").replace("%","\\%").replace("_","\\_")+"%";}
}
