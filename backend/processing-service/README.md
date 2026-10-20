# OMRAGUL Processing Service

The **Processing Service** is an orchestration microservice in the OMRAGUL (Offline Multimodal Retrieval-Augmented Generation Unified Library) enterprise platform. It manages the lifecycle of asynchronous document processing jobs, coordinating document metadata validation, multimodal extraction, chunking, and vector indexing across downstream services.

---

## 1. Responsibilities

- **Job Lifecycle Management:** Creation, status tracking, bounded retries, and explicit state transitions.
- **Persistent Job State:** Flyway-managed relational schema in `processing_db` with optimistic locking and indexed queries.
- **Workflow Orchestration:** Coordinates Knowledge Service, Multimodal Service, Embedding Service, and RAG Service without holding open database transactions across network calls.
- **Failure Classification:** Distinguishes permanent non-retryable failures (e.g., missing document, unsupported format, deleted document) from transient retryable failures (e.g., network timeouts, 503 Service Unavailable).
- **Restart Recovery:** Recovers in-flight interrupted jobs upon service restart without silent job loss.
- **Status Synchronization:** Synchronizes document processing status back to the Knowledge Service (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).
- **Strict Boundary Integrity:** Does **not** reimplement JWT issuance, document storage CRUD, OCR/transcription algorithms, vector index internals, or agent planning.

---

## 2. API Endpoints

All APIs are prefixed with `/api/v1/processing`.

| Method | Endpoint | Description | Success Code |
|---|---|---|---|
| `POST` | `/api/v1/processing/jobs` | Enqueue document for processing | `201 Created` |
| `GET` | `/api/v1/processing/jobs/{id}` | Get full job details by ID | `200 OK` |
| `GET` | `/api/v1/processing/jobs/{id}/status` | Get lightweight job progress status | `200 OK` |
| `GET` | `/api/v1/processing/documents/{documentId}/status` | Get latest processing status for document | `200 OK` |
| `GET` | `/api/v1/processing/jobs` | List jobs with optional `?status=` filter & pagination | `200 OK` |
| `POST` | `/api/v1/processing/jobs/{id}/retry` | Retry a failed job (if under max attempts) | `200 OK` |
| `POST` | `/api/v1/processing/jobs/{id}/cancel` | Cancel an active job | `200 OK` |

### API Schemas

#### Create Processing Job (`POST /api/v1/processing/jobs`)
**Request:**
```json
{
  "document_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "knowledge_base_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "idempotency_key": "unique-client-uuid-or-hash"
}
```

**Response (`201 Created`):**
```json
{
  "id": "7b8971f4-3d02-466d-9fcb-9c7161b2e67a",
  "document_id": "3fa85f64-5717-4562-b3fc-2c963f66afa6",
  "knowledge_base_id": "a0eebc99-9c0b-4ef8-bb6d-6bb9bd380a11",
  "status": "QUEUED",
  "stage": "INITIALIZATION",
  "current_step": "Job enqueued for execution",
  "attempt_count": 0,
  "max_attempts": 3,
  "error_code": null,
  "error_message": null,
  "idempotency_key": "unique-client-uuid-or-hash",
  "created_at": "2026-10-20T01:50:00Z",
  "updated_at": "2026-10-20T01:50:00Z",
  "started_at": null,
  "completed_at": null
}
```

#### Duplicate Prevention (`409 Conflict`)
If an active job (`QUEUED`, `VALIDATING`, `EXTRACTING`, `CHUNKING`, `EMBEDDING_INDEXING`) is already processing the same document:
```json
{
  "timestamp": "2026-10-20T01:50:05Z",
  "status": 409,
  "error": "DUPLICATE_JOB",
  "message": "Document 3fa85f64-5717-4562-b3fc-2c963f66afa6 is already being processed by active job 7b8971f4-3d02-466d-9fcb-9c7161b2e67a",
  "path": "/api/v1/processing/jobs"
}
```

---

## 3. Lifecycle State Machine

```
   [ Client Request ]
           │
           ▼
        QUEUED ───────────────► CANCELLED
           │
           ▼
       VALIDATING ────────────► FAILED
           │
           ▼
       EXTRACTING ────────────► FAILED
           │
           ▼
        CHUNKING ─────────────► FAILED
           │
           ▼
   EMBEDDING_INDEXING ────────► FAILED
           │                        │
           ▼                        ▼ (Retry if attempts < max)
       COMPLETED                  QUEUED
```

- **Legal State Transitions:**
  - `QUEUED` → `VALIDATING`, `CANCELLED`, `FAILED`
  - `VALIDATING` → `EXTRACTING`, `FAILED`, `CANCELLED`
  - `EXTRACTING` → `CHUNKING`, `FAILED`, `CANCELLED`
  - `CHUNKING` → `EMBEDDING_INDEXING`, `FAILED`, `CANCELLED`
  - `EMBEDDING_INDEXING` → `COMPLETED`, `FAILED`, `CANCELLED`
  - `FAILED` → `QUEUED` (Manual retry or automated transient retry)
  - `COMPLETED`, `CANCELLED` are terminal states.

---

## 4. Downstream Integrations and Verified Ports

| Microservice | Container Port | Host Port | Verified Endpoints | Contract Schema |
|---|---|---|---|---|
| **Knowledge Service** | `8080` | `8082` | `GET /api/v1/documents/{id}`<br>`PATCH /api/v1/documents/{id}/processing-status/{status}` | [`DocumentResponse`](file:///e:/CAPSTONE%20CSE%20AI/OMRAGUL/OMRAGUL/backend/knowledge-service/src/main/java/com/omragul/knowledge/dto/DocumentResponse.java) |
| **Multimodal Service** | `8004` | `8004` | `POST /api/v1/multimodal/pdf/extract`<br>`POST /api/v1/multimodal/ocr`<br>`POST /api/v1/multimodal/speech/transcribe`<br>`POST /api/v1/multimodal/chunk?text=...` | Multipart `file` input;<br>Returns JSON text/chunks |
| **Embedding Service** | `8003` | `8003` | `POST /api/v1/embeddings/batch` | `{"texts": [...]}` → `{"embeddings": [[...]]}` |
| **RAG Service** | `8002` | `8002` | `POST /api/v1/rag/index` | `{"chunks": [{"text": ..., "document_id": ...}]}` |

---

## 5. Security & Authentication Constraints

- **No Internal API Key:** In compliance with repository requirements, no `internal_api_key`, header filters, interceptors, or placeholder shared secrets are introduced.
- **Deferred Service Authentication:** Service-to-service authentication is explicitly deferred and left for the owner to implement via platform-level identity (e.g., mTLS or Identity Service asymmetric JWTs).
- **Data Protection:** No document bodies, tokens, credentials, or sensitive headers are emitted in logs.

---

## 6. Configuration Properties

```yaml
processing:
  knowledge-service:
    url: ${KNOWLEDGE_SERVICE_URL:http://localhost:8082}
  multimodal-service:
    url: ${MULTIMODAL_SERVICE_URL:http://localhost:8004}
  embedding-service:
    url: ${EMBEDDING_SERVICE_URL:http://localhost:8003}
  rag-service:
    url: ${RAG_SERVICE_URL:http://localhost:8002}

  orchestration:
    max-attempts: ${PROCESSING_MAX_ATTEMPTS:3}
    connect-timeout-ms: ${PROCESSING_CONNECT_TIMEOUT_MS:5000}
    read-timeout-ms: ${PROCESSING_READ_TIMEOUT_MS:30000}

  executor:
    core-pool-size: ${PROCESSING_EXECUTOR_CORE_POOL_SIZE:4}
    max-pool-size: ${PROCESSING_EXECUTOR_MAX_POOL_SIZE:8}
    queue-capacity: ${PROCESSING_EXECUTOR_QUEUE_CAPACITY:100}
```

---

## 7. Build, Tests, and Verification

### 7.1 Running the Automated Test Suite

Run the full automated test suite using the bundled Maven wrapper:

```powershell
cd backend/processing-service
.\mvnw.cmd test
```

**Test Coverage Summary (62 Tests across 10 Test Suites):**
- **Unit & State Machine:**
  - `ProcessingStatusTest` (5 tests): State transition legality, terminal state detection, recovery eligibility.
  - `ProcessingJobRepositoryTest` (4 tests): In-memory database persistence, optimistic locking (`@Version`), duplicate conflict handling, active status queries.
- **Service & Lifecycle:**
  - `ProcessingJobServiceTest` (9 tests): Job creation, duplicate prevention (`409 Conflict`), idempotency key re-delivery, retry attempt incrementation, cancellation invariants.
  - `DefaultProcessingOrchestratorTest` (8 tests): Full asynchronous pipeline execution, permanent failure classification without retry, transient failure handling with re-queueing, retry budget exhaustion, startup interrupted-job recovery, cancellation protection (never overwriting CANCELLED with COMPLETED/FAILED and stopping future pipeline stages), optimistic locking concurrency handling, and empty text extraction permanent failure.
- **REST API & WebMvc:**
  - `ProcessingJobControllerTest` (9 tests): Request validation, HTTP status codes (`201`, `200`, `400`, `404`, `409`), pagination, UUID parameter checks.
- **Downstream Client Contract Tests:**
  - `KnowledgeServiceClientTest` (5 tests): `GET /api/v1/documents/{id}` deserialization, `404` handling, `PATCH /processing-status/{status}` call, 5xx server errors, network connection timeouts.
  - `MultimodalServiceClientTest` (8 tests): Direct text extraction, PDF extraction via `POST /api/v1/multimodal/pdf/extract`, Image OCR via `POST /api/v1/multimodal/ocr`, Audio speech transcription via `POST /api/v1/multimodal/speech/transcribe`, 5xx server errors, network timeouts, sliding-window chunking, empty content handling.
  - `EmbeddingServiceClientTest` (7 tests): Batch embedding requests via `POST /api/v1/embeddings/batch`, dimension checking (384), 4xx client errors, 5xx server errors, malformed response handling, network connection timeouts.
  - `RagServiceClientTest` (8 tests): Vector indexing requests via `POST /api/v1/rag/index`, chunk metadata propagation, knowledge-base ID handling, 4xx client errors, 5xx downstream errors, malformed responses, connection failures.

### 7.2 Packaging the Service

Build the self-contained Spring Boot executable JAR:

```powershell
cd backend/processing-service
.\mvnw.cmd clean package -DskipTests
```

The resulting artifact is located at:
`backend/processing-service/target/processing-service-0.0.1-SNAPSHOT.jar`

### 7.3 Docker and End-to-End Verification Status

- **Dockerfile:** Multi-stage build provided (`maven:3.9.11-eclipse-temurin-21` -> `eclipse-temurin:21-jre`).
- **Environment Status:** Docker Desktop daemon was not running during local verification (`open //./pipe/dockerDesktopLinuxEngine: The system cannot find the file specified`).
- **Execution with Docker:**
  Once Docker Desktop is launched, build and run using:
  ```powershell
  docker build -t omragul/processing-service:latest backend/processing-service/
  docker run -p 8083:8080 --env-file backend/processing-service/env.example omragul/processing-service:latest
  ```
