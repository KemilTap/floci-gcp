package io.floci.gcp.services.gcs.model;

import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class GcsLocationTypes {

    public static final String REGION = "region";
    public static final String DUAL_REGION = "dual-region";
    public static final String MULTI_REGION = "multi-region";

    private static final Set<String> MULTI_REGIONS = Set.of("US", "EU", "ASIA");
    private static final Set<String> PREDEFINED_DUAL_REGIONS = Set.of("ASIA1", "EUR4", "EUR5", "EUR7", "EUR8", "NAM4");

    private GcsLocationTypes() {}

    public static String of(String location, List<String> dataLocations) {
        if (dataLocations != null && dataLocations.size() == 2) {
            return DUAL_REGION;
        }
        String normalized = location == null ? "US" : location.toUpperCase(Locale.ROOT);
        if (PREDEFINED_DUAL_REGIONS.contains(normalized)) {
            return DUAL_REGION;
        }
        if (MULTI_REGIONS.contains(normalized)) {
            return MULTI_REGION;
        }
        return REGION;
    }
}
