package fi.sisu.market.security;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    /** Lowest legal cost keeps the test fast; the algorithm is the same. */
    private final PasswordHasher hasher = new PasswordHasher(4);

    @Test
    void storesSaltedBcryptNotThePassword() {
        String a = hasher.hash("salasana123");
        String b = hasher.hash("salasana123");
        assertTrue(a.matches("^\\$2[aby]?\\$04\\$[./A-Za-z0-9]{53}$"), a);
        assertFalse(a.contains("salasana"));
        assertNotEquals(a, b, "a fresh random salt every time");
    }

    @Test
    void verifiesOnlyTheRightPassword() {
        String hash = hasher.hash("salasana123");
        assertTrue(hasher.matches("salasana123", hash));
        assertFalse(hasher.matches("salasana124", hash));
        assertFalse(hasher.matches("Salasana123", hash), "case-sensitive");
        assertFalse(hasher.matches("", hash));
        assertFalse(hasher.matches(null, hash));
    }

    /** Regression: these two passwords share their first 72 bytes and both used to log in. */
    @Test
    void passwordsBeyond72BytesNeverMatch() {
        String stored = "ä".repeat(36); // exactly 72 bytes: allowed
        String hash = hasher.hash(stored);
        assertTrue(hasher.matches(stored, hash));
        assertFalse(hasher.matches(stored + "anything", hash), "would match if bcrypt truncated silently");
        assertThrows(IllegalArgumentException.class, () -> hasher.hash("ä".repeat(37)), "refuses to create such hashes");
    }

    @Test
    void missingOrCorruptHashesNeverMatch() {
        assertFalse(hasher.matches("salasana123", null));
        assertFalse(hasher.matches("salasana123", ""));
        assertFalse(hasher.matches("salasana123", "salasana123"), "plain-text 'hash'");
        assertFalse(hasher.matches("salasana123", "$2a$04$tooShort"));
    }

    @Test
    void detectsHashesThatNeedUpgrading() {
        PasswordHasher stronger = new PasswordHasher(5);
        String weak = hasher.hash("salasana123");
        assertTrue(stronger.needsRehash(weak));
        assertFalse(hasher.needsRehash(weak));
        assertFalse(stronger.needsRehash(stronger.hash("salasana123")));
        assertTrue(hasher.needsRehash("not-a-bcrypt-hash"));
        assertTrue(stronger.matches("salasana123", weak), "old hashes keep working until upgraded");
    }

    @Test
    void rejectsUnsafeCost() {
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(3));
        assertThrows(IllegalArgumentException.class, () -> new PasswordHasher(32));
    }
}
