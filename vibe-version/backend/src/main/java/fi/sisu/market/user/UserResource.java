package fi.sisu.market.user;

import fi.sisu.market.common.ApiException;
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

import java.util.List;

@Path("/api/users")
@Produces(MediaType.APPLICATION_JSON)
@Authenticated
public class UserResource {

    private static final int MAX_PAGE_SIZE = 100;

    @Inject
    CurrentUser currentUser;

    @Inject
    LedgerRepository ledger;

    @GET
    @Path("/me")
    public UserDto me() {
        return UserDto.from(currentUser.require());
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
}
