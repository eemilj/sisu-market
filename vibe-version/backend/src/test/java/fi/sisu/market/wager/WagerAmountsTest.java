package fi.sisu.market.wager;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WagerAmountsTest {

    private static ErrorCode rejected(String amount) {
        return assertThrows(ApiException.class, () -> WagerAmounts.toWholeSisu(new BigDecimal(amount))).code();
    }

    private static ErrorCode rejectedParam(String raw) {
        return assertThrows(ApiException.class, () -> WagerAmounts.parse(raw)).code();
    }

    @Test
    void acceptsWholeAmountsInAnyEquivalentNotation() {
        assertEquals(10, WagerAmounts.toWholeSisu(new BigDecimal("10")));
        assertEquals(10, WagerAmounts.toWholeSisu(new BigDecimal("10.000")));
        assertEquals(1000, WagerAmounts.toWholeSisu(new BigDecimal("1e3")));
        assertEquals(1, WagerAmounts.toWholeSisu(BigDecimal.ONE));
        assertEquals(WagerAmounts.MAX, WagerAmounts.toWholeSisu(BigDecimal.valueOf(WagerAmounts.MAX)));
    }

    @Test
    void rejectsZeroNegativeAndMissing() {
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE, rejected("0"));
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE, rejected("0.000"));
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE, rejected("-5"));
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE, rejected("-0.5"));
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE,
                assertThrows(ApiException.class, () -> WagerAmounts.toWholeSisu(null)).code());
    }

    @Test
    void rejectsFractions() {
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejected("10.5"));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejected("0.1"));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejected("10.0000000001"));
    }

    @Test
    void rejectsAbsurdlyLargeAmountsCheaply() {
        assertEquals(ErrorCode.AMOUNT_TOO_LARGE, rejected(String.valueOf(WagerAmounts.MAX + 1)));
        assertEquals(ErrorCode.AMOUNT_TOO_LARGE, rejected("1e20"));
        assertEquals(ErrorCode.AMOUNT_TOO_LARGE, rejected("1e999999999"), "huge exponent must not blow up");
        ApiException e = assertThrows(ApiException.class, () -> WagerAmounts.toWholeSisu(new BigDecimal("1e13")));
        assertEquals(WagerAmounts.MAX, e.params().get("max"));
    }

    @Test
    void parsesQueryParameters() {
        assertEquals(42, WagerAmounts.parse(" 42 "));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejectedParam("abc"));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejectedParam("1,5"), "Finnish decimal comma is not a number here");
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejectedParam(""));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejectedParam(null));
        assertEquals(ErrorCode.AMOUNT_NOT_WHOLE, rejectedParam("1".repeat(41)), "overlong input");
        assertEquals(ErrorCode.AMOUNT_NOT_POSITIVE, rejectedParam("-1"));
    }
}
