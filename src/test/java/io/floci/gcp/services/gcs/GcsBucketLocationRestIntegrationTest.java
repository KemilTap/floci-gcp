package io.floci.gcp.services.gcs;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;

@QuarkusTest
class GcsBucketLocationRestIntegrationTest {

    @Test
    void bucketProjectNumberMatchesResourceManager() {
        String projectNumber = given().when().get("/v1/projects/gcs-number-project")
                .then().statusCode(200).extract().path("projectNumber");

        given()
                .contentType("application/json")
                .body(Map.of("name", "project-number-bucket"))
                .when().post("/storage/v1/b?project=gcs-number-project")
                .then().statusCode(200)
                .body("projectNumber", equalTo(projectNumber))
                .body("$", not(hasKey("projectId")));

        given().when().get("/storage/v1/b/project-number-bucket")
                .then().statusCode(200)
                .body("projectNumber", equalTo(projectNumber))
                .body("$", not(hasKey("projectId")));

        given().queryParam("project", projectNumber)
                .when().get("/storage/v1/b")
                .then().statusCode(200)
                .body("items.name", equalTo(List.of("project-number-bucket")))
                .body("items[0]", not(hasKey("projectId")));
    }

    @ParameterizedTest
    @CsvSource({
            "loc-rest-nam4, NAM4, dual-region",
            "loc-rest-eur4, EUR4, dual-region",
            "loc-rest-us, US, multi-region",
            "loc-rest-region, us-central1, region"
    })
    void bucketReportsLocationType(String bucket, String location, String locationType) {
        given()
                .contentType("application/json")
                .body(Map.of("name", bucket, "location", location))
                .when().post("/storage/v1/b?project=test-project")
                .then().statusCode(200)
                .body("locationType", equalTo(locationType));

        given().when().get("/storage/v1/b/" + bucket)
                .then().statusCode(200)
                .body("locationType", equalTo(locationType));

        given().queryParam("project", "test-project")
                .when().get("/storage/v1/b")
                .then().statusCode(200)
                .body("items.find { it.name == '" + bucket + "' }.locationType", equalTo(locationType));

        given().when().get("/storage/v1/b/" + bucket + "/storageLayout")
                .then().statusCode(200)
                .body("locationType", equalTo(locationType));
    }

    @Test
    void customPlacementConfigMakesADualRegionAndIsEchoed() {
        given()
                .contentType("application/json")
                .body(Map.of("name", "loc-rest-custom-dual", "location", "US",
                        "customPlacementConfig", Map.of("dataLocations", List.of("US-EAST1", "US-WEST1"))))
                .when().post("/storage/v1/b?project=test-project")
                .then().statusCode(200)
                .body("location", equalTo("US"))
                .body("locationType", equalTo("dual-region"))
                .body("customPlacementConfig.dataLocations", equalTo(List.of("US-EAST1", "US-WEST1")));

        given().when().get("/storage/v1/b/loc-rest-custom-dual")
                .then().statusCode(200)
                .body("locationType", equalTo("dual-region"))
                .body("customPlacementConfig.dataLocations", equalTo(List.of("US-EAST1", "US-WEST1")));
    }

    @Test
    void customPlacementConfigNeedsExactlyTwoRegions() {
        for (List<String> dataLocations : List.of(List.of("US-EAST1"), List.of("US-EAST1", "US-WEST1", "US-CENTRAL1"))) {
            given()
                    .contentType("application/json")
                    .body(Map.of("name", "loc-rest-bad-placement", "location", "US",
                            "customPlacementConfig", Map.of("dataLocations", dataLocations)))
                    .when().post("/storage/v1/b?project=test-project")
                    .then().statusCode(400)
                    .body("error.status", equalTo("INVALID_ARGUMENT"));
        }
        given().when().get("/storage/v1/b/loc-rest-bad-placement").then().statusCode(404);
    }
}
