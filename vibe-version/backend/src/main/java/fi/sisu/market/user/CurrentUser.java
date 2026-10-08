package fi.sisu.market.user;

import fi.sisu.market.common.ApiException;
import fi.sisu.market.common.ErrorCode;
import jakarta.enterprise.context.RequestScoped;
import jakarta.inject.Inject;
import org.bson.types.ObjectId;
import org.eclipse.microprofile.jwt.JsonWebToken;

/** Resolves the authenticated caller's {@link User} from the JWT subject. */
@RequestScoped
public class CurrentUser {

    @Inject
    JsonWebToken jwt;

    @Inject
    UserRepository users;

    public User require() {
        String subject = jwt.getSubject();
        if (subject == null || !ObjectId.isValid(subject)) {
            throw ApiException.unauthorized(ErrorCode.SESSION_INVALID, "Invalid session");
        }
        return users.findByIdOptional(new ObjectId(subject))
                // Token is valid but the account is gone (e.g. database was reset).
                .orElseThrow(() -> ApiException.unauthorized(ErrorCode.SESSION_INVALID, "Account no longer exists"));
    }
}
