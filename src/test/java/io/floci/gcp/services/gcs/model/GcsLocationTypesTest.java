package io.floci.gcp.services.gcs.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GcsLocationTypesTest {

    @Test
    void classifiesLocations() {
        assertEquals("dual-region", GcsLocationTypes.of("NAM4", null));
        assertEquals("dual-region", GcsLocationTypes.of("eur4", null));
        for (String code : List.of("ASIA1", "EUR5", "EUR7", "EUR8")) {
            assertEquals("dual-region", GcsLocationTypes.of(code, null), code);
        }
        assertEquals("multi-region", GcsLocationTypes.of("US", null));
        assertEquals("multi-region", GcsLocationTypes.of("asia", List.of()));
        assertEquals("multi-region", GcsLocationTypes.of(null, null));
        assertEquals("region", GcsLocationTypes.of("us-central1", null));
        assertEquals("dual-region", GcsLocationTypes.of("US", List.of("US-EAST1", "US-WEST1")));
    }

    @Test
    void persistedBucketRoundTripsThroughAPlainObjectMapper() throws Exception {
        GcsBucket bucket = new GcsBucket();
        bucket.setName("round-trip");
        bucket.setLocation("US");
        bucket.setCustomPlacementConfig(Map.of("dataLocations", List.of("US-EAST1", "US-WEST1")));
        ObjectMapper mapper = new ObjectMapper();

        String json = mapper.writeValueAsString(bucket);
        GcsBucket restored = mapper.readValue(json, GcsBucket.class);

        assertEquals("dual-region", mapper.readTree(json).get("locationType").asText());
        assertEquals("dual-region", restored.getLocationType());
        assertEquals(List.of("US-EAST1", "US-WEST1"), restored.getDataLocations());
    }
}
