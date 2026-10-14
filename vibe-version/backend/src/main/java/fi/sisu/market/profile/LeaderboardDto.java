package fi.sisu.market.profile;

import java.util.List;

/**
 * @param me the caller's own row when logged in and not already among {@code entries}; otherwise null
 */
public record LeaderboardDto(List<Entry> entries, Entry me) {

    /** {@code rank} is a competition rank: equal balances share a rank (1, 2, 2, 4...). */
    public record Entry(long rank, String username, long balance) {
    }
}
