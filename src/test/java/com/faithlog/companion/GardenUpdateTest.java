package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import java.util.*;
import org.junit.jupiter.api.Test;
class GardenUpdateTest {
 @Test void accepts_empty_garden(){assertThat(new GardenUpdate(0,null,List.of()).placements()).isEmpty();}
 @Test void rejects_duplicate_design_and_slots(){
  var p=new GardenUpdate.Placement(1,"david-lamb:base",0.5,0.5);
  assertThatThrownBy(()->new GardenUpdate(0,null,List.of(p,p))).isInstanceOf(RuntimeException.class);
 }
 @Test void rejects_nonfinite_and_out_of_bounds_coordinates(){
  assertThatThrownBy(()->new GardenUpdate.Placement(1,"x",Double.NaN,0)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new GardenUpdate.Placement(6,"x",0,0)).isInstanceOf(RuntimeException.class);
  assertThatThrownBy(()->new GardenUpdate.Placement(1,"x",1.1,0)).isInstanceOf(RuntimeException.class);
 }
}
