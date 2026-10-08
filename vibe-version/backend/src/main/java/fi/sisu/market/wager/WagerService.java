package fi.sisu.market.wager;

import com.mongodb.ErrorCategory;
import com.mongodb.MongoException;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import fi.sisu.market.live.EventHub;
import fi.sisu.market.live.LiveEvent;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.pricing.Cpmm;
import fi.sisu.market.user.UserRepository;
import fi.sisu.market.wallet.LedgerEntry;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

import java.time.Instant;

@ApplicationScoped
public class WagerService {

    /** Result of {@link #place}: {@code replayed} is true when an idempotent retry returned an earlier wager. */
    public record Placement(WagerResult result, boolean replayed) {
    }

    @Inject
    WalletService wallet;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    @Inject
    UserRepository users;

    @Inject
    EventHub events;

    /** Prices a wager without placing it. */
    public QuoteDto quote(ObjectId predictionId, Outcome side, long amount) {
        requirePositive(amount);
        Prediction p = predictions.findByIdOptional(predictionId)
                .orElseThrow(() -> ApiException.notFound(ErrorCode.PREDICTION_NOT_FOUND, "Prediction not found"));
        requireOpen(p, Instant.now());
        return QuoteDto.from(p.pool(), Cpmm.buy(p.pool(), side, amount));
    }

    /**
     * Places a wager. One transaction prices it against the current pool, updates the pool and the
     * prediction's total, debits the user's balance (with a ledger entry) and records the wager. Any failure,
     * including a crash part-way, leaves none of these changes behind.
     */
    public Placement place(ObjectId userId, ObjectId predictionId, Outcome side, long amount,
                           Long minPayout, String requestId) {
        requirePositive(amount);
        if (requestId != null) {
            var earlier = wagers.findByRequestId(userId, requestId);
            if (earlier.isPresent()) {
                return replay(earlier.get(), predictionId, side, amount);
            }
        }

        try {
            WagerResult result = wallet.inTransaction(session -> {
                // Evaluated per attempt: the driver may retry this block after a write conflict.
                Instant now = Instant.now();
                Prediction p = predictions.mongoCollection().find(session, Filters.eq("_id", predictionId)).first();
                if (p == null) {
                    throw ApiException.notFound(ErrorCode.PREDICTION_NOT_FOUND, "Prediction not found");
                }
                requireOpen(p, now);

                Cpmm.Trade trade = Cpmm.buy(p.pool(), side, amount);
                if (minPayout != null && trade.payoutIfWin() < minPayout) {
                    throw ApiException.conflict(ErrorCode.ODDS_MOVED, "The odds have moved: this wager would now pay "
                                    + trade.payoutIfWin() + " Sisu if it wins, below the " + minPayout
                                    + " Sisu you were shown. Review and try again.")
                            .with("payoutIfWin", trade.payoutIfWin())
                            .with("minPayout", minPayout);
                }

                // Compare-and-set on the pool we priced against; open/close is re-checked atomically too.
                Bson unchanged = Filters.and(
                        Filters.eq("_id", predictionId),
                        Filters.eq("status", PredictionStatus.OPEN.name()),
                        Filters.gt("closesAt", now),
                        Filters.eq("poolYesShares", p.poolYesShares),
                        Filters.eq("poolNoShares", p.poolNoShares));
                long matched = predictions.mongoCollection().updateOne(session, unchanged, Updates.combine(
                        Updates.set("poolYesShares", trade.poolAfter().yesShares()),
                        Updates.set("poolNoShares", trade.poolAfter().noShares()),
                        Updates.inc("totalWagered", amount),
                        Updates.inc("version", 1))).getMatchedCount();
                if (matched == 0) {
                    throw ApiException.conflict(ErrorCode.ODDS_CHANGED_RETRY, "The odds changed while placing your wager. Please try again.");
                }

                LedgerEntry debit = wallet.apply(session, userId, -amount, TransactionType.WAGER,
                        "Wager on " + side + ": " + p.question, predictionId);

                Wager wager = new Wager();
                wager.id = new ObjectId();
                wager.userId = userId;
                wager.predictionId = predictionId;
                wager.side = side;
                wager.amount = amount;
                wager.shares = trade.shares();
                wager.payoutIfWin = trade.payoutIfWin();
                wager.status = WagerStatus.OPEN;
                wager.requestId = requestId;
                wager.ledgerEntryId = debit.id;
                wager.createdAt = now;
                wagers.insert(session, wager);

                p.updatePool(trade.poolAfter());
                p.totalWagered += amount;
                p.version++;
                return new WagerResult(WagerDto.from(wager), debit.balanceAfter, debit.balanceVersion, PredictionDto.from(p, now));
            });
            // After commit: everyone watching sees the new odds and total.
            events.publish(LiveEvent.toEveryone(LiveEvent.PREDICTION_UPDATED, result.prediction()));
            return new Placement(result, false);
        } catch (MongoException e) {
            // Two concurrent requests with the same key: the unique index let only one commit.
            if (requestId != null && ErrorCategory.fromErrorCode(e.getCode()) == ErrorCategory.DUPLICATE_KEY) {
                var earlier = wagers.findByRequestId(userId, requestId);
                if (earlier.isPresent()) {
                    return replay(earlier.get(), predictionId, side, amount);
                }
            }
            throw e;
        }
    }

    private Placement replay(Wager earlier, ObjectId predictionId, Outcome side, long amount) {
        if (!earlier.predictionId.equals(predictionId) || earlier.side != side || earlier.amount != amount) {
            throw ApiException.conflict(ErrorCode.REQUEST_ID_REUSED, "This requestId was already used for a different wager");
        }
        var user = users.findById(earlier.userId);
        Prediction p = predictions.findById(predictionId);
        return new Placement(new WagerResult(WagerDto.from(earlier), user.balance, user.balanceVersion,
                PredictionDto.from(p, Instant.now())), true);
    }

    private static void requirePositive(long amount) {
        if (amount <= 0) {
            throw ApiException.badRequest(ErrorCode.AMOUNT_NOT_POSITIVE, "Amount must be greater than zero");
        }
    }

    private static void requireOpen(Prediction p, Instant now) {
        if (p.effectiveStatus(now) != PredictionStatus.OPEN) {
            throw ApiException.conflict(ErrorCode.PREDICTION_CLOSED, "This prediction is closed for wagering");
        }
    }
}
