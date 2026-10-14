package fi.sisu.market.profile;

import java.time.Instant;

/** Public view of a user. Balances are public because the leaderboard ranks by them. */
public record ProfileDto(String username, long balance, long rank, Instant joinedAt, int predictionsCreated,
                         boolean admin) {
}
