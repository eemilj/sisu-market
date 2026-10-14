package fi.sisu.market.security;

import io.quarkus.elytron.security.common.BcryptUtil;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * bcrypt password hashing. Each hash records its own cost, so raising {@code sisu.bcrypt-cost} later still
 * verifies old hashes and {@link #needsRehash} tells the login flow to upgrade them.
 */
@ApplicationScoped
public class PasswordHasher {

    private static final Pattern BCRYPT = Pattern.compile("^\\$2[abxy]?\\$(\\d{2})\\$[./A-Za-z0-9]{53}$");

    private final int cost;
    /** Verified against when a user doesn't exist, so a miss takes as long as a wrong password. */
    private final String dummyHash;

    @Inject
    public PasswordHasher(@ConfigProperty(name = "sisu.bcrypt-cost", defaultValue = "12") int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("bcrypt cost must be 4-31, was " + cost);
        }
        this.cost = cost;
        this.dummyHash = BcryptUtil.bcryptHash("timing-equalizer-" + System.nanoTime(), cost);
    }

    public String hash(String password) {
        requireHashable(password);
        return BcryptUtil.bcryptHash(password, cost);
    }

    /**
     * Constant-ish time check. Passwords longer than bcrypt can represent never match (they could not have been
     * set under {@link PasswordPolicy}), and a null hash burns the same time as a real comparison.
     */
    public boolean matches(String password, String hash) {
        if (password == null || PasswordPolicy.utf8Length(password) > PasswordPolicy.MAX_BYTES) {
            BcryptUtil.matches("x", dummyHash);
            return false;
        }
        String target = hash != null && BCRYPT.matcher(hash).matches() ? hash : dummyHash;
        boolean ok = BcryptUtil.matches(password, target);
        // A "match" against the dummy hash (missing or corrupt stored hash) never counts.
        return ok && target == hash;
    }

    /** True if {@code hash} was made with a lower cost than configured (or isn't a bcrypt hash). */
    public boolean needsRehash(String hash) {
        Matcher m = hash == null ? null : BCRYPT.matcher(hash);
        return m == null || !m.matches() || Integer.parseInt(m.group(1)) < cost;
    }

    private static void requireHashable(String password) {
        if (password == null || PasswordPolicy.utf8Length(password) > PasswordPolicy.MAX_BYTES) {
            throw new IllegalArgumentException("Password must be 1-" + PasswordPolicy.MAX_BYTES + " bytes");
        }
    }
}
