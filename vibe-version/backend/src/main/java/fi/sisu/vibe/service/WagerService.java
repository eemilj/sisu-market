package fi.sisu.vibe.service;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.gte;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.vibe.api.Views.NewWager;
import fi.sisu.vibe.api.Views.WagerResult;
import fi.sisu.vibe.domain.Side;
import fi.sisu.vibe.domain.TxReason;
import fi.sisu.vibe.domain.WagerStatus;
import fi.sisu.vibe.store.Db;
import fi.sisu.vibe.store.Docs;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WagerService {

    private static final Logger LOG = Logger.getLogger(WagerService.class);
    private static final FindOneAndUpdateOptions AFTER = new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);

    private final Db db;
    private final Clock clock;
    private final LiveFeed feed;

    public WagerService(Db db, Clock clock, LiveFeed feed) {
        this.db = db;
        this.clock = clock;
        this.feed = feed;
    }

    /**
     * Debits the user, grows the pool and records the wager plus its transaction atomically. Both
     * the debit (balance >= amount) and the pool update (still open) are conditional updates, so
     * concurrent wagers can neither overdraw a balance nor slip in after closing.
     */
    public WagerResult place(String predictionId, NewWager req, String userId, String username) {
        ObjectId pid = Docs.parseId(predictionId).orElseThrow(() -> ApiException.notFound("Prediction"));
        ObjectId uid = new ObjectId(userId);
        long amount = req.amount();
        Instant now = clock.instant();

        try {
            WagerResult result = db.tx(s -> {
                Document user = db.users().findOneAndUpdate(
                        s, and(eq("_id", uid), gte("balance", amount)), Updates.inc("balance", -amount), AFTER);
                if (user == null) {
                    throw ApiException.badRequest("INSUFFICIENT_BALANCE", "Not enough sisu for this wager");
                }
                String pool = req.side() == Side.YES ? "yesPool" : "noPool";
                Document prediction = db.predictions().findOneAndUpdate(
                        s,
                        and(eq("_id", pid), eq("status", "OPEN"), gt("closesAt", Date.from(now))),
                        Updates.combine(Updates.inc(pool, amount), Updates.inc("wagerCount", 1)),
                        AFTER);
                if (prediction == null) {
                    if (db.predictions().countDocuments(s, eq("_id", pid)) == 0) {
                        throw ApiException.notFound("Prediction");
                    }
                    throw ApiException.conflict("PREDICTION_NOT_OPEN", "Prediction is no longer open for wagers");
                }

                Document wager = new Document("_id", new ObjectId())
                        .append("predictionId", pid)
                        .append("question", prediction.getString("question"))
                        .append("userId", uid)
                        .append("username", username)
                        .append("side", req.side().name())
                        .append("amount", amount)
                        .append("status", WagerStatus.OPEN.name())
                        .append("createdAt", Date.from(now));
                db.wagers().insertOne(s, wager);
                long balance = Docs.amount(user, "balance");
                db.transactions().insertOne(s, Docs.newTx(uid, -amount, balance, TxReason.WAGER, pid, now));
                return new WagerResult(Docs.wager(wager, prediction), Docs.prediction(prediction, now), balance);
            });
            feed.publish(result.prediction());
            return result;
        } catch (ApiException e) {
            LOG.infof("Wager rejected: user=%s prediction=%s amount=%d reason=%s", userId, predictionId, amount, e.code());
            throw e;
        }
    }
}
