package fi.sisu.market.user;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.prediction.Prediction;
import fi.sisu.market.prediction.PredictionRepository;
import fi.sisu.market.profile.WagerHistoryItem;
import fi.sisu.market.profile.WagerHistoryPage;
import fi.sisu.market.wager.Wager;
import fi.sisu.market.wager.WagerDto;
import fi.sisu.market.wager.WagerRepository;
import fi.sisu.market.wallet.LedgerEntry;
import fi.sisu.market.wallet.LedgerEntryDto;
import fi.sisu.market.wallet.LedgerRepository;
import fi.sisu.market.wallet.TransactionPage;
import io.quarkus.security.Authenticated;
import jakarta.inject.Inject;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import org.bson.types.ObjectId;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    CurrentUser currentUser;

    @Inject
    LedgerRepository ledger;

    @Inject
    AdminPolicy adminPolicy;

    @Inject
    WagerRepository wagers;

    @Inject
    PredictionRepository predictions;

    @GET
    @Path("/me")
    public UserDto me() {
        User user = currentUser.require();
        return UserDto.from(user, adminPolicy.isAdmin(user));
    }

    /** The caller's balance history, newest first. */
    @GET
    @Path("/me/transactions")
    public TransactionPage transactions(@QueryParam("before") String before,
                                        @QueryParam("limit") @DefaultValue("20") int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        ObjectId cursor = null;
        if (before != null && !before.isBlank()) {
            if (!ObjectId.isValid(before)) {
                throw ApiException.badRequest("Invalid cursor");
            }
            cursor = new ObjectId(before);
        }

        User user = currentUser.require();
        // Fetch one extra to know whether another page exists.
        List<LedgerEntry> entries = ledger.page(user.id, cursor, limit + 1);
        boolean hasMore = entries.size() > limit;
        List<LedgerEntry> page = hasMore ? entries.subList(0, limit) : entries;
        String next = hasMore ? page.getLast().id.toHexString() : null;
        return new TransactionPage(page.stream().map(LedgerEntryDto::from).toList(), next);
    }

    /** The caller's own wagers, newest first. Private: other users' positions are not exposed. */
    @GET
    @Path("/me/wagers")
    public WagerHistoryPage myWagers(@QueryParam("before") String before,
                                     @QueryParam("limit") @DefaultValue("20") int limit) {
        if (limit < 1 || limit > MAX_PAGE_SIZE) {
            throw ApiException.badRequest("limit must be between 1 and " + MAX_PAGE_SIZE);
        }
        ObjectId cursor = null;
        if (before != null && !before.isBlank()) {
            if (!ObjectId.isValid(before)) {
                throw ApiException.badRequest("Invalid cursor");
            }
            cursor = new ObjectId(before);
        }
        User user = currentUser.require();
        List<Wager> found = wagers.page(user.id, cursor, limit + 1);
        boolean hasMore = found.size() > limit;
        List<Wager> page = hasMore ? found.subList(0, limit) : found;

        Map<ObjectId, Prediction> byId = predictions.byIds(page.stream().map(w -> w.predictionId).distinct().toList())
                .stream().collect(Collectors.toMap(p -> p.id, Function.identity()));
        Instant now = Instant.now();
        List<WagerHistoryItem> items = page.stream().map(w -> {
            Prediction p = byId.get(w.predictionId);
            return p == null
                    ? new WagerHistoryItem(WagerDto.from(w), "(deleted prediction)", null, null)
                    : new WagerHistoryItem(WagerDto.from(w), p.question, p.effectiveStatus(now), p.outcome);
        }).toList();
        return new WagerHistoryPage(items, hasMore ? page.getLast().id.toHexString() : null);
    }
}
