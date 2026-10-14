package fi.sisu.market.common;

/** Shared input rules, used by request records so every text field is checked the same way on the server. */
public final class InputRules {

    /** A local part, "@", and a domain containing a dot and a TLD of at least 2 characters (same rule as the UI). */
    public static final String EMAIL = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]{2,}$";

    /** Single-line text: no control characters at all (no newlines, tabs, NUL, escape sequences...). */
    public static final String SINGLE_LINE = "^[^\\p{Cntrl}]*$";

    /** Multi-line text: newlines and tabs allowed, other control characters not. */
    public static final String MULTI_LINE = "^(?:[^\\p{Cntrl}]|[\\n\\r\\t])*$";

    private InputRules() {
    }

    /** Trims surrounding whitespace, keeping null as null so @NotBlank/@NotNull still report it. */
    public static String trim(String s) {
        return s == null ? null : s.strip();
    }
}
