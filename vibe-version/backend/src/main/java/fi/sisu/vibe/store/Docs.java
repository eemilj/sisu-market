package fi.sisu.vibe.store;

import fi.sisu.vibe.api.Views.PredictionView;
import fi.sisu.vibe.api.Views.TxView;
import fi.sisu.vibe.api.Views.UserView;
import fi.sisu.vibe.api.Views.WagerView;
import fi.sisu.vibe.domain.Parimutuel;
import fi.sisu.vibe.domain.PredictionStatus;
import fi.sisu.vibe.domain.Role;
import fi.sisu.vibe.domain.Side;
import fi.sisu.vibe.domain.TxReason;
import fi.sisu.vibe.domain.WagerStatus;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import org.bson.Document;
import org.bson.types.ObjectId;

/** Mapping between MongoDB documents and API views. */
public final class Docs {

    private Docs() {}

    public static Optional<ObjectId> parseId(String id) {
        return id != null && ObjectId.isValid(id) ? Optional.of(new ObjectId(id)) : Optional.empty();
    }

    public static long amount(Document d, String field) {
        Object v = d.get(field);
        return v == null ? 0 : ((Number) v).longValue();
    }

    public static Instant instant(Document d, String field) {
        Date date = d.getDate(field);
        return date == null ? null : date.toInstant();
    }

    public static UserView user(Document d) {
        return new UserView(
                d.getObjectId("_id").toHexString(),
                d.getString("username"),
                amount(d, "balance"),
                Role.valueOf(d.getString("role")));
    }

    /** The status a reader should see: an OPEN prediction past its closing time is already CLOSED. */
    public static PredictionStatus effectiveStatus(Document d, Instant now) {
        PredictionStatus stored = PredictionStatus.valueOf(d.getString("status"));
        if (stored == PredictionStatus.OPEN && !instant(d, "closesAt").isAfter(now)) {
            return PredictionStatus.CLOSED;
        }
        return stored;
    }

    public static PredictionView prediction(Document d, Instant now) {
        long yes = amount(d, "yesPool");
        long no = amount(d, "noPool");
        String outcome = d.getString("outcome");
        return new PredictionView(
                d.getObjectId("_id").toHexString(),
                d.getString("question"),
                d.getString("description"),
                d.getString("criteria"),
                instant(d, "closesAt"),
                instant(d, "createdAt"),
                d.getObjectId("creatorId").toHexString(),
                d.getString("creatorName"),
                effectiveStatus(d, now),
                yes,
                no,
                yes + no,
                Parimutuel.yesProbability(yes, no),
                outcome == null ? null : Side.valueOf(outcome),
                d.getInteger("wagerCount", 0));
    }

    /** {@code pools} is the wager's prediction, used to quote the payout of still-open wagers. */
    public static WagerView wager(Document d, Document pools) {
        WagerStatus status = WagerStatus.valueOf(d.getString("status"));
        Side side = Side.valueOf(d.getString("side"));
        long amount = amount(d, "amount");
        Long payout = d.containsKey("payout") ? amount(d, "payout") : null;
        Long potential = null;
        if (status == WagerStatus.OPEN && pools != null) {
            long yes = amount(pools, "yesPool");
            long no = amount(pools, "noPool");
            potential = Parimutuel.payoutFor(amount, side == Side.YES ? yes : no, yes + no);
        }
        return new WagerView(
                d.getObjectId("_id").toHexString(),
                d.getObjectId("predictionId").toHexString(),
                d.getString("question"),
                d.getString("username"),
                side,
                amount,
                status,
                payout,
                potential,
                instant(d, "createdAt"));
    }

    public static TxView tx(Document d) {
        ObjectId predictionId = d.getObjectId("predictionId");
        return new TxView(
                d.getObjectId("_id").toHexString(),
                amount(d, "amount"),
                amount(d, "balanceAfter"),
                TxReason.valueOf(d.getString("reason")),
                predictionId == null ? null : predictionId.toHexString(),
                instant(d, "createdAt"));
    }

    public static Document newTx(ObjectId userId, long amount, long balanceAfter, TxReason reason, ObjectId predictionId, Instant at) {
        return new Document("userId", userId)
                .append("amount", amount)
                .append("balanceAfter", balanceAfter)
                .append("reason", reason.name())
                .append("predictionId", predictionId)
                .append("createdAt", Date.from(at));
    }
}
