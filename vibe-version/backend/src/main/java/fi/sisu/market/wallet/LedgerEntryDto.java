package fi.sisu.market.wallet;

import java.time.Instant;

/**
 * @param description        English, for developers; clients build their own label from {@code type}
 * @param predictionQuestion the related prediction's question, so clients can show what the entry was for
 */
public record LedgerEntryDto(String id, TransactionType type, long amount, long balanceAfter,
                             String description, String predictionId, String predictionQuestion, Instant createdAt) {

    public static LedgerEntryDto from(LedgerEntry e, String predictionQuestion) {
        return new LedgerEntryDto(e.id.toHexString(), e.type, e.amount, e.balanceAfter, e.description,
                e.predictionId == null ? null : e.predictionId.toHexString(), predictionQuestion, e.createdAt);
    }
}
