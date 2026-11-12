package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import java.util.List;
import org.junit.jupiter.api.Test;
class DrawPolicyTest {
 @Test void selects_weight_boundaries(){var p=new DrawPolicy(List.of(new DrawPolicy.Entry("a",3),new DrawPolicy.Entry("b",1)));assertThat(p.select(0)).isEqualTo("a");assertThat(p.select(2)).isEqualTo("a");assertThat(p.select(3)).isEqualTo("b");}
 @Test void rejects_empty_duplicate_and_nonpositive_weights(){assertThatThrownBy(()->new DrawPolicy(List.of())).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new DrawPolicy(List.of(new DrawPolicy.Entry("a",0)))).isInstanceOf(IllegalArgumentException.class);assertThatThrownBy(()->new DrawPolicy(List.of(new DrawPolicy.Entry("a",1),new DrawPolicy.Entry("a",1)))).isInstanceOf(IllegalArgumentException.class);}
 @Test void bounds_random_input_and_cost(){var p=new DrawPolicy(List.of(new DrawPolicy.Entry("a",1)));assertThatThrownBy(()->p.select(1)).isInstanceOf(IllegalArgumentException.class);assertThat(DrawPolicy.cost(10)).isEqualTo(1000);assertThatThrownBy(()->DrawPolicy.cost(2)).isInstanceOf(IllegalArgumentException.class);}
}
