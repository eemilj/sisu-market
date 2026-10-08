package fi.sisu.market.wager;

import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import fi.sisu.market.common.ApiException;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionDto;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.wallet.LedgerEntry;
import fi.sisu.market.wallet.TransactionType;
import fi.sisu.market.wallet.WalletService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;

import java.time.Instant;

@ApplicationScoped
public class WagerService {

    @Inject
    WalletService wallet;

    @Inject
    PredictionRepository predictions;

    @Inject
    WagerRepository wagers;

    /**
     * Places a wager in one transaction: grow the chosen pool (only if the prediction is still open),
     * debit the user (only if they can afford it) and record the wager. Any failure rolls back all three.
     */
    public WagerResult place(ObjectId userId, ObjectId predictionId, Outcome side, long amount) {
        if (amount <= 0) {
            throw ApiException.badRequest("Amount must be greater than zero");
        }
        return wallet.inTransaction(session -> {
            // Evaluated per attempt: the driver may retry this block.
            Instant now = Instant.now();
            String pool = side == Outcome.YES ? "yesPool" : "noPool";
            Prediction updated = predictions.mongoCollection().findOneAndUpdate(session,
                    Filters.and(
                            Filters.eq("_id", predictionId),
                            Filters.eq("status", PredictionStatus.OPEN.name()),
                            Filters.gt("closesAt", now)),
                    Updates.inc(pool, amount),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            if (updated == null) {
                boolean exists = predictions.mongoCollection().countDocuments(session, Filters.eq("_id", predictionId)) > 0;
                throw exists
                        ? ApiException.conflict("This prediction is closed for wagering")
                        : ApiException.notFound("Prediction not found");
            }

            LedgerEntry debit = wallet.apply(session, userId, -amount, TransactionType.WAGER,
                    "Wager on " + side + ": " + updated.question, predictionId);

            Wager wager = new Wager();
            wager.id = new ObjectId();
            wager.userId = userId;
            wager.predictionId = predictionId;
            wager.side = side;
            wager.amount = amount;
            wager.ledgerEntryId = debit.id;
            wager.createdAt = now;
            wagers.mongoCollection().insertOne(session, wager);

            return new WagerResult(WagerDto.from(wager), debit.balanceAfter, PredictionDto.from(updated, now));
        });
    }
}
