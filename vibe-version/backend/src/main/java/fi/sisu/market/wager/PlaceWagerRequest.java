package fi.sisu.market.wager;

import fi.sisu.market.prediction.Outcome;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;

/**
 * {@code amount} is a BigDecimal on purpose: Jackson would silently truncate 10.5 into a {@code long}.
 * Here a fractional amount is rejected with a clear message instead.
 */
public record PlaceWagerRequest(
        @NotNull(message = "Side is required (YES or NO)")
        Outcome side,

        @NotNull(message = "Amount is required")
        @Positive(message = "Amount must be greater than zero")
        @Digits(integer = 15, fraction = 0, message = "Amount must be a whole number of Sisu")
        BigDecimal amount) {
}
