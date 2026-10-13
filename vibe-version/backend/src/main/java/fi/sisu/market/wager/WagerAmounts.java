package fi.sisu.market.wager;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;

import java.math.BigDecimal;

/** Turns a requested wager amount into whole Sisu, or explains why it can't be one. Pure; unit-tested. */
public final class WagerAmounts {

    /** Far above any reachable balance; keeps arithmetic and storage comfortably in range. */
    public static final long MAX = 1_000_000_000_000L;

    private WagerAmounts() {
    }

    /** Accepts e.g. {@code 10}, {@code 10.0} and {@code 1e3}; rejects {@code 0}, {@code -5}, {@code 10.5}, {@code 1e20}. */
    public static long toWholeSisu(BigDecimal amount) {
        if (amount == null) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount is required");
        }
        if (amount.signum() <= 0) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
        }
        // Compare before stripping: stripTrailingZeros on a huge exponent is cheap, but keep the order obvious.
        if (amount.compareTo(BigDecimal.valueOf(MAX)) > 0) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_TOO_LARGE, "Amount is too large").with("max", MAX);
        }
        if (amount.stripTrailingZeros().scale() > 0) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_WHOLE, "Amount must be a whole number of Sisu");
        }
        return amount.longValueExact();
    }

    /** For query parameters such as {@code ?amount=100}. */
    public static long parse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > 40) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_WHOLE, "Amount must be a whole number of Sisu");
        }
        try {
            return toWholeSisu(new BigDecimal(raw.strip()));
        } catch (NumberFormatException e) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_WHOLE, "Amount must be a whole number of Sisu");
        }
    }
}
