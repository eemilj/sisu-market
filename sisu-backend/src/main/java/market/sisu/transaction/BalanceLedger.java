package market.sisu.transaction;

import com.mongodb.client.ClientSession;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Updates;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import market.sisu.common.ApiException;
import market.sisu.common.Sisu;
import market.sisu.user.User;
import market.sisu.user.UserRepository;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;

/**
 * The only code path that changes {@code User.balanceSisu}. Every change writes the balance and a
 * matching ledger entry with the caller's {@link ClientSession}, so both land in the same MongoDB
 * transaction (data-model.md invariants 3 and 5: non-negative balance, ledger completeness).
 */
@ApplicationScoped
public class BalanceLedger {

    private static final FindOneAndUpdateOptions RETURN_UPDATED =
            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER);

    private final UserRepository users;
    private final TransactionRepository transactions;
    private final Clock clock;

    BalanceLedger(UserRepository users, TransactionRepository transactions, Clock clock) {
        this.users = users;
        this.transactions = transactions;
        this.clock = clock;
    }

    /**
     * Adds sisu to a live account's balance and records why.
     *
     * @return the balance after the credit
     */
    public Sisu credit(ClientSession session, ObjectId userId, Sisu amount, TransactionReason reason, Related related) {
        requirePositive(amount);
        User updated = users.mongoCollection()
                .findOneAndUpdate(
                        session, liveUser(userId), Updates.inc("balanceSisu", amount.amount()), RETURN_UPDATED);
        if (updated == null) {
            throw ApiException.notFound("USER_NOT_FOUND", "No such user");
        }
        record(session, userId, TransactionDirection.CREDIT, amount, reason, related);
        return Sisu.of(updated.balanceSisu);
    }

    /**
     * Removes sisu from a balance and records why. The sufficiency check and the decrement are a
     * single conditional write, so two concurrent debits can never both succeed against a balance
     * that only covers one of them (FR-009, FR-020).
     *
     * @return the balance after the debit
     * @throws ApiException {@code 422 INSUFFICIENT_BALANCE} if the balance does not cover the amount
     */
    public Sisu debit(ClientSession session, ObjectId userId, Sisu amount, TransactionReason reason, Related related) {
        requirePositive(amount);
        User updated = users.mongoCollection()
                .findOneAndUpdate(
                        session,
                        Filters.and(liveUser(userId), Filters.gte("balanceSisu", amount.amount())),
                        Updates.inc("balanceSisu", -amount.amount()),
                        RETURN_UPDATED);
        if (updated == null) {
            if (users.findById(session, userId).filter(u -> u.deletedAt == null).isEmpty()) {
                throw ApiException.notFound("USER_NOT_FOUND", "No such user");
            }
            throw ApiException.unprocessable("INSUFFICIENT_BALANCE", "Amount exceeds the current balance");
        }
        record(session, userId, TransactionDirection.DEBIT, amount, reason, related);
        return Sisu.of(updated.balanceSisu);
    }

    private void record(
            ClientSession session,
            ObjectId userId,
            TransactionDirection direction,
            Sisu amount,
            TransactionReason reason,
            Related related) {
        Transaction entry = new Transaction();
        entry.id = new ObjectId();
        entry.userId = userId;
        entry.direction = direction;
        entry.amountSisu = amount.amount();
        entry.reason = reason;
        entry.relatedWagerId = related.wagerId();
        entry.relatedPredictionId = related.predictionId();
        entry.createdAt = clock.instant();
        transactions.append(session, entry);
    }

    private static Bson liveUser(ObjectId userId) {
        return Filters.and(Filters.eq("_id", userId), Filters.eq("deletedAt", null));
    }

    private static void requirePositive(Sisu amount) {
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("Ledger amounts must be positive, got " + amount);
        }
    }

    /** The wager/prediction a ledger entry concerns; both {@code null} for a starting grant. */
    public record Related(ObjectId wagerId, ObjectId predictionId) {

        public static final Related NONE = new Related(null, null);
    }
}
