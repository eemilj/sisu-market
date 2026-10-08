package fi.sisu.market.user;

import java.time.Instant;

/** Public view of a user. Never exposes the password hash. */
public record UserDto(String id, String username, String email, long balance, Instant createdAt) {

    public static UserDto from(User user) {
        return new UserDto(user.id.toHexString(), user.username, user.email, user.balance, user.createdAt);
    }
}
