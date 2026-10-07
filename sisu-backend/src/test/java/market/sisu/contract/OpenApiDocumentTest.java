package market.sisu.contract;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasItems;
import static org.hamcrest.Matchers.notNullValue;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

/** The generated OpenAPI document carries the contract-level metadata from contracts/openapi.yaml. */
@QuarkusTest
class OpenApiDocumentTest {

    @Test
    void generatedDocumentMatchesContractMetadata() {
        given().queryParam("format", "json")
                .when()
                .get("/q/openapi")
                .then()
                .statusCode(200)
                .body("info.title", equalTo("Sisu Market API"))
                .body("info.version", equalTo("0.1.0"))
                .body("components.securitySchemes.sessionCookie.type", equalTo("apiKey"))
                .body("components.securitySchemes.sessionCookie.in", equalTo("cookie"))
                .body("components.securitySchemes.sessionCookie.name", equalTo("sisu_session"))
                .body("components.schemas.Error.required", hasItems("code", "message"))
                .body("paths.'/api/realtime/updates'.get", notNullValue())
                .body("paths.'/api/predictions/{id}/wagers'.post", notNullValue())
                .body("paths.'/api/users/me/wagers'.get", notNullValue())
                .body("components.schemas.PlaceWagerRequest.required", hasItems("outcome", "amountSisu"))
                .body("components.schemas.Wager.properties.potentialPayoutSisu", notNullValue());
    }
}
