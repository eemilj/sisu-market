package fi.sisu.market.security;

import fi.sisu.market.common.ApiError;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordPolicyTest {

    private static List<String> rules(String password, String username) {
        return PasswordPolicy.check(password, username).stream().map(ApiError.FieldError::rule).toList();
    }

    @Test
    void acceptsOrdinaryPasswords() {
        assertTrue(rules("salasana123", "matti").isEmpty());
        assertTrue(rules("Revontulet ja sauna!", "matti").isEmpty(), "spaces and punctuation are fine");
        assertTrue(rules("a".repeat(72), "matti").isEmpty(), "exactly 72 ASCII bytes");
    }

    @Test
    void rejectsShortBlankAndMissing() {
        assertEquals(List.of("Size"), rules("1234567", "matti"));
        assertEquals(List.of("NotBlank"), rules("        ", "matti"));
        assertEquals(List.of("NotBlank"), rules(null, "matti"));
    }

    @Test
    void countsCharactersNotUtf16Units() {
        // 8 emoji are 8 characters (16 UTF-16 units) — long enough.
        assertTrue(rules("😀".repeat(8), "matti").isEmpty());
        assertEquals(List.of("Size"), rules("😀".repeat(7), "matti"));
    }

    /** bcrypt ignores everything after 72 bytes; "ä" is 2 bytes, so 37 of them would silently be truncated. */
    @Test
    void limitsUtf8BytesNotCharacters() {
        assertTrue(rules("ä".repeat(36), "matti").isEmpty(), "36 x 2 bytes = 72");
        assertEquals(List.of("MaxBytes"), rules("ä".repeat(37), "matti"));
        assertEquals(List.of("MaxBytes"), rules("ä".repeat(72), "matti"), "72 characters but 144 bytes");
        assertEquals(73, PasswordPolicy.utf8Length("a".repeat(71) + "ä"));
    }

    @Test
    void rejectsUsernameAsPasswordIgnoringCaseAndSpaces() {
        assertEquals(List.of("SameAsUsername"), rules("Matti_Virtanen", "matti_virtanen"));
        assertEquals(List.of("SameAsUsername"), rules(" matti_virtanen ", "Matti_Virtanen"));
        assertTrue(rules("matti_virtanen1", "matti_virtanen").isEmpty());
    }

    @Test
    void reportsEveryProblemAtOnce() {
        assertEquals(List.of("Size", "SameAsUsername"), rules("abc", "abc"));
    }
}
