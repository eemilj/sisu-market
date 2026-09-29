package market.sisu.user;

import org.bson.types.ObjectId;

/**
 * The account shape returned by register/login and the session check (contracts/openapi.yaml
 * {@code UserSummary}). Never carries the password hash. User Story 6 extends the profile
 * endpoint with wager history and created predictions; the balance fields stay as defined here.
 */
public record UserSummary(String id, String username, long balanceSisu) {

    public static UserSummary of(User user) {
        return new UserSummary(user.id.toHexString(), user.username, user.balanceSisu);
    }

    public ObjectId objectId() {
        return new ObjectId(id);
    }
}
