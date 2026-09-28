package market.sisu.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/auth/register} body (contracts/openapi.yaml {@code RegisterRequest}). Bean
 * validation failures reach the client as {@code 422 VALIDATION_FAILED} via the global exception
 * mappers; {@link market.sisu.user.UserService} re-checks the same rules for callers that bypass
 * HTTP.
 */
public record RegisterRequest(
        @NotBlank String username, @Size(min = 8) String password) {}
