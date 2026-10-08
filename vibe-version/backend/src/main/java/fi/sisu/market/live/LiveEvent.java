package fi.sisu.market.live;

import org.bson.types.ObjectId;

/**
 * Something that changed and that connected browsers should hear about.
 *
 * @param type     SSE event name, e.g. {@code prediction.updated}
 * @param audience the only user allowed to receive it, or null for everyone
 * @param payload  serialised to JSON as the event data
 */
public record LiveEvent(String type, ObjectId audience, Object payload) {

    public static final String PREDICTION_CREATED = "prediction.created";
    public static final String PREDICTION_UPDATED = "prediction.updated";
    public static final String WALLET_TRANSACTION = "wallet.transaction";

    public static LiveEvent toEveryone(String type, Object payload) {
        return new LiveEvent(type, null, payload);
    }

    public static LiveEvent toUser(ObjectId userId, String type, Object payload) {
        return new LiveEvent(type, userId, payload);
    }

    public boolean visibleTo(ObjectId userId) {
        return audience == null || audience.equals(userId);
    }
}
