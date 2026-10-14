package fi.sisu.market.admin;

import fi.sisu.market.user.User;

import java.time.Instant;

/** Row of the admin user list. Never exposes the password hash. */
public record AdminUserDto(String id, String username, String email, long balance, Instant createdAt, boolean admin) {

    public static AdminUserDto from(User user, boolean admin) {
        return new AdminUserDto(user.id.toHexString(), user.username, user.email, user.balance, user.createdAt, admin);
    }
}
