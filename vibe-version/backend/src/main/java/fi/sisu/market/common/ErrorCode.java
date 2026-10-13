package fi.sisu.market.common;

/**
 * Stable, machine-readable error identifiers. Clients show their own (Finnish) text for each code; the English
 * {@code message} next to it is for developers and logs. Never rename a code: clients depend on it.
 */
public enum ErrorCode {
    // generic
    VALIDATION_FAILED,
    MALFORMED_BODY,
    INVALID_LIMIT,
    INVALID_CURSOR,
    INVALID_FILTER,
    HTTP_ERROR,
    /** Unexpected server failure; params.errorId identifies it in the server log. */
    INTERNAL_ERROR,
    PAYLOAD_TOO_LARGE,
    // auth & users
    USERNAME_TAKEN,
    EMAIL_TAKEN,
    ACCOUNT_EXISTS,
    INVALID_CREDENTIALS,
    /** params.retryAfterSeconds */
    TOO_MANY_LOGIN_ATTEMPTS,
    /** params.retryAfterSeconds */
    TOO_MANY_REGISTRATIONS,
    SESSION_INVALID,
    USER_NOT_FOUND,
    // predictions
    PREDICTION_NOT_FOUND,
    CLOSING_DATE_INVALID,
    CLOSING_TOO_SOON,
    CLOSING_TOO_FAR,
    // wagers
    AMOUNT_NOT_POSITIVE,
    AMOUNT_NOT_WHOLE,
    /** params.max */
    AMOUNT_TOO_LARGE,
    SIDE_INVALID,
    QUOTE_PARAMS_REQUIRED,
    INSUFFICIENT_BALANCE,
    PREDICTION_CLOSED,
    ODDS_MOVED,
    ODDS_CHANGED_RETRY,
    REQUEST_ID_REUSED,
    // resolution
    RESOLVE_NOT_ALLOWED,
    RESOLVE_NOT_ALLOWED_HAS_STAKE,
    PREDICTION_STILL_OPEN,
    ALREADY_RESOLVED
}
