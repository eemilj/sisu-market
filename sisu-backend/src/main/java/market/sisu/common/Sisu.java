package market.sisu.common;

/**
 * An amount of sisu, the platform's play-money currency. Always a non-negative whole number
 * (Constitution II: never floating point). Persisted entities store the raw {@code long}; this
 * type is used in business logic so overflow and negative amounts fail loudly.
 */
public record Sisu(long amount) implements Comparable<Sisu> {

    public static final Sisu ZERO = new Sisu(0);

    public Sisu {
        if (amount < 0) {
            throw new IllegalArgumentException("Sisu amount must not be negative: " + amount);
        }
    }

    public static Sisu of(long amount) {
        return new Sisu(amount);
    }

    public Sisu plus(Sisu other) {
        return new Sisu(Math.addExact(amount, other.amount));
    }

    /** @throws IllegalArgumentException if {@code other} is larger than this amount */
    public Sisu minus(Sisu other) {
        return new Sisu(Math.subtractExact(amount, other.amount));
    }

    public boolean isPositive() {
        return amount > 0;
    }

    public boolean isAtLeast(Sisu other) {
        return amount >= other.amount;
    }

    @Override
    public int compareTo(Sisu other) {
        return Long.compare(amount, other.amount);
    }

    @Override
    public String toString() {
        return amount + " sisu";
    }
}
