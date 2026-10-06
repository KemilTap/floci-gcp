package io.floci.gcp.services.gcs;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.floci.gcp.services.gcs.model.GcsBucket;
import io.quarkus.jackson.ObjectMapperCustomizer;
import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.inject.Singleton;

/**
 * Keeps storage-only bucket fields out of the JSON API. The storage backends use their own
 * plain ObjectMapper, so {@code projectId} still persists while the API mapper omits it.
 */
@Singleton
public class GcsJacksonCustomizer implements ObjectMapperCustomizer {

    @Override
    public void customize(ObjectMapper mapper) {
        mapper.addMixIn(GcsBucket.class, GcsBucketApiMixin.class);
    }

    @RegisterForReflection
    abstract static class GcsBucketApiMixin {

        @JsonIgnore
        abstract String getProjectId();
    }
}
