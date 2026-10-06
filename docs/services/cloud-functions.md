# Cloud Functions

floci-gcp emulates the Cloud Functions v2 control plane over REST JSON using Google's published protobuf types.

| Config | Default | Description |
|---|---|---|
| `FLOCI_GCP_SERVICES_CLOUDFUNCTIONS_ENABLED` | `true` | Enable/disable Cloud Functions |

## Supported API Surface

| Operation | Path |
|---|---|
| Create function | `POST /v2/projects/{project}/locations/{location}/functions` |
| List functions | `GET /v2/projects/{project}/locations/{location}/functions` |
| Get function | `GET /v2/projects/{project}/locations/{location}/functions/{function}` |
| Delete function | `DELETE /v2/projects/{project}/locations/{location}/functions/{function}` |
| Generate upload URL | `POST /v2/projects/{project}/locations/{location}/functions:generateUploadUrl` |

List functions accepts `-` as the location (`projects/{project}/locations/-/functions`) to list functions across all locations.

Create and delete return completed `google.longrunning.Operation` resources immediately. Operations can be read, listed, waited on, and deleted under `/v2/projects/{project}/locations/{location}/operations`.

## Behavior

Cloud Functions resources are metadata only. Creating a function synthesizes `ACTIVE` state, default `GEN_2` environment when omitted, URL, timestamps, and Cloud Run service references in `serviceConfig`.

`generateUploadUrl` returns an upload URL backed by the existing GCS XML `PUT /{bucket}/{object}` object path and includes a `storageSource` in the response. Uploaded source archives are stored as inert GCS metadata; no function build or runtime is executed. Cloud Storage must be enabled for source upload URL generation.

`validateOnly=true` returns a successful completed operation without storing or deleting resources. Validate-only operations are not retained for later operation get/list calls.

## SDK Usage

Cloud Functions clients should use the HTTP JSON transport, an explicit endpoint, and no credentials:

```java
FunctionServiceSettings settings = FunctionServiceSettings.newHttpJsonBuilder()
    .setEndpoint("http://localhost:4588")
    .setCredentialsProvider(NoCredentialsProvider.create())
    .build();
```

## Not Implemented

- Runtime invocation
- Function updates
- Download URL generation
- Runtime listing
- IAM
