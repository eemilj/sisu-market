package fi.sisu.market.profile;

import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.prediction.PredictionStatus;
import fi.sisu.market.wager.WagerDto;

/** A wager with enough about its prediction to show it in a history list. */
public record WagerHistoryItem(WagerDto wager, String question, PredictionStatus predictionStatus, Outcome outcome) {
}
