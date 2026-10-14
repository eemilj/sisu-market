package fi.sisu.market.user;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admins are configured by username ({@code sisu.admin-usernames}). Checked on every request rather than baked
 * into the JWT, so removing someone from the list takes effect immediately.
 */
@ApplicationScoped
public class AdminPolicy {

    private final Set<String> adminKeys;

    AdminPolicy(@ConfigProperty(name = "sisu.admin-usernames") Optional<List<String>> usernames) {
        this.adminKeys = usernames.orElse(List.of()).stream()
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(UserRepository::normalize)
                .collect(Collectors.toUnmodifiableSet());
    }

    public boolean isAdmin(User user) {
        return user != null && adminKeys.contains(user.usernameKey);
    }
}
