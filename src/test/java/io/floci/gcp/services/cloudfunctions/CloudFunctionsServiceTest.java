package io.floci.gcp.services.cloudfunctions;

import com.google.cloud.functions.v2.Environment;
import com.google.cloud.functions.v2.Function;
import com.google.cloud.functions.v2.GenerateUploadUrlResponse;
import com.google.cloud.functions.v2.ListFunctionsResponse;
import com.google.longrunning.Operation;
import com.google.protobuf.Any;
import com.google.protobuf.Message;
import io.floci.gcp.core.common.GcpException;
import io.floci.gcp.core.common.ServiceRegistry;
import io.floci.gcp.core.storage.InMemoryStorage;
import io.floci.gcp.services.gcs.GcsService;
import io.floci.gcp.services.operations.LongRunningOperationsService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CloudFunctionsServiceTest {

    private CloudFunctionsService service;
    private GcsService gcsService;

    @BeforeEach
    void setUp() {
        LongRunningOperationsService operations = mock(LongRunningOperationsService.class);
        when(operations.done(anyString(), any(Message.class), any(Message.class)))
                .thenAnswer(invocation -> Operation.newBuilder()
                        .setName(invocation.getArgument(0, String.class) + "/operations/test-op")
                        .setDone(true)
                        .setResponse(Any.pack(invocation.getArgument(1, Message.class)))
                        .setMetadata(Any.pack(invocation.getArgument(2, Message.class)))
                        .build());
        when(operations.doneTransient(anyString(), any(Message.class), any(Message.class)))
                .thenAnswer(invocation -> Operation.newBuilder()
                        .setName(invocation.getArgument(0, String.class) + "/operations/test-op")
                        .setDone(true)
                        .setResponse(Any.pack(invocation.getArgument(1, Message.class)))
                        .setMetadata(Any.pack(invocation.getArgument(2, Message.class)))
                        .build());
        gcsService = mock(GcsService.class);
        service = new CloudFunctionsService(new InMemoryStorage<>(), operations, gcsService);
    }

    @Test
    void createSynthesizesActiveGen2Function() {
        Operation operation = service.createFunction("p1", "us-central1", "fn",
                "{\"buildConfig\":{\"runtime\":\"java21\"}}", false);

        assertTrue(operation.getDone());
        Function function = service.getFunction("projects/p1/locations/us-central1/functions/fn");
        assertEquals("projects/p1/locations/us-central1/functions/fn", function.getName());
        assertEquals(Function.State.ACTIVE, function.getState());
        assertEquals(Environment.GEN_2, function.getEnvironment());
        assertEquals("https://us-central1-p1.cloudfunctions.net/fn", function.getUrl());
        assertEquals("projects/p1/locations/us-central1/services/fn", function.getServiceConfig().getService());
        assertTrue(function.getServiceConfig().getAllTrafficOnLatestRevision());
    }

    @Test
    void duplicateCreateAndMissingGetUseGcpErrors() {
        service.createFunction("p1", "us-central1", "fn", "{}", false);

        GcpException duplicate = assertThrows(GcpException.class,
                () -> service.createFunction("p1", "us-central1", "fn", "{}", false));
        assertEquals("ALREADY_EXISTS", duplicate.getGcpStatus());

        GcpException missing = assertThrows(GcpException.class,
                () -> service.getFunction("projects/p1/locations/us-central1/functions/missing"));
        assertEquals("NOT_FOUND", missing.getGcpStatus());
    }

    @Test
    void listFunctionsPaginatesAndFiltersByProjectAndLocation() {
        service.createFunction("p1", "us-central1", "a", "{}", false);
        service.createFunction("p1", "us-central1", "b", "{}", false);
        service.createFunction("p2", "us-central1", "a", "{}", false);
        service.createFunction("p1", "europe-west1", "c", "{}", false);

        ListFunctionsResponse firstPage = service.listFunctions("p1", "us-central1", 1, null);
        assertEquals(1, firstPage.getFunctionsCount());
        assertFalse(firstPage.getNextPageToken().isBlank());

        ListFunctionsResponse secondPage = service.listFunctions("p1", "us-central1", 10,
                firstPage.getNextPageToken());
        assertEquals(1, secondPage.getFunctionsCount());
        assertEquals("", secondPage.getNextPageToken());

        assertEquals(1, service.listFunctions("p2", "us-central1", 10, null).getFunctionsCount());
        assertEquals(1, service.listFunctions("p1", "europe-west1", 10, null).getFunctionsCount());
    }

    @Test
    void listFunctionsWithLocationWildcardSpansAllLocationsOfProject() {
        service.createFunction("p1", "us-central1", "a", "{}", false);
        service.createFunction("p1", "europe-west1", "b", "{}", false);
        service.createFunction("p2", "us-central1", "c", "{}", false);

        ListFunctionsResponse response = service.listFunctions("p1", "-", 10, null);

        assertEquals(List.of(
                        "projects/p1/locations/europe-west1/functions/b",
                        "projects/p1/locations/us-central1/functions/a"),
                response.getFunctionsList().stream().map(Function::getName).toList());
    }

    @Test
    void validateOnlyDoesNotPersistCreateOrDeleteMutations() {
        service.createFunction("p1", "us-central1", "validate", "{}", true);
        assertThrows(GcpException.class,
                () -> service.getFunction("projects/p1/locations/us-central1/functions/validate"));

        service.createFunction("p1", "us-central1", "real", "{}", false);
        service.deleteFunction("projects/p1/locations/us-central1/functions/real", true);

        assertEquals("projects/p1/locations/us-central1/functions/real",
                service.getFunction("projects/p1/locations/us-central1/functions/real").getName());
    }

    @Test
    void deleteFunctionRemovesStoredFunction() {
        service.createFunction("p1", "us-central1", "fn", "{}", false);
        String name = "projects/p1/locations/us-central1/functions/fn";

        Operation operation = service.deleteFunction(name, false);

        assertTrue(operation.getDone());
        assertThrows(GcpException.class, () -> service.getFunction(name));
    }

    @Test
    void generateUploadUrlCreatesSourceBucketAndReturnsStorageSource() {
        GenerateUploadUrlResponse response = service.generateUploadUrl(
                "project-1", "us-central1", "http://localhost:4588");

        assertTrue(response.getUploadUrl().startsWith("http://localhost:4588/gcf-v2-sources-project-1-us-central1/"));
        assertEquals("gcf-v2-sources-project-1-us-central1", response.getStorageSource().getBucket());
        assertTrue(response.getStorageSource().getObject().startsWith("source-"));
        assertEquals(response.getUploadUrl(), response.getStorageSource().getSourceUploadUrl());
        verify(gcsService).createBucket(eq("gcf-v2-sources-project-1-us-central1"),
                eq("project-1"), eq("http://localhost:4588"), anyMap());
    }

    @SuppressWarnings("unchecked")
    @Test
    void generateUploadUrlIgnoresExistingSourceBucket() {
        when(gcsService.createBucket(anyString(), anyString(), anyString(), any(Map.class)))
                .thenThrow(GcpException.alreadyExists("Bucket already exists"));

        assertDoesNotThrow(() -> service.generateUploadUrl("p1", "us-central1", "http://localhost:4588"));
    }

    @Test
    void generateUploadUrlRequiresGcsServiceEnabled() {
        ServiceRegistry registry = mock(ServiceRegistry.class);
        when(registry.isEnabled("gcs")).thenReturn(false);
        CloudFunctionsService gated = new CloudFunctionsService(new InMemoryStorage<>(),
                mock(LongRunningOperationsService.class), gcsService, registry);

        GcpException ex = assertThrows(GcpException.class,
                () -> gated.generateUploadUrl("p1", "us-central1", "http://localhost:4588"));

        assertEquals("UNAVAILABLE", ex.getGcpStatus());
        verifyNoInteractions(gcsService);
    }
}
