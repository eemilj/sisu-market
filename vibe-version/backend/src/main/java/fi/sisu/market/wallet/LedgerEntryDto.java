package fi.sisu.market.wallet;

import java.time.Instant;

public record LedgerEntryDto(String id, TransactionType type, long amount, long balanceAfter,
                             String description, String predictionId, Instant createdAt) {

    public static LedgerEntryDto from(LedgerEntry e) {
        return new LedgerEntryDto(e.id.toHexString(), e.type, e.amount, e.balanceAfter, e.description,
                e.predictionId == null ? null : e.predictionId.toHexString(), e.createdAt);
    }
}
