package fi.sisu.market.pricing;

import com.mongodb.client.MongoCollection;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.wager.WagerStatus;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.bson.Document;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.math.BigDecimal;
import java.math.MathContext;

/**
 * One-time upgrade of data written before the CPMM pricing model (predictions with {@code yesPool}/{@code noPool},
 * wagers without {@code shares}). Idempotent: safe to run on every start and to resume after a crash.
 *
 * <ol>
 *   <li>Legacy wagers had no defined payout, so each is refunded: its status flip, the balance credit (with
 *       ledger entry) and the removal of its stake from the legacy pool happen in one transaction.</li>
 *   <li>Each legacy prediction then gets a CPMM pool at the probability its remaining (seeded) pools implied.</li>
 * </ol>
 */
@ApplicationScoped
public class PricingMigration {

    private static final Logger LOG = Logger.getLogger(PricingMigration.class);

    @Inject
    WalletService wallet;

    @Inject
    PredictionRepository predictions;

    @ConfigProperty(name = "sisu.market.liquidity")
    long liquidity;

    void onStart(@Observes StartupEvent event) {
        run();
    }

    /** Runs the migration; returns {refunded wagers, converted predictions}. */
    public int[] run() {
        int refunded = refundLegacyWagers();
        int converted = convertLegacyPredictions();
        if (refunded + converted > 0) {
            LOG.infof("Pricing migration: refunded %d legacy wagers, converted %d predictions", refunded, converted);
        }
        return new int[] {refunded, converted};
    }

    private MongoCollection<Document> raw(String collection) {
        return predictions.mongoDatabase().getCollection(collection);
    }

    private int refundLegacyWagers() {
        int count = 0;
        for (Document wager : raw("wagers").find(Filters.exists("shares", false))) {
            ObjectId wagerId = wager.getObjectId("_id");
            ObjectId userId = wager.getObjectId("userId");
            ObjectId predictionId = wager.getObjectId("predictionId");
            long amount = ((Number) wager.get("amount")).longValue();
            String pool = Outcome.valueOf(wager.getString("side")) == Outcome.YES ? "yesPool" : "noPool";

            boolean done = wallet.inTransaction(session -> {
                // Claim the wager first; if another run already refunded it, do nothing.
                long claimed = raw("wagers").updateOne(session,
                        Filters.and(Filters.eq("_id", wagerId), Filters.exists("shares", false),
                                Filters.ne("status", WagerStatus.REFUNDED.name())),
                        Updates.combine(
                                Updates.set("status", WagerStatus.REFUNDED.name()),
                                Updates.set("shares", BigDecimal.ZERO),
                                Updates.set("payoutIfWin", 0L))).getModifiedCount();
                if (claimed == 0) {
                    return false;
                }
                raw("predictions").updateOne(session,
                        Filters.and(Filters.eq("_id", predictionId), Filters.exists(pool)),
                        Updates.inc(pool, -amount));
                wallet.apply(session, userId, amount, TransactionType.WAGER_REFUND,
                        "Refund: wager placed before payouts were defined", predictionId);
                return true;
            });
            if (done) {
                count++;
            }
        }
        return count;
    }

    private int convertLegacyPredictions() {
        int count = 0;
        for (Document p : raw("predictions").find(Filters.exists("yesPool"))) {
            long yes = ((Number) p.get("yesPool")).longValue();
            long no = ((Number) p.get("noPool")).longValue();
            BigDecimal probability = yes + no <= 0
                    ? new BigDecimal("0.5")
                    : BigDecimal.valueOf(yes).divide(BigDecimal.valueOf(yes + no), MathContext.DECIMAL64);
            Cpmm.Pool pool = Cpmm.initialPool(liquidity, probability);

            // All legacy wagers were refunded above, so no real Sisu is wagered on it.
            long modified = raw("predictions").updateOne(
                    Filters.and(Filters.eq("_id", p.getObjectId("_id")), Filters.exists("yesPool")),
                    Updates.combine(
                            Updates.set("poolYesShares", pool.yesShares()),
                            Updates.set("poolNoShares", pool.noShares()),
                            Updates.set("totalWagered", 0L),
                            Updates.unset("yesPool"),
                            Updates.unset("noPool"))).getModifiedCount();
            count += (int) modified;
        }
        return count;
    }
}
