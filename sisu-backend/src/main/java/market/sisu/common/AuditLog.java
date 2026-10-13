package market.sisu.common;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.function.Consumer;
import org.jboss.logging.Logger;

/**
 * Structured audit events for resolutions, cancellations and failed transactions (Constitution IV,
 * research.md section 9). Each event is one {@code event=<name> key=value ...} line on the
 * {@code market.sisu.audit} logger, so it can be grepped and traced after the fact.
 *
 * <p>Callers pass identifiers and counts only; this class never receives passwords, tokens or
 * password hashes. Values are stripped of whitespace so a value can never forge an extra field.
 */
@ApplicationScoped
public class AuditLog {

    private static final Logger LOG = Logger.getLogger("market.sisu.audit");
    private static final int MAX_VALUE_LENGTH = 200;

    private final Consumer<String> info;
    private final Consumer<String> warn;

    @Inject
    public AuditLog() {
        this(message -> LOG.info(message), message -> LOG.warn(message));
    }

    /** For tests: route lines to a capturing sink. */
    public AuditLog(Consumer<String> info, Consumer<String> warn) {
        this.info = info;
        this.warn = warn;
    }

    public void predictionResolved(
            String predictionId, String resolverId, String outcome, int winners, long totalPaidSisu) {
        info.accept(line(
                "prediction-resolved",
                "predictionId",
                predictionId,
                "resolverId",
                resolverId,
                "outcome",
                outcome,
                "winners",
                winners,
                "totalPaidSisu",
                totalPaidSisu));
    }

    public void predictionCancelled(
            String predictionId, String creatorId, String reason, int refundedWagers, long refundedSisu) {
        info.accept(line(
                "prediction-cancelled",
                "predictionId",
                predictionId,
                "creatorId",
                creatorId,
                "reason",
                reason,
                "refundedWagers",
                refundedWagers,
                "refundedSisu",
                refundedSisu));
    }

    public void accountDeleted(String userId, int ownWagersRefunded) {
        info.accept(line("account-deleted", "userId", userId, "ownWagersRefunded", ownWagersRefunded));
    }

    public void transactionFailed(String errorCode, String exception) {
        warn.accept(line("transaction-failed", "errorCode", errorCode, "exception", exception));
    }

    static String line(String event, Object... keyValues) {
        StringBuilder out = new StringBuilder("event=").append(event);
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out.append(' ').append(keyValues[i]).append('=').append(clean(keyValues[i + 1]));
        }
        return out.toString();
    }

    private static String clean(Object value) {
        String text = value == null ? "null" : value.toString().replaceAll("\\s+", "_");
        return text.length() > MAX_VALUE_LENGTH ? text.substring(0, MAX_VALUE_LENGTH) : text;
    }
}
