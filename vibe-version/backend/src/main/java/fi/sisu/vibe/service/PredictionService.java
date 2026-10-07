package fi.sisu.vibe.service;

import static com.mongodb.client.model.Filters.and;
import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.gt;
import static com.mongodb.client.model.Filters.lte;
import static com.mongodb.client.model.Filters.ne;

import com.mongodb.client.model.FindOneAndUpdateOptions;
import com.mongodb.client.model.ReturnDocument;
import com.mongodb.client.model.Sorts;
import com.mongodb.client.model.Updates;
import fi.sisu.vibe.api.Views.NewPrediction;
import fi.sisu.vibe.api.Views.PredictionDetail;
import fi.sisu.vibe.api.Views.PredictionView;
import fi.sisu.vibe.api.Views.WagerView;
import fi.sisu.vibe.domain.Parimutuel;
import fi.sisu.vibe.domain.PredictionStatus;
import fi.sisu.vibe.domain.Side;
import fi.sisu.vibe.domain.TxReason;
import fi.sisu.vibe.domain.WagerStatus;
import fi.sisu.vibe.store.Db;
import fi.sisu.vibe.store.Docs;
import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;
import org.bson.Document;
import org.bson.conversions.Bson;
import org.bson.types.ObjectId;
import org.jboss.logging.Logger;

@ApplicationScoped
public class PredictionService {

    private static final Logger LOG = Logger.getLogger(PredictionService.class);
    private static final Duration MIN_OPEN_TIME = Duration.ofMinutes(5);

    private final Db db;
    private final Clock clock;
    private final LiveFeed feed;

    public PredictionService(Db db, Clock clock, LiveFeed feed) {
        this.db = db;
        this.clock = clock;
        this.feed = feed;
    }

    public PredictionView create(NewPrediction req, String userId, String username) {
        Instant now = clock.instant();
        if (req.closesAt().isBefore(now.plus(MIN_OPEN_TIME))) {
            throw ApiException.badRequest("CLOSING_DATE_PAST", "Closing time must be at least 5 minutes in the future");
        }
        Document doc = new Document("_id", new ObjectId())
                .append("question", req.question().strip())
                .append("description", req.description() == null ? "" : req.description().strip())
                .append("criteria", req.criteria().strip())
                .append("closesAt", Date.from(req.closesAt()))
                .append("createdAt", Date.from(now))
                .append("creatorId", new ObjectId(userId))
                .append("creatorName", username)
                .append("status", PredictionStatus.OPEN.name())
                .append("yesPool", 0L)
                .append("noPool", 0L)
                .append("wagerCount", 0);
        db.predictions().insertOne(doc);
        PredictionView view = Docs.prediction(doc, now);
        feed.publish(view);
        return view;
    }

    public List<PredictionView> list(PredictionStatus status) {
        Instant now = clock.instant();
        Date nowDate = Date.from(now);
        Bson filter;
        Bson sort;
        if (status == null) {
            filter = new Document();
            sort = Sorts.descending("createdAt");
        } else {
            filter = switch (status) {
                case OPEN -> and(eq("status", "OPEN"), gt("closesAt", nowDate));
                // Includes OPEN predictions whose closing time passed before the scheduler ran.
                case CLOSED -> and(ne("status", "RESOLVED"), lte("closesAt", nowDate));
                case RESOLVED -> eq("status", "RESOLVED");
            };
            sort = status == PredictionStatus.OPEN ? Sorts.ascending("closesAt") : Sorts.descending("closesAt");
        }
        return db.predictions().find(filter).sort(sort).limit(200)
                .map(d -> Docs.prediction(d, now)).into(new ArrayList<>());
    }

    public PredictionDetail detail(String id) {
        Document p = find(id);
        List<WagerView> wagers = db.wagers().find(eq("predictionId", p.getObjectId("_id")))
                .sort(Sorts.descending("createdAt")).limit(50)
                .map(w -> Docs.wager(w, p)).into(new ArrayList<>());
        return new PredictionDetail(Docs.prediction(p, clock.instant()), wagers);
    }

    /**
     * Resolves a closed prediction and pays out, all in one transaction. The status flip to RESOLVED
     * is conditional, so a retried or concurrent resolve can never pay out twice.
     */
    public PredictionView resolve(String id, Side outcome, String userId, boolean isAdmin) {
        Document existing = find(id);
        Instant now = clock.instant();
        if (!isAdmin && !existing.getObjectId("creatorId").toHexString().equals(userId)) {
            throw new ApiException(403, "NOT_ALLOWED", "Only the creator or an admin can resolve this prediction");
        }
        PredictionStatus status = Docs.effectiveStatus(existing, now);
        if (status == PredictionStatus.RESOLVED) {
            throw ApiException.conflict("ALREADY_RESOLVED", "Prediction is already resolved");
        }
        if (status == PredictionStatus.OPEN) {
            throw ApiException.conflict("NOT_CLOSED", "Prediction can be resolved only after it closes");
        }

        ObjectId pid = existing.getObjectId("_id");
        Document resolved = db.tx(s -> {
            Document p = db.predictions().findOneAndUpdate(
                    s,
                    and(eq("_id", pid), ne("status", "RESOLVED"), lte("closesAt", Date.from(now))),
                    Updates.combine(
                            Updates.set("status", PredictionStatus.RESOLVED.name()),
                            Updates.set("outcome", outcome.name()),
                            Updates.set("resolvedAt", Date.from(now))),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            if (p == null) {
                throw ApiException.conflict("ALREADY_RESOLVED", "Prediction is already resolved");
            }

            List<Document> wagers = db.wagers().find(s, and(eq("predictionId", pid), eq("status", "OPEN")))
                    .into(new ArrayList<>());
            List<Parimutuel.Stake> stakes = wagers.stream()
                    .map(w -> new Parimutuel.Stake(
                            w.getObjectId("_id").toHexString(), Side.valueOf(w.getString("side")), Docs.amount(w, "amount")))
                    .toList();
            boolean refund = Parimutuel.isRefund(stakes, outcome);
            Map<String, Long> payouts = Parimutuel.settle(stakes, outcome);

            for (Document w : wagers) {
                long payout = payouts.get(w.getObjectId("_id").toHexString());
                WagerStatus ws = refund ? WagerStatus.REFUNDED
                        : Side.valueOf(w.getString("side")) == outcome ? WagerStatus.WON : WagerStatus.LOST;
                db.wagers().updateOne(s, eq("_id", w.getObjectId("_id")),
                        Updates.combine(Updates.set("status", ws.name()), Updates.set("payout", payout)));
                if (payout > 0) {
                    ObjectId uid = w.getObjectId("userId");
                    Document user = db.users().findOneAndUpdate(s, eq("_id", uid), Updates.inc("balance", payout),
                            new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
                    if (user != null) {
                        db.transactions().insertOne(s, Docs.newTx(uid, payout, Docs.amount(user, "balance"),
                                refund ? TxReason.REFUND : TxReason.PAYOUT, pid, now));
                    }
                }
            }
            LOG.infof("Resolved prediction %s as %s by %s: %d wagers, refund=%s", pid, outcome, userId, wagers.size(), refund);
            return p;
        });
        PredictionView view = Docs.prediction(resolved, now);
        feed.publish(view);
        return view;
    }

    /** Persists the CLOSED status for predictions past their closing time. Reads never depend on it. */
    @Scheduled(every = "30s", delayed = "5s")
    void closeExpired() {
        Instant now = clock.instant();
        List<Document> expired = db.predictions()
                .find(and(eq("status", "OPEN"), lte("closesAt", Date.from(now))))
                .into(new ArrayList<>());
        for (Document p : expired) {
            Document closed = db.predictions().findOneAndUpdate(
                    and(eq("_id", p.getObjectId("_id")), eq("status", "OPEN")),
                    Updates.set("status", PredictionStatus.CLOSED.name()),
                    new FindOneAndUpdateOptions().returnDocument(ReturnDocument.AFTER));
            if (closed != null) {
                LOG.infof("Closed prediction %s", p.getObjectId("_id"));
                feed.publish(Docs.prediction(closed, now));
            }
        }
    }

    Document find(String id) {
        return Docs.parseId(id)
                .map(oid -> db.predictions().find(eq("_id", oid)).first())
                .orElseThrow(() -> ApiException.notFound("Prediction"));
    }
}
