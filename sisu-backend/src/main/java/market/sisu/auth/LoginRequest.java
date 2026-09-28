package market.sisu.auth;

/**
 * {@code POST /api/auth/login} body (contracts/openapi.yaml {@code LoginRequest}). Deliberately
 * carries no bean-validation constraints: any wrong credential — including an empty one — must be
 * answered with the single generic {@code 401 INVALID_CREDENTIALS} (FR-003), never a 422.
 */
public record LoginRequest(String username, String password) {}
