package fi.sisu.market.common;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;
import java.util.Map;

/**
 * Uniform JSON error body, e.g.
 * {@code {"status": 409, "code": "INSUFFICIENT_BALANCE", "message": "...", "params": {"balance": 750}}}.
 *
 * @param code        stable identifier; see {@link ErrorCode}
 * @param message     English, for developers and logs; clients should build user text from code and params
 * @param params      values for the client's message (optional)
 * @param fieldErrors per-field problems for {@code VALIDATION_FAILED} (optional)
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(int status, String code, String message, Map<String, Object> params,
                       List<FieldError> fieldErrors) {

    /**
     * @param field  request field, e.g. {@code question}
     * @param rule   the violated rule: the constraint name, e.g. {@code Size}, {@code NotBlank}, {@code Pattern}
     * @param params the rule's limits, e.g. {@code min}/{@code max}
     */
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    public record FieldError(String field, String rule, String message, Map<String, Object> params) {
    }

    public static ApiError of(int status, ErrorCode code, String message) {
        return new ApiError(status, code.name(), message, null, null);
    }
}
