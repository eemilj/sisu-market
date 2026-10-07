package market.sisu.user;

import java.util.List;
import market.sisu.prediction.PredictionSummary;
import market.sisu.wager.Wager;
import org.eclipse.microprofile.openapi.annotations.media.Schema;

@Schema(name = "UserProfile")
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
