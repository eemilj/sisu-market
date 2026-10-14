package fi.sisu.market.profile;

import java.util.List;

/** Newest first. Pass {@code nextCursor} as {@code before} for the next page; null when done. */
public record WagerHistoryPage(List<WagerHistoryItem> items, String nextCursor) {
}
