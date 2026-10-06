package io.floci.gcp.test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.storage.Bucket;
import com.google.cloud.storage.BucketInfo;
import com.google.cloud.storage.Storage;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Bucket location metadata as the Java SDK sees it over both transports: locationType
 * (storage.proto Bucket.location_type) and customPlacementConfig.dataLocations, plus a
 * project number that agrees with Resource Manager (checked over raw JSON because the
 * SDK keeps BucketInfo.getProject() package-private).
 */
class GcsBucketLocationTest {

    static Stream<String> transports() {
        return Stream.of("json", "grpc");
    }

    @ParameterizedTest
    @MethodSource("transports")
    void bucketsReportLocationTypeAndProjectNumber(String transport) throws Exception {
        Storage storage = "grpc".equals(transport)
                ? TestFixtures.storageGrpcClient() : TestFixtures.storageClient();
        String prefix = TestFixtures.uniqueName("loc-" + transport);
        List<String> created = List.of(prefix + "-nam4", prefix + "-us", prefix + "-region", prefix + "-custom");
        try {
            Bucket nam4 = storage.create(BucketInfo.newBuilder(created.get(0)).setLocation("NAM4").build());
            Bucket us = storage.create(BucketInfo.newBuilder(created.get(1)).setLocation("US").build());
            Bucket region = storage.create(BucketInfo.newBuilder(created.get(2)).setLocation("us-central1").build());
            storage.create(BucketInfo.newBuilder(created.get(3))
                    .setLocation("US")
                    .setCustomPlacementConfig(BucketInfo.CustomPlacementConfig.newBuilder()
                            .setDataLocations(List.of("US-EAST1", "US-WEST1"))
                            .build())
                    .build());

            assertThat(nam4.getLocationType()).isEqualTo("dual-region");
            assertThat(us.getLocationType()).isEqualTo("multi-region");
            assertThat(region.getLocationType()).isEqualTo("region");

            Bucket custom = storage.get(created.get(3));
            assertThat(custom.getLocationType()).isEqualTo("dual-region");
            assertThat(custom.getCustomPlacementConfig().getDataLocations())
                    .containsExactly("US-EAST1", "US-WEST1");

            assertThat(getJson("/storage/v1/b/" + created.get(1)).path("projectNumber").asText())
                    .isEqualTo(getJson("/v1/projects/" + TestFixtures.projectId()).path("projectNumber").asText());
        } finally {
            for (String name : created) {
                try {
                    storage.delete(name);
                } catch (Exception ignored) {
                    // Best-effort cleanup: a failed delete must not mask the assertions above.
                }
            }
        }
    }

    private static JsonNode getJson(String path) throws Exception {
        HttpResponse<String> response = HttpClient.newHttpClient().send(
                HttpRequest.newBuilder(URI.create(TestFixtures.endpoint() + path)).GET().build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        return new ObjectMapper().readTree(response.body());
    }
}
