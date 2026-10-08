package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * {@code amount} is a BigDecimal on purpose: Jackson would silently truncate 10.5 into a {@code long}.
 * Here a fractional amount is rejected with a clear message instead.
 *
 * @param minPayout optional slippage guard: reject the wager if it would pay out less than this if it wins
 *                  (send the payout from the quote the user saw)
 * @param requestId optional idempotency key: repeating a request with the same key returns the original
 *                  wager instead of placing a second one
 */
public record PlaceWagerRequest(
        @NotNull(message = "Side is required (YES or NO)")
        Outcome side,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 0, message = "Amount must be a whole number of Sisu")
        BigDecimal amount,

        @Min(value = 0, message = "minPayout must not be negative")
        Long minPayout,

        @Pattern(regexp = "^[A-Za-z0-9-]{8,64}$", message = "requestId must be 8-64 letters, digits or dashes")
        String requestId) {
}
