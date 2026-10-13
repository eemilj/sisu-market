package fi.sisu.market.prediction;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ClosingWindowTest {

    private static final Instant NOW = Instant.parse("2026-10-09T12:00:00Z");

    private static ErrorCode rejected(String value) {
        return assertThrows(ApiException.class, () -> ClosingWindow.validate(value, NOW)).code();
    }

    @Test
    void acceptsTheExactBoundaries() {
        assertEquals(NOW.plus(Duration.ofHours(1)), ClosingWindow.validate("2026-10-09T13:00:00Z", NOW));
        Instant fiveYears = NOW.plus(ClosingWindow.MAX_OPEN_TIME);
        assertEquals(fiveYears, ClosingWindow.validate(fiveYears.toString(), NOW));
    }

    @Test
    void rejectsJustOutsideTheBoundaries() {
        assertEquals(ErrorCode.CLOSING_TOO_SOON, rejected("2026-10-09T12:59:59.999Z"));
        assertEquals(ErrorCode.CLOSING_TOO_SOON, rejected("2026-10-09T11:00:00Z"), "in the past");
        assertEquals(ErrorCode.CLOSING_TOO_FAR, rejected(NOW.plus(ClosingWindow.MAX_OPEN_TIME).plusSeconds(1).toString()));
    }

    @Test
    void tooSoonTellsTheEarliestAllowedTime() {
        ApiException e = assertThrows(ApiException.class, () -> ClosingWindow.validate("2026-10-09T12:30:00Z", NOW));
        assertEquals(NOW.plus(Duration.ofHours(1)), e.params().get("earliest"));
    }

    @Test
    void honoursTimeZoneOffsets() {
        // 15:30 in Helsinki summer time (+03:00) is 12:30 UTC: too soon. 16:00 +03:00 is exactly one hour.
        assertEquals(ErrorCode.CLOSING_TOO_SOON, rejected("2026-10-09T15:30:00+03:00"));
        assertEquals(NOW.plus(Duration.ofHours(1)), ClosingWindow.validate("2026-10-09T16:00:00+03:00", NOW));
    }

    @Test
    void rejectsAmbiguousOrInvalidDates() {
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected("2027-01-01T12:00:00"), "no offset");
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected("2027-02-30T12:00:00Z"), "no such day");
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected("31.12.2026"), "Finnish format is for display only");
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected(""));
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected(null));
        assertEquals(ErrorCode.CLOSING_DATE_INVALID, rejected("2027-01-01T12:00:00Z" + " ".repeat(100) + "x"));
        assertEquals(NOW.plus(Duration.ofDays(1)), ClosingWindow.validate("  2026-10-10T12:00:00Z  ", NOW), "surrounding spaces ok");
    }
}
