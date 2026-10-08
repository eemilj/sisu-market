package fi.sisu.market.wager;

import fi.sisu.market.prediction.PredictionDto;

/** Everything the UI needs to refresh after a wager: the wager, the new balance and the new odds. */
public record WagerResult(WagerDto wager, long balance, PredictionDto prediction) {
}
