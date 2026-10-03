package market.sisu.user;

/**
 * Leaderboard entry shape returned by {@code GET /api/leaderboard} (contracts/openapi.yaml
 * {@code LeaderboardEntry}).
 */
public record LeaderboardEntry(int rank, String username, long balanceSisu) {}
