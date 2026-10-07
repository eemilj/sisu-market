package fi.sisu.vibe.service;

import static com.mongodb.client.model.Filters.eq;
import static com.mongodb.client.model.Filters.in;

import com.mongodb.client.model.Sorts;
import fi.sisu.vibe.api.Views.LeaderboardEntry;
import fi.sisu.vibe.api.Views.PredictionView;
import fi.sisu.vibe.api.Views.Profile;
import fi.sisu.vibe.api.Views.TxView;
import fi.sisu.vibe.api.Views.UserView;
import fi.sisu.vibe.api.Views.WagerView;
import fi.sisu.vibe.store.Db;
import fi.sisu.vibe.store.Docs;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.bson.Document;
import org.bson.types.ObjectId;

@ApplicationScoped
public class UserService {

    private final Db db;
    private final Clock clock;

    public UserService(Db db, Clock clock) {
        this.db = db;
        this.clock = clock;
    }

    public UserView me(String userId) {
        return Docs.user(userDoc(new ObjectId(userId)));
    }

    public List<TxView> transactions(String userId) {
        return db.transactions()
                .find(eq("userId", new ObjectId(userId)))
                .sort(Sorts.descending("createdAt", "_id"))
                .limit(200)
                .map(Docs::tx)
                .into(new ArrayList<>());
    }

    public List<LeaderboardEntry> leaderboard(int limit) {
        List<LeaderboardEntry> out = new ArrayList<>();
        for (Document d : db.users().find().sort(Sorts.descending("balance")).limit(limit)) {
            out.add(new LeaderboardEntry(out.size() + 1, d.getString("username"), Docs.amount(d, "balance")));
        }
        return out;
    }

    public Profile profile(String username) {
        Document user = db.users().find(eq("usernameKey", username.toLowerCase(Locale.ROOT))).first();
        if (user == null) {
            throw ApiException.notFound("User");
        }
        ObjectId id = user.getObjectId("_id");
        Instant now = clock.instant();

        List<Document> wagers = db.wagers().find(eq("userId", id))
                .sort(Sorts.descending("createdAt")).limit(100).into(new ArrayList<>());
        List<ObjectId> predictionIds = wagers.stream().map(w -> w.getObjectId("predictionId")).distinct().toList();
        Map<ObjectId, Document> pools = new HashMap<>();
        db.predictions().find(in("_id", predictionIds)).forEach(p -> pools.put(p.getObjectId("_id"), p));
        List<WagerView> wagerViews = wagers.stream()
                .map(w -> Docs.wager(w, pools.get(w.getObjectId("predictionId"))))
                .toList();

        List<PredictionView> created = db.predictions().find(eq("creatorId", id))
                .sort(Sorts.descending("createdAt")).limit(100)
                .map(p -> Docs.prediction(p, now)).into(new ArrayList<>());

        return new Profile(Docs.user(user), wagerViews, created);
    }

    private Document userDoc(ObjectId id) {
        Document user = db.users().find(eq("_id", id)).first();
        if (user == null) {
            throw ApiException.notFound("User");
        }
        return user;
    }
}
