package market.sisu.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import market.sisu.common.Sisu;
import org.junit.jupiter.api.Test;

class SisuTest {

    @Test
    void rejectsNegativeAmounts() {
        assertThrows(IllegalArgumentException.class, () -> Sisu.of(-1));
    }

    @Test
    void addsAndSubtracts() {
        assertEquals(Sisu.of(150), Sisu.of(100).plus(Sisu.of(50)));
        assertEquals(Sisu.of(50), Sisu.of(100).minus(Sisu.of(50)));
        assertEquals(Sisu.ZERO, Sisu.of(100).minus(Sisu.of(100)));
    }

    @Test
    void subtractingMoreThanAvailableFails() {
        assertThrows(IllegalArgumentException.class, () -> Sisu.of(10).minus(Sisu.of(11)));
    }

    @Test
    void overflowFailsInsteadOfWrapping() {
        assertThrows(ArithmeticException.class, () -> Sisu.of(Long.MAX_VALUE).plus(Sisu.of(1)));
    }

    @Test
    void comparisons() {
        assertTrue(Sisu.of(1).isPositive());
        assertFalse(Sisu.ZERO.isPositive());
        assertTrue(Sisu.of(10).isAtLeast(Sisu.of(10)));
        assertFalse(Sisu.of(9).isAtLeast(Sisu.of(10)));
        assertTrue(Sisu.of(1).compareTo(Sisu.of(2)) < 0);
        assertEquals("5 sisu", Sisu.of(5).toString());
    }
}
