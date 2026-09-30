package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.response.ValidatableResponse;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * {@code POST /api/predictions} against contracts/openapi.yaml: a logged-in user creates an open
 * prediction attributed to them that shows up in the list; empty fields and a closing date that is
 * not in the future are rejected with a specific error (User Story 4; FR-006, FR-012 through
 * FR-015).
 */
@QuarkusTest
class CreatePredictionContractTest {

    private String username;
    private String session;

    @BeforeEach
    void registerCreator() {
        username = "luoja-" + UUID.randomUUID();
        session = given().contentType("application/json")
                .body("""
                        {"username":"%s","password":"correct horse battery staple"}""".formatted(username))
                .when()
                .post("/api/auth/register")
                .then()
                .statusCode(201)
                .extract()
                .cookie("sisu_session");
    }

    @Test
    void createsAnOpenPredictionAttributedToTheCreatorThatAppearsInTheList() {
        String question = "Sataako Helsingissä juhannuksena? " + UUID.randomUUID();
        Instant closingAt = Instant.now().plus(7, ChronoUnit.DAYS).truncatedTo(ChronoUnit.SECONDS);

        var created = given().cookie("sisu_session", session)
                .contentType("application/json")
                .body(body(question, "Kaisaniemen sääasema", "Sademäärä > 0 mm", closingAt.toString()))
                .when()
                .post("/api/predictions")
                .then()
                .statusCode(201)
                .body("id", notNullValue())
                .body("question", equalTo(question))
                .body("description", equalTo("Kaisaniemen sääasema"))
                .body("resolutionCriteria", equalTo("Sademäärä > 0 mm"))
                .body("closingAt", equalTo(closingAt.toString()))
                .body("status", equalTo("OPEN"))
                .body("creatorUsername", equalTo(username))
                .body("totalSisuWagered", equalTo(0))
                .body("oddsYesPercent", equalTo(50.0f))
                .body("winningOutcome", nullValue())
                .extract();
        String id = created.path("id");
        assertThat(created.header("Location"), endsWith("/api/predictions/" + id));

        given().queryParam("status", "OPEN")
                .when()
                .get("/api/predictions")
                .then()
                .statusCode(200)
                .body("id", hasItem(id));
        given().when()
                .get("/api/predictions/{id}", id)
                .then()
                .statusCode(200)
                .body("creatorUsername", equalTo(username));
    }

    @Test
    void requiresLogin() {
        given().contentType("application/json")
                .body(body("Kysymys?", "", "Peruste", future()))
                .when()
                .post("/api/predictions")
                .then()
                .statusCode(401)
                .body("code", equalTo("UNAUTHENTICATED"));
    }

    @Test
    void rejectsAnEmptyQuestion() {
        create(body("   ", "kuvaus", "Peruste", future())).statusCode(422).body("code", equalTo("QUESTION_REQUIRED"));
    }

    @Test
    void rejectsEmptyResolutionCriteria() {
        create(body("Kysymys?", "kuvaus", "", future()))
                .statusCode(422)
                .body("code", equalTo("RESOLUTION_CRITERIA_REQUIRED"));
    }

    @Test
    void rejectsFieldsOverTheirMaximumLength() {
        create(body("k".repeat(201), "", "Peruste", future()))
                .statusCode(422)
                .body("code", equalTo("QUESTION_TOO_LONG"));
        create(body("Kysymys?", "d".repeat(2001), "Peruste", future()))
                .statusCode(422)
                .body("code", equalTo("DESCRIPTION_TOO_LONG"));
        create(body("Kysymys?", "", "r".repeat(1001), future()))
                .statusCode(422)
                .body("code", equalTo("RESOLUTION_CRITERIA_TOO_LONG"));
    }

    @Test
    void rejectsAClosingDateInThePast() {
        String past = Instant.now().minus(1, ChronoUnit.HOURS).toString();
        create(body("Kysymys?", "kuvaus", "Peruste", past))
                .statusCode(422)
                .body("code", equalTo("CLOSING_DATE_NOT_IN_FUTURE"));
    }

    @Test
    void rejectsAMissingClosingDate() {
        String withoutClosingDate = """
                {"question":"Kysymys?","description":"","resolutionCriteria":"Peruste"}""";
        create(withoutClosingDate).statusCode(422).body("code", equalTo("CLOSING_DATE_NOT_IN_FUTURE"));
    }

    private ValidatableResponse create(String json) {
        return given().cookie("sisu_session", session)
                .contentType("application/json")
                .body(json)
                .when()
                .post("/api/predictions")
                .then();
    }

    private static String future() {
        return Instant.now().plus(1, ChronoUnit.DAYS).toString();
    }

    private static String body(String question, String description, String criteria, String closingAt) {
        String template = """
                {"question":"%s","description":"%s","resolutionCriteria":"%s","closingAt":"%s"}""";
        return template.formatted(question, description, criteria, closingAt);
    }
}
