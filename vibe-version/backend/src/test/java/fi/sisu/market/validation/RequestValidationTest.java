package fi.sisu.market.validation;

import fi.sisu.market.auth.LoginRequest;
import fi.sisu.market.auth.RegisterRequest;
import fi.sisu.market.prediction.Category;
import fi.sisu.market.prediction.CreatePredictionRequest;
import fi.sisu.market.prediction.Outcome;
import fi.sisu.market.wager.PlaceWagerRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.hibernate.validator.messageinterpolation.ParameterMessageInterpolator;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server-side copy of every rule the UI checks. These run without Quarkus: the constraint annotations on
 * the request records are the rules the API enforces.
 */
class RequestValidationTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.byDefaultProvider().configure()
                .messageInterpolator(new ParameterMessageInterpolator())
                .buildValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    /** "field:Rule" for every violation. */
    private static Set<String> problems(Object request) {
        Set<String> out = new TreeSet<>();
        for (ConstraintViolation<Object> v : validator.validate(request)) {
            out.add(v.getPropertyPath() + ":" + v.getConstraintDescriptor().getAnnotation().annotationType().getSimpleName());
        }
        return out;
    }

    // ---- registration ----

    private static RegisterRequest register(String username, String email, String password) {
        return new RegisterRequest(username, email, password);
    }

    @Test
    void validRegistrationPasses() {
        assertTrue(problems(register("matti_v", "matti@example.fi", "salasana123")).isEmpty());
    }

    @Test
    void usernameRules() {
        assertTrue(problems(register("abc", "a@example.fi", "salasana123")).isEmpty(), "3 chars is the minimum");
        assertTrue(problems(register("a".repeat(20), "a@example.fi", "salasana123")).isEmpty(), "20 chars is the maximum");
        for (String bad : new String[] {"ab", "a".repeat(21), "matti v", "mätti", "matti-v", "matti!", "<script>"}) {
            assertEquals(Set.of("username:Pattern"), problems(register(bad, "a@example.fi", "salasana123")), bad);
        }
        assertTrue(problems(register("", "a@example.fi", "salasana123")).contains("username:NotBlank"));
    }

    @Test
    void usernameAndEmailAreTrimmedBeforeValidation() {
        RegisterRequest r = register("  matti_v  ", "  matti@example.fi ", "salasana123");
        assertEquals("matti_v", r.username());
        assertEquals("matti@example.fi", r.email());
        assertTrue(problems(r).isEmpty());
        assertEquals(" salasana123 ", register("m", "e", " salasana123 ").password(), "passwords are never altered");
    }

    @Test
    void emailMustHaveADomainWithATld() {
        for (String bad : new String[] {"matti", "matti@", "@example.fi", "x@localhost", "x@example.f", "matti @example.fi"}) {
            assertEquals(Set.of("email:Email"), problems(register("matti_v", bad, "salasana123")), bad);
        }
        assertTrue(problems(register("matti_v", "m.v+sisu@posti.example.fi", "salasana123")).isEmpty());
        // Over 254 characters in total (an over-long local part also breaks the address format).
        assertTrue(problems(register("matti_v", "a".repeat(250) + "@example.fi", "salasana123")).contains("email:Size"));
    }

    @Test
    void passwordLengthIsCheckedHereAndBytesInPasswordPolicy() {
        assertEquals(Set.of("password:Size"), problems(register("matti_v", "m@example.fi", "1234567")));
        assertTrue(problems(register("matti_v", "m@example.fi", "a".repeat(72))).isEmpty());
        assertEquals(Set.of("password:Size"), problems(register("matti_v", "m@example.fi", "a".repeat(73))));
        assertTrue(problems(register("matti_v", "m@example.fi", "        ")).contains("password:NotBlank"));
    }

    @Test
    void loginRejectsOversizedInputBeforeAnyHashing() {
        assertTrue(problems(new LoginRequest("matti", "x")).isEmpty(), "login doesn't reveal password rules");
        assertEquals(Set.of("password:Size"), problems(new LoginRequest("matti", "x".repeat(201))));
        assertEquals(Set.of("username:Size"), problems(new LoginRequest("m".repeat(101), "x")));
        assertEquals("matti", new LoginRequest("  matti ", "x").username());
    }

    // ---- predictions ----

    private static CreatePredictionRequest prediction(String question, String description, String criteria) {
        return new CreatePredictionRequest(question, description, criteria, Category.SPORTS, "2027-01-01T12:00:00Z");
    }

    @Test
    void validPredictionPasses() {
        assertTrue(problems(prediction("Voittaako Suomi MM-kultaa?", "Taustaa.", "KYLLÄ, jos Suomi voittaa.")).isEmpty());
    }

    @Test
    void lengthsCountTheTrimmedText() {
        assertEquals(Set.of("question:Size"),
                problems(prediction("     Lumi?      ", "d", "KYLLÄ, jos lunta tulee.")), "padding can't reach the minimum");
        assertEquals(Set.of("question:Size"), problems(prediction("K".repeat(201), "d", "KYLLÄ, jos lunta tulee.")));
        assertEquals(Set.of("resolutionCriteria:Size"), problems(prediction("Sataako lunta?", "d", "KYLLÄ")));
        assertEquals(Set.of("description:Size"), problems(prediction("Sataako lunta?", "d".repeat(2001), "KYLLÄ, jos lunta tulee.")));
        assertTrue(problems(prediction("Sataako lunta?", "   ", "KYLLÄ, jos lunta tulee.")).contains("description:NotBlank"));
    }

    @Test
    void controlCharactersAreRejected() {
        assertEquals(Set.of("question:Pattern"), problems(prediction("Sataako\nlunta huomenna?", "d", "KYLLÄ, jos sataa.")),
                "question is a single line");
        assertEquals(Set.of("question:Pattern"), problems(prediction("Sataako lunta\u0000 huomenna?", "d", "KYLLÄ, jos sataa.")));
        assertEquals(Set.of("question:Pattern"), problems(prediction("Sataako \u001B[31mlunta?", "d", "KYLLÄ, jos sataa.")));
        assertTrue(problems(prediction("Sataako lunta?", "Rivi 1\nRivi 2\tsarkain", "KYLLÄ, jos\nsataa.")).isEmpty(),
                "newlines and tabs are fine in longer text");
        assertEquals(Set.of("description:Pattern"), problems(prediction("Sataako lunta?", "bell\u0007", "KYLLÄ, jos sataa.")));
        assertEquals(Set.of("resolutionCriteria:Pattern"), problems(prediction("Sataako lunta?", "d", "KYLLÄ, jos\u0000 sataa.")));
    }

    @Test
    void categoryAndClosingDateAreRequired() {
        assertEquals(Set.of("category:NotNull"),
                problems(new CreatePredictionRequest("Sataako lunta?", "d", "KYLLÄ, jos sataa.", null, "2027-01-01T12:00:00Z")));
        assertTrue(problems(new CreatePredictionRequest("Sataako lunta?", "d", "KYLLÄ, jos sataa.", Category.WEATHER, " "))
                .contains("closesAt:NotBlank"));
        assertEquals(Set.of("closesAt:Size"), problems(new CreatePredictionRequest("Sataako lunta?", "d",
                "KYLLÄ, jos sataa.", Category.WEATHER, "2".repeat(65))));
    }

    // ---- wagers ----

    private static PlaceWagerRequest wager(Outcome side, String amount, Long minPayout, String requestId) {
        return new PlaceWagerRequest(side, amount == null ? null : new BigDecimal(amount), minPayout, requestId);
    }

    @Test
    void wagerRules() {
        assertTrue(problems(wager(Outcome.YES, "10", 15L, "0f8fad5b-d9cb-469f-a165-70867728950e")).isEmpty());
        assertTrue(problems(wager(Outcome.NO, "10", null, null)).isEmpty(), "minPayout and requestId are optional");
        assertEquals(Set.of("side:NotNull"), problems(wager(null, "10", null, null)));
        assertEquals(Set.of("amount:NotNull"), problems(wager(Outcome.YES, null, null, null)));
        assertEquals(Set.of("amount:Positive"), problems(wager(Outcome.YES, "0", null, null)));
        assertEquals(Set.of("amount:Positive"), problems(wager(Outcome.YES, "-3", null, null)));
        assertEquals(Set.of("amount:Digits"), problems(wager(Outcome.YES, "10.5", null, null)));
        assertEquals(Set.of("amount:Digits"), problems(wager(Outcome.YES, "1e14", null, null)));
        assertEquals(Set.of("minPayout:Min"), problems(wager(Outcome.YES, "10", -1L, null)));
        for (String badId : new String[] {"short", "has spaces in it", "a".repeat(65), "drop';--"}) {
            assertEquals(Set.of("requestId:Pattern"), problems(wager(Outcome.YES, "10", null, badId)), badId);
        }
    }
}
