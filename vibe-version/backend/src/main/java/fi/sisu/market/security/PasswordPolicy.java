package fi.sisu.market.security;

import fi.sisu.market.common.ApiError;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Rules for new passwords that bean validation can't express. Pure, so it is unit-tested directly.
 *
 * <p>The byte limit matters for security: bcrypt only looks at the first 72 <em>bytes</em>, so two different
 * long passwords sharing those bytes would both log in. "ä" is 2 bytes in UTF-8, so a 72-character limit is not
 * enough; this checks the encoded length.
 */
public final class PasswordPolicy {

    public static final int MIN_CHARS = 8;
    public static final int MAX_BYTES = 72;

    private PasswordPolicy() {
    }

    public static int utf8Length(String s) {
        return s.getBytes(StandardCharsets.UTF_8).length;
    }

    /** @return problems with {@code password} for an account named {@code username}; empty if acceptable */
    public static List<ApiError.FieldError> check(String password, String username) {
        List<ApiError.FieldError> problems = new ArrayList<>();
        if (password == null || password.isBlank()) {
            problems.add(problem("NotBlank", "Password is required", Map.of()));
            return problems;
        }
        if (password.codePointCount(0, password.length()) < MIN_CHARS) {
            problems.add(problem("Size", "Password must be at least " + MIN_CHARS + " characters", Map.of("min", MIN_CHARS)));
        }
        if (utf8Length(password) > MAX_BYTES) {
            problems.add(problem("MaxBytes", "Password must be at most " + MAX_BYTES + " bytes in UTF-8",
                    Map.of("max", MAX_BYTES)));
        }
        if (username != null && password.trim().toLowerCase(Locale.ROOT).equals(username.trim().toLowerCase(Locale.ROOT))) {
            problems.add(problem("SameAsUsername", "Password must not be the same as the username", Map.of()));
        }
        return problems;
    }

    private static ApiError.FieldError problem(String rule, String message, Map<String, Object> params) {
        return new ApiError.FieldError("password", rule, message, params);
    }
}
