package com.faithlog.companion;
import java.util.*;
import com.faithlog.global.exception.*;
public record GardenUpdate(long version,String backgroundId,List<Placement> placements){
 public GardenUpdate{
  if(version<0||backgroundId!=null&&(backgroundId.isBlank()||backgroundId.length()>40)||placements==null||placements.size()>5||placements.stream().anyMatch(Objects::isNull))throw invalid();
  placements=List.copyOf(placements);
  if(placements.stream().map(Placement::slot).distinct().count()!=placements.size()||placements.stream().map(Placement::designId).distinct().count()!=placements.size())throw invalid();
 }
 private static BusinessException invalid(){return new BusinessException(ErrorCode.GLOBAL_VALIDATION_FAILED);}
 public record Placement(int slot,String designId,double x,double y){
  public Placement{if(slot<1||slot>5||designId==null||designId.isBlank()||designId.length()>100||!Double.isFinite(x)||!Double.isFinite(y)||x<0||x>1||y<0||y>1)throw invalid();}
 }
}
