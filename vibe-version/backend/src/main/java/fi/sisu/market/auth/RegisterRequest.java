package fi.sisu.market.auth;

import fi.sisu.market.common.InputRules;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Username and email are trimmed before validation. The password is used exactly as typed; its byte length and
 * other rules are checked by {@link fi.sisu.market.security.PasswordPolicy}.
 */
public record RegisterRequest(
        @NotBlank(message = "Username is required")
        @Pattern(regexp = "^[A-Za-z0-9_]{3,20}$",
                message = "Username must be 3-20 characters: letters, digits or underscore")
        String username,

        @NotBlank(message = "Email is required")
        // regexp adds the "has a domain with a TLD" rule the UI uses, reported as one Email violation.
        @Email(regexp = InputRules.EMAIL, message = "Email must be a valid address, e.g. name@example.fi")
        @Size(max = 254, message = "Email is too long")
        String email,

        @NotBlank(message = "Password is required")
        // Characters; non-ASCII characters take more than one byte, so PasswordPolicy also checks the bytes.
        @Size(min = 8, max = 72, message = "Password must be 8-72 characters")
        String password) {

    public RegisterRequest {
        username = InputRules.trim(username);
        email = InputRules.trim(email);
    }
}
