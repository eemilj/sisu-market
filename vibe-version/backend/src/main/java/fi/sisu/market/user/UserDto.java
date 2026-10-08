package fi.sisu.market.user;

import java.time.Instant;

/** Public view of a user. Never exposes the password hash. */
public record UserDto(String id, String username, String email, long balance, long balanceVersion, Instant createdAt,
                      /** May resolve any prediction. */
                      boolean admin) {

    public static UserDto from(User user, boolean admin) {
        return new UserDto(user.id.toHexString(), user.username, user.email, user.balance, user.balanceVersion,
                user.createdAt, admin);
    }
}
