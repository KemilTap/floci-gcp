package io.floci.gcp.services.iam;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.startsWith;

@QuarkusTest
class IamWildcardProjectRestIntegrationTest {

    private static final String PROJECT = "iam-wildcard-it";
    private static final String EMAIL = "tf-sa@" + PROJECT + ".iam.gserviceaccount.com";
    private static final String WILDCARD_SA = "/v1/projects/-/serviceAccounts/" + EMAIL;

    @Test
    void serviceAccountAndKeysAreReachableThroughProjectWildcard() {
        given()
                .contentType("application/json")
                .body(Map.of("accountId", "tf-sa", "serviceAccount", Map.of("displayName", "TF")))
                .when().post("/v1/projects/" + PROJECT + "/serviceAccounts")
                .then()
                .statusCode(200);

        given()
                .when().get(WILDCARD_SA)
                .then()
                .statusCode(200)
                .body("name", equalTo("projects/" + PROJECT + "/serviceAccounts/" + EMAIL))
                .body("projectId", equalTo(PROJECT));

        String keyId = given()
                .contentType("application/json")
                .body("{}")
                .when().post(WILDCARD_SA + "/keys")
                .then()
                .statusCode(200)
                .body("name", startsWith("projects/" + PROJECT + "/serviceAccounts/" + EMAIL + "/keys/"))
                .extract().path("keyId");

        given()
                .when().get(WILDCARD_SA + "/keys")
                .then()
                .statusCode(200)
                .body("keys", hasSize(1));

        given()
                .when().get(WILDCARD_SA + "/keys/" + keyId)
                .then()
                .statusCode(200)
                .body("keyId", equalTo(keyId));

        given()
                .contentType("application/json")
                .body("{}")
                .when().post(WILDCARD_SA + ":getIamPolicy")
                .then()
                .statusCode(200);

        given()
                .when().delete(WILDCARD_SA)
                .then()
                .statusCode(200);

        given()
                .when().get("/v1/projects/" + PROJECT + "/serviceAccounts/" + EMAIL)
                .then()
                .statusCode(404);
    }

    @Test
    void missingServiceAccountUnderWildcardIsPermissionDenied() {
        given()
                .when().get("/v1/projects/-/serviceAccounts/fake@example.com")
                .then()
                .statusCode(403)
                .body("error.status", equalTo("PERMISSION_DENIED"));
    }
}
