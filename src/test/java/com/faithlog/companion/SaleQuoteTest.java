package com.faithlog.companion;
import static org.assertj.core.api.Assertions.*;
import org.junit.jupiter.api.Test;
class SaleQuoteTest {
 @Test void retains_last_copy(){assertThat(SaleQuote.calculate(3,2,10).totalPoints()).isEqualTo(20);assertThatThrownBy(()->SaleQuote.calculate(3,3,10)).isInstanceOf(IllegalArgumentException.class);}
 @Test void rejects_overflow(){assertThatThrownBy(()->SaleQuote.calculate(Long.MAX_VALUE,2,Long.MAX_VALUE)).isInstanceOf(ArithmeticException.class);}
}
