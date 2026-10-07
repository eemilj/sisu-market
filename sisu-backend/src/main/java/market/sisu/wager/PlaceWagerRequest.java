package market.sisu.wager;

import java.math.BigDecimal;
import market.sisu.prediction.Outcome;
import org.eclipse.microprofile.openapi.annotations.enums.SchemaType;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

/**
 * {@code POST /api/predictions/{id}/wagers} body (contracts/openapi.yaml {@code PlaceWagerRequest}).
 *
 * <p>{@code amountSisu} is read as a {@link BigDecimal} so that a fractional amount such as
 * {@code 1.5} reaches {@link WagerService#placeWager} intact and is rejected there (User Story 3,
 * scenario 5), rather than being silently truncated to {@code 1} by JSON number coercion.
 */
@Schema(
        name = "PlaceWagerRequest",
        requiredProperties = {"outcome", "amountSisu"})
public record PlaceWagerRequest(
        Outcome outcome,

        @Schema(type = SchemaType.INTEGER, format = "int64", minimum = "1")
        BigDecimal amountSisu) {}
