package fi.sisu.market.auth;

import fi.sisu.market.user.UserDto;

public record AuthResponse(String token, UserDto user) {
}
