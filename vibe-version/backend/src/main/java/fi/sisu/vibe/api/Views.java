package fi.sisu.vibe.api;

import fi.sisu.vibe.domain.PredictionStatus;
import fi.sisu.vibe.domain.Role;
import fi.sisu.vibe.domain.Side;
import fi.sisu.vibe.domain.TxReason;
import fi.sisu.vibe.domain.WagerStatus;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;

/** Request and response bodies of the REST API. */
public final class Views {

    private Views() {}

    // ---- requests ----

    public record Credentials(
            @NotBlank @Size(min = 3, max = 20) @Pattern(regexp = "^[A-Za-z0-9_]+$") String username,
            @NotBlank @Size(min = 8, max = 100) String password) {}

    public record NewPrediction(
            @NotBlank @Size(max = 200) String question,
            @Size(max = 2000) String description,
            @NotBlank @Size(max = 1000) String criteria,
            @NotNull Instant closesAt) {}

    public record NewWager(@NotNull Side side, @NotNull @Min(1) @Max(1_000_000_000) Long amount) {}

    public record Resolution(@NotNull Side outcome) {}

    // ---- responses ----

    public record UserView(String id, String username, long balance, Role role) {}

    public record Session(String token, UserView user) {}

    public record PredictionView(
            String id,
            String question,
            String description,
            String criteria,
            Instant closesAt,
            Instant createdAt,
            String creatorId,
            String creatorName,
            PredictionStatus status,
            long yesPool,
            long noPool,
            long totalPool,
            double yesProbability,
            Side outcome,
            int wagerCount) {}

    public record WagerView(
            String id,
            String predictionId,
            String question,
            String username,
            Side side,
            long amount,
            WagerStatus status,
            Long payout,
            Long potentialPayout,
            Instant createdAt) {}

    public record PredictionDetail(PredictionView prediction, List<WagerView> recentWagers) {}

    public record WagerResult(WagerView wager, PredictionView prediction, long balance) {}

    public record TxView(String id, long amount, long balanceAfter, TxReason reason, String predictionId, Instant createdAt) {}

    public record LeaderboardEntry(int rank, String username, long balance) {}

    public record Profile(UserView user, List<WagerView> wagers, List<PredictionView> predictions) {}

    public record ApiError(String code, String message) {}

    /** Pushed over the SSE stream. {@code type} is "prediction" or "ping". */
    public record LiveEvent(String type, PredictionView prediction) {}
}
