package market.sisu.user;

/** FR-035: only {@code RESOLVER} may resolve predictions; granted out-of-band, never self-service. */
public enum Role {
    USER,
    RESOLVER
}
