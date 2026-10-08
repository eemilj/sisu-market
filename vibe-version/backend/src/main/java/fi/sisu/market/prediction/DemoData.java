package fi.sisu.market.prediction;

import fi.sisu.market.pricing.Cpmm;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Finnish-themed starter predictions, inserted on first boot so the app isn't empty. They differ from
 * user-created ones only in their opening probability; nobody has wagered on them yet.
 */
final class DemoData {

    private DemoData() {
    }

    static List<Prediction> predictions(long liquidity) {
        return List.of(
                p(liquidity, "Will Helsinki get its first snow cover before 15 November 2026?",
                        "The first lasting snow in the capital has arrived later in recent autumns.",
                        "YES if the Finnish Meteorological Institute reports snow cover at Helsinki Kaisaniemi before 15.11.2026.",
                        Category.WEATHER, "2026-11-15T00:00:00Z", "0.42"),
                p(liquidity, "Will Lake Kallavesi freeze over near Kuopio before New Year?",
                        "Ice cover on Kallavesi decides when the winter ice roads and skating tracks can open.",
                        "YES if SYKE ice observations report Kallavesi frozen at the Kuopio station before 1.1.2027.",
                        Category.WEATHER, "2027-01-01T00:00:00Z", "0.31"),
                p(liquidity, "Will Finland win gold at the 2027 IIHF Ice Hockey World Championship?",
                        "Can Leijonat take the men's world title again?",
                        "YES if Finland wins the final of the 2027 men's World Championship. Any other result is NO.",
                        Category.SPORTS, "2027-05-01T00:00:00Z", "0.26"),
                p(liquidity, "Will a Finnish player score 40+ goals in the 2026-27 NHL regular season?",
                        "Several Finnish forwards have come close to the 40-goal mark in recent seasons.",
                        "YES if any Finnish-born or Finnish-national player scores 40 or more regular-season goals. Playoffs don't count.",
                        Category.SPORTS, "2027-04-20T00:00:00Z", "0.45"),
                p(liquidity, "Will voter turnout exceed 70% in the April 2027 Finnish parliamentary election?",
                        "Turnout among citizens resident in Finland was 72% in 2023.",
                        "Resolves on the official turnout for Finnish citizens resident in Finland, as reported by the Ministry of Justice. YES if strictly above 70.0%.",
                        Category.POLITICS, "2027-04-18T00:00:00Z", "0.54"),
                p(liquidity, "Will Finland's unemployment rate (trend) drop below 9% in any month of H1 2027?",
                        "Unemployment has stayed high as the economy recovers slowly.",
                        "YES if Statistics Finland's trend unemployment rate for persons aged 15-74 is below 9.0% for any month from January to June 2027.",
                        Category.ECONOMY, "2027-06-30T00:00:00Z", "0.35"),
                p(liquidity, "Will the ECB deposit facility rate be 2.00% or lower on 31 March 2027?",
                        "Euro-area rates drive Finnish mortgage costs, since most of them track Euribor.",
                        "YES if the ECB deposit facility rate in effect on 31.3.2027 is 2.00% or lower.",
                        Category.ECONOMY, "2027-03-31T00:00:00Z", "0.61"),
                p(liquidity, "Will Finland finish in the top 10 at Eurovision 2027?",
                        "Finland's results have ranged from winning to last place.",
                        "Resolves on the final scoreboard of the Grand Final. Failing to qualify for the final resolves NO.",
                        Category.CULTURE, "2027-05-15T00:00:00Z", "0.48"));
    }

    private static Prediction p(long liquidity, String question, String description, String resolutionCriteria,
                                Category category, String closesAt, String openingYesProbability) {
        Prediction p = new Prediction();
        p.question = question;
        p.description = description;
        p.resolutionCriteria = resolutionCriteria;
        p.category = category;
        p.closesAt = Instant.parse(closesAt);
        p.status = PredictionStatus.OPEN;
        p.updatePool(Cpmm.initialPool(liquidity, new BigDecimal(openingYesProbability)));
        p.totalWagered = 0;
        p.createdAt = Instant.now();
        return p;
    }
}
