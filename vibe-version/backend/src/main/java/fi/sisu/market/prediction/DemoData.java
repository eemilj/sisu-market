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

    /**
     * @param legacyQuestion the English question earlier versions seeded, used to translate existing databases
     */
    record Seed(String legacyQuestion, String question, String description, String resolutionCriteria,
                Category category, String closesAt, String openingYesProbability) {
    }

    static final List<Seed> SEEDS = List.of(
            new Seed("Will Helsinki get its first snow cover before 15 November 2026?",
                    "Saako Helsinki ensimmäisen lumipeitteensä ennen 15.11.2026?",
                    "Pysyvä lumi on tullut pääkaupunkiin viime syksyinä yhä myöhemmin.",
                    "KYLLÄ, jos Ilmatieteen laitos raportoi lumipeitteen Helsingin Kaisaniemessä ennen 15.11.2026.",
                    Category.WEATHER, "2026-10-12T00:00:00Z", "0.42"),
            new Seed("Will Lake Kallavesi freeze over near Kuopio before New Year?",
                    "Jäätyykö Kallavesi Kuopion edustalla ennen uuttavuotta?",
                    "Kallaveden jääpeite ratkaisee, milloin talven jäätiet ja luistelureitit voidaan avata.",
                    "KYLLÄ, jos Suomen ympäristökeskuksen jäähavainnot ilmoittavat Kallaveden jäätyneen Kuopion havaintopaikalla ennen 1.1.2027.",
                    Category.WEATHER, "2027-01-01T00:00:00Z", "0.31"),
            new Seed("Will Finland win gold at the 2027 IIHF Ice Hockey World Championship?",
                    "Voittaako Suomi kultaa jääkiekon MM-kisoissa 2027?",
                    "Pystyvätkö Leijonat voittamaan miesten maailmanmestaruuden jälleen?",
                    "KYLLÄ, jos Suomi voittaa miesten MM-kisojen 2027 loppuottelun. Mikä tahansa muu tulos ratkeaa EI.",
                    Category.SPORTS, "2027-05-01T00:00:00Z", "0.26"),
            new Seed("Will a Finnish player score 40+ goals in the 2026-27 NHL regular season?",
                    "Tekeekö joku suomalaispelaaja vähintään 40 maalia NHL:n runkosarjassa 2026–27?",
                    "Useat suomalaishyökkääjät ovat viime kausina olleet lähellä 40 maalin rajaa.",
                    "KYLLÄ, jos kuka tahansa Suomessa syntynyt tai Suomen kansalaisuuden omaava pelaaja tekee runkosarjassa vähintään 40 maalia. Pudotuspelejä ei lasketa.",
                    Category.SPORTS, "2027-04-20T00:00:00Z", "0.45"),
            new Seed("Will voter turnout exceed 70% in the April 2027 Finnish parliamentary election?",
                    "Nouseeko äänestysaktiivisuus yli 70 prosentin huhtikuun 2027 eduskuntavaaleissa?",
                    "Suomessa asuvien Suomen kansalaisten äänestysaktiivisuus oli 72 % vuonna 2023.",
                    "Ratkeaa oikeusministeriön ilmoittaman Suomessa asuvien Suomen kansalaisten virallisen äänestysaktiivisuuden mukaan. KYLLÄ, jos se on yli 70,0 %.",
                    Category.POLITICS, "2027-04-18T00:00:00Z", "0.54"),
            new Seed("Will Finland's unemployment rate (trend) drop below 9% in any month of H1 2027?",
                    "Laskeeko Suomen työttömyysasteen trendi alle 9 prosentin jonakin kuukautena tammi–kesäkuussa 2027?",
                    "Työttömyys on pysynyt korkeana talouden toipuessa hitaasti.",
                    "KYLLÄ, jos Tilastokeskuksen 15–74-vuotiaiden työttömyysasteen trendi on alle 9,0 % jonakin kuukautena tammi–kesäkuussa 2027.",
                    Category.ECONOMY, "2027-06-30T00:00:00Z", "0.35"),
            new Seed("Will the ECB deposit facility rate be 2.00% or lower on 31 March 2027?",
                    "Onko EKP:n talletuskorko enintään 2,00 % 31.3.2027?",
                    "Euroalueen korot vaikuttavat suomalaisten asuntolainojen hintaan, koska useimmat niistä on sidottu euriboriin.",
                    "KYLLÄ, jos 31.3.2027 voimassa oleva EKP:n talletuskorko on enintään 2,00 %.",
                    Category.ECONOMY, "2027-03-31T00:00:00Z", "0.61"),
            new Seed("Will Finland finish in the top 10 at Eurovision 2027?",
                    "Sijoittuuko Suomi Euroviisujen 2027 kymmenen parhaan joukkoon?",
                    "Suomen sijoitukset ovat vaihdelleet voitosta viimeiseen sijaan.",
                    "Ratkeaa finaalin lopullisten pisteiden mukaan. Jos Suomi ei pääse finaaliin, ennuste ratkeaa EI.",
                    Category.CULTURE, "2027-05-15T00:00:00Z", "0.48"));

    private DemoData() {
    }

    static List<Prediction> predictions(long liquidity) {
        return SEEDS.stream().map(s -> {
            Prediction p = new Prediction();
            p.question = s.question();
            p.description = s.description();
            p.resolutionCriteria = s.resolutionCriteria();
            p.category = s.category();
            p.closesAt = Instant.parse(s.closesAt());
            p.status = PredictionStatus.OPEN;
            p.updatePool(Cpmm.initialPool(liquidity, new BigDecimal(s.openingYesProbability())));
            p.totalWagered = 0;
            p.createdAt = Instant.now();
            return p;
        }).toList();
    }
}
