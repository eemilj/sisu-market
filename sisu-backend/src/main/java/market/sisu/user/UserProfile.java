package market.sisu.user;

import java.util.List;
import market.sisu.prediction.PredictionSummary;
import market.sisu.wager.Wager;

/**
 * The logged-in user's own private profile shape returned by {@code GET /api/users/me}
 * (contracts/openapi.yaml {@code UserProfile}; FR-030).
 */
public record UserProfile(
        String id,
        String username,
        long balanceSisu,
        Role role,
        List<PredictionSummary> createdPredictions,
        List<Wager> wagers) {

    public static UserProfile of(User user, List<PredictionSummary> createdPredictions, List<Wager> wagers) {
        return new UserProfile(
                user.id.toHexString(), user.username, user.balanceSisu, user.role, createdPredictions, wagers);
    }
}
