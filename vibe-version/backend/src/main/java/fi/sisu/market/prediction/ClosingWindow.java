package fi.sisu.market.prediction;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

/** When a new prediction may close. Pure; unit-tested at the boundaries. */
public final class ClosingWindow {

    /** Gives people a meaningful window to take positions. */
    public static final Duration MIN_OPEN_TIME = Duration.ofHours(1);
    /** Keeps markets resolvable within a sensible horizon. */
    public static final Duration MAX_OPEN_TIME = Duration.ofDays(5 * 365);

    private ClosingWindow() {
    }

    /**
     * Parses and checks a requested closing time.
     *
     * @param value ISO-8601 with an explicit offset, e.g. {@code 2027-01-01T12:00:00Z} or {@code ...+02:00}
     * @return the closing instant, at least {@link #MIN_OPEN_TIME} and at most {@link #MAX_OPEN_TIME} after {@code now}
     */
    public static Instant validate(String value, Instant now) {
        Instant closesAt = parse(value);
        Instant earliest = now.plus(MIN_OPEN_TIME);
        Instant latest = now.plus(MAX_OPEN_TIME);
        if (closesAt.isBefore(earliest)) {
            throw ApiException.badRequest(ErrorCode.CLOSING_TOO_SOON, "Closing date must be at least 1 hour in the future")
                    .with("earliest", earliest);
        }
        if (closesAt.isAfter(latest)) {
            throw ApiException.badRequest(ErrorCode.CLOSING_TOO_FAR, "Closing date must be within 5 years")
                    .with("latest", latest);
        }
        return closesAt;
    }

    /** Requires an explicit offset so the instant is unambiguous. */
    static Instant parse(String value) {
        if (value == null || value.isBlank() || value.length() > 64) {
            throw invalid();
        }
        try {
            return OffsetDateTime.parse(value.strip()).toInstant();
        } catch (DateTimeParseException e) {
            throw invalid();
        }
    }

    private static ApiException invalid() {
        return ApiException.badRequest(ErrorCode.CLOSING_DATE_INVALID,
                "Closing date must be an ISO-8601 date-time with time zone, e.g. 2027-01-01T12:00:00Z");
    }
}
