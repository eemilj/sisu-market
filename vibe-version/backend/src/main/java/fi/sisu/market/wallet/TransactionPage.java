package fi.sisu.market.wallet;

import java.util.List;

/** A page of history, newest first. Pass {@code nextCursor} as {@code before} to get the next page; null when done. */
public record TransactionPage(List<LedgerEntryDto> items, String nextCursor) {
}
