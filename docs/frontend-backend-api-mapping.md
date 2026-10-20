# OMNIRAG Frontend-to-Backend API Mapping & Contract Verification

> **Document Status**: Verified against source code, configuration files, DTOs, and test suites across all backend services (`gateway-service`, `identity-service`, `knowledge-service`, `processing-service`, `analytics-service`, `notification-service`, `ai-services/rag-service`) and the frontend repository.  
> **Date**: October 2026  
> **Environment**: OMNIRAG Multi-Service Architecture (Java 21 / Spring Boot 3.3.x, Python 3.11 / FastAPI, React 19 / Vite)

---

## 1. Executive Summary & Verification Methodology

This specification defines the authoritative API boundary between the OMNIRAG Web Frontend and backend microservices. The frontend communicates exclusively with the **API Gateway** (`http://localhost:8080` in local development / Docker compose host port), which acts as the unified reverse proxy, JWT authenticator, and downstream request forwarder.

### Verification Standards Applied
1. **Source Inspection**: All documented endpoints are verified against `@RestController` class definitions, `@RequestMapping` annotations, `@PreAuthorize` rules, request/response DTOs, and Spring Security configurations.
2. **Gateway Traversal**: Routes are checked against `com.omragul.gateway.controller.GatewayRouteController` and `com.omragul.gateway.proxy.ReverseProxyService` to confirm URL rewriting and header propagation.
3. **Zero Assumption Principle**: Features without backend controller support are explicitly marked as **UNIMPLEMENTED / BLOCKED**, with defined graceful frontend fallbacks. No endpoints or fields are invented.

---

## 2. Architecture & Gateway Route Inventory

The Gateway Service runs on port `8080` (both host and container). Browser code must never attempt to call internal Docker hostnames (`identity-service`, `knowledge-service`, etc.).

### 2.1 Gateway Routing Matrix

| Route Pattern | HTTP Method | Target Service URL Config | Gateway Rewrite Rule | Authentication Policy |
|---|---|---|---|---|
| `/health` | `GET` | *Local Gateway Response* | None | Public (`permitAll`) |
| `/api/v1/auth/**` | `ANY` | `http://identity-service:8080` | None (preserves URI) | Public for `/signup`, `/login`, `/refresh`; JWT required for `/logout`, `/logout-all` |
| `/api/v1/users/**` | `ANY` | `http://identity-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/documents/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/categories/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/document-metadata/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/document-permissions/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/document-shares/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/document-tags/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/document-versions/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/folders/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/folder-permissions/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/api/v1/tags/**` | `ANY` | `http://knowledge-service:8080` | None (preserves URI) | JWT required |
| `/knowledge-bases/**` | `ANY` | `http://knowledge-service:8080` | None (canonical path) | JWT required |
| `/api/v1/knowledge-bases/**` | `ANY` | `http://knowledge-service:8080` | **Rewrites** `/api/v1/knowledge-bases` &rarr; `/knowledge-bases` | JWT required |
| `/api/v1/processing/**` | `ANY` | `http://processing-service:8080` | None (preserves URI) | JWT required |
| `/api/analytics/**` | `ANY` | `http://analytics-service:8080` | None (preserves URI) | JWT required |
| `/api/notifications/**` | `ANY` | `http://notification-service:8080` | None (preserves URI) | JWT required |

### 2.2 Security & Token Propagation
- **CORS**: Allowed origin defaults to `FRONTEND_ORIGIN` (`http://localhost:3000`). Methods: `GET`, `POST`, `PUT`, `DELETE`, `PATCH`, `OPTIONS`, `HEAD`. Credentials allowed (`true`).
- **Authorization Header**: Client sends `Authorization: Bearer <accessToken>`.
- **Downstream Header Injection**: The Gateway's `JwtAuthenticationFilter` validates the RS256 JWT using `classpath:certs/public_key.pem`, and `ReverseProxyService` automatically appends downstream user context:
  - `X-User-Id`: Extracted user UUID (matches `sub` claim).
  - `X-User-Roles`: Comma-separated roles (e.g. `USER,ADMIN`).
  - `X-User-Email`: User email.
  - `X-User-Name`: Username.
  - `X-Request-Id`: Preserved or newly generated UUID.

---

## 3. Verified Backend Service APIs

### 3.1 Identity Service (`/api/v1/auth/**`, `/api/v1/users/**`)

#### A. Authentication Endpoints (`AuthController.java`)
- **`POST /api/v1/auth/signup`**
  - **Auth**: Public
  - **Request Body** (`SignupRequestDto`):
    ```json
    {
      "username": "string (3-100 chars)",
      "email": "user@example.com",
      "password": "string (8-100 chars)",
      "profile": {
        "firstName": "string (optional, max 100)",
        "lastName": "string (optional, max 100)",
        "phoneNumber": "string (optional, max 20)"
      }
    }
    ```
  - **Response 201** (`SignupResponseDto`):
    ```json
    {
      "message": "User registered successfully",
      "user": {
        "userId": "uuid",
        "username": "string",
        "email": "string",
        "status": "ACTIVE",
        "emailVerified": false,
        "profile": {
          "firstName": "string",
          "lastName": "string",
          "phoneNumber": "string",
          "gender": null,
          "dateOfBirth": null,
          "timezone": null,
          "language": null,
          "bio": null,
          "profileImageUrl": null
        }
      }
    }
    ```
- **`POST /api/v1/auth/login`**
  - **Auth**: Public
  - **Request Body** (`LoginRequestDto`):
    ```json
    {
      "identifier": "username or email (NotBlank)",
      "password": "password string (NotBlank)"
    }
    ```
    *(Important: Field is named `identifier`, NOT `email` or `username`)*
  - **Response 200** (`LoginResponseDto`):
    ```json
    {
      "accessToken": "eyJhbGciOiJSUzI1NiIs...",
      "refreshToken": "base64url_random_token_string",
      "tokenType": "Bearer",
      "expiresIn": 900,
      "user": {
        "userId": "uuid",
        "username": "string",
        "email": "string",
        "status": "ACTIVE",
        "emailVerified": false,
        "profile": { ... }
      }
    }
    ```
- **`POST /api/v1/auth/refresh`**
  - **Auth**: Public
  - **Request Body** (`RefreshTokenRequestDto`):
    ```json
    {
      "refreshToken": "string (NotBlank)"
    }
    ```
  - **Response 200** (`TokenResponseDto`):
    ```json
    {
      "accessToken": "string",
      "refreshToken": "string",
      "tokenType": "Bearer",
      "expiresIn": 900
    }
    ```
- **`POST /api/v1/auth/logout`**
  - **Auth**: Authenticated (JWT)
  - **Request Body** (`LogoutRequestDto`):
    ```json
    {
      "refreshToken": "string (NotBlank)"
    }
    ```
  - **Response 204**: No Content
- **`POST /api/v1/auth/logout-all`**
  - **Auth**: Authenticated (JWT)
  - **Response 204**: No Content (Revokes all active sessions for authenticated user)

#### B. User Profile & Management Endpoints (`UserController.java`)
- **`GET /api/v1/users/{userId}`** &rarr; Requires `USER_READ` authority. Returns `UserResponseDto`.
- **`PUT /api/v1/users/{userId}`** &rarr; Requires `USER_UPDATE` authority. Body: `{ "username": "...", "email": "..." }`. Returns `UserResponseDto`.
- **`DELETE /api/v1/users/{userId}`** &rarr; Requires `USER_DELETE` authority. Soft-deletes user (Response 204).
- **`PATCH /api/v1/users/{userId}/restore`** &rarr; Requires `USER_RESTORE` authority. Restores user (Response 204).
- **`PATCH /api/v1/users/{userId}/lock`** &rarr; Requires `USER_LOCK` authority. Locks user account (Response 204).
- **`PATCH /api/v1/users/{userId}/unlock`** &rarr; Requires `USER_UNLOCK` authority. Unlocks user account (Response 204).
- **`GET /api/v1/users/{userId}/profile`** &rarr; Requires `USER_PROFILE_READ` authority. Returns `UserProfileResponseDto`. Allowed if caller has `ROLE_ADMIN` OR caller's userId matches `{userId}`.
- **`PUT /api/v1/users/{userId}/profile`** &rarr; Requires `USER_PROFILE_UPDATE` authority. Body: `UpdateUserProfileRequestDto`. Returns `UserProfileResponseDto`.

#### C. RBAC Endpoints (`UserRoleController.java`)
- **`GET /api/v1/users/{userId}/roles`** &rarr; Requires `ROLE_READ`. Returns `List<RoleResponseDto>`.
- **`POST /api/v1/users/{userId}/roles/{roleId}`** &rarr; Requires `ROLE_ASSIGN`. Assigns role.
- **`DELETE /api/v1/users/{userId}/roles/{roleId}`** &rarr; Requires `ROLE_REVOKE`. Revokes role.

---

### 3.2 Knowledge Service (`/api/v1/documents/**`, `/knowledge-bases/**`)

#### A. Document Endpoints (`DocumentController.java`)
- **`POST /api/v1/documents`** &rarr; Creates document record in knowledge base.
  - **Request Body** (`DocumentRequest`):
    ```json
    {
      "knowledgeBaseId": "uuid (NotNull)",
      "folderId": "uuid (optional)",
      "categoryId": "uuid (optional)",
      "name": "string (NotBlank)",
      "originalFilename": "string (NotBlank)",
      "storedFilename": "string (optional)",
      "storagePath": "string (optional)",
      "fileExtension": "string (optional)",
      "mimeType": "string (optional)",
      "fileSizeBytes": 1048576,
      "checksum": "string (optional)",
      "ownerId": "uuid (NotNull)"
    }
    ```
  - **Response 200** (`DocumentResponse`): Full document schema including `id`, `processingStatus` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`), `status` (`ACTIVE`, `ARCHIVED`, `DELETED`), timestamps.
- **`GET /api/v1/documents/{id}`** &rarr; Returns `DocumentResponse`.
- **`GET /api/v1/documents/knowledge-base/{knowledgeBaseId}`** &rarr; Returns `List<DocumentResponse>`.
- **`GET /api/v1/documents/knowledge-base/{knowledgeBaseId}/page?page=0&size=20&sort=createdAt,desc`** &rarr; Returns Spring `Page<DocumentResponse>`.
- **`GET /api/v1/documents/knowledge-base/{knowledgeBaseId}/status/{status}`** &rarr; Filter by `Status` (`ACTIVE`, `ARCHIVED`, `DELETED`).
- **`GET /api/v1/documents/knowledge-base/{knowledgeBaseId}/processing-status/{processingStatus}`** &rarr; Filter by `ProcessingStatus` (`PENDING`, `PROCESSING`, `COMPLETED`, `FAILED`).
- **`PUT /api/v1/documents/{id}`** &rarr; Update document metadata. Body: `DocumentUpdateRequest`.
- **`PATCH /api/v1/documents/{id}/archive`** &rarr; Archive document.
- **`PATCH /api/v1/documents/{id}/restore`** &rarr; Restore document.
- **`DELETE /api/v1/documents/{id}`** &rarr; Soft-delete document.

#### B. Knowledge Base Endpoints (`KnowledgeBaseController.java`)
- Accessible via `/knowledge-bases` or `/api/v1/knowledge-bases` (Gateway rewrite):
  - **`POST /knowledge-bases`** &rarr; Creates knowledge base. Requires `X-User-Id` header (automatically supplied by Gateway from JWT).
  - **`GET /knowledge-bases`** &rarr; Lists knowledge bases owned by `X-User-Id`.
  - **`GET /knowledge-bases/{id}`** &rarr; Details by UUID.
  - **`GET /knowledge-bases/{id}/statistics`** &rarr; Returns `KnowledgeBaseStatisticsResponse` (`totalDocuments`, `totalFileSizeBytes`, `activeDocuments`, `archivedDocuments`).

---

### 3.3 Processing Service (`/api/v1/processing/**`)

#### Processing Job Endpoints (`ProcessingJobController.java`)
- **`POST /api/v1/processing/jobs`** &rarr; Initiates asynchronous document processing pipeline.
  - **Request Body** (`ProcessingJobCreateRequest`):
    ```json
    {
      "document_id": "uuid (required)",
      "knowledge_base_id": "uuid (optional)",
      "idempotency_key": "string (optional)"
    }
    ```
  - **Response 201** (`ProcessingJobResponse`):
    ```json
    {
      "id": "uuid",
      "documentId": "uuid",
      "knowledgeBaseId": "uuid",
      "status": "QUEUED | PROCESSING | COMPLETED | FAILED | CANCELLED",
      "stage": "INGESTION | EXTRACTION | CHUNKING | EMBEDDING | INDEXING | COMPLETED",
      "currentStep": "string",
      "attemptCount": 1,
      "maxAttempts": 3,
      "errorCode": null,
      "errorMessage": null,
      "createdAt": "iso8601",
      "updatedAt": "iso8601"
    }
    ```
- **`GET /api/v1/processing/jobs/{id}`** &rarr; Full job details.
- **`GET /api/v1/processing/jobs/{id}/status`** &rarr; Lightweight `JobStatusResponse` (status, stage, currentStep, progressPercentage).
- **`GET /api/v1/processing/documents/{documentId}/status`** &rarr; `DocumentProcessingStatusResponse` (latest job for document).
- **`GET /api/v1/processing/jobs?status=QUEUED&page=0&size=20`** &rarr; Paginated jobs.
- **`POST /api/v1/processing/jobs/{id}/retry`** &rarr; Re-enqueues failed job.
- **`POST /api/v1/processing/jobs/{id}/cancel`** &rarr; Cancels active job.

---

### 3.4 Analytics Service (`/api/analytics/**`)

All endpoints require `ANALYTICS_READ` authority or `ROLE_ADMIN`.

#### A. Dashboard Overview (`AnalyticsController.java`)
- **`GET /api/analytics/dashboard?from={iso}&to={iso}`**
  - Optional parameters `from` and `to` (ISO-8601 UTC timestamp, defaults to last 30 days; max range 365 days).
  - **Response 200** (`DashboardResponseDto`):
    ```json
    {
      "generatedAt": "2026-10-21T00:00:00Z",
      "dataStatus": "AVAILABLE | EMPTY | PARTIALLY_AVAILABLE | UPSTREAM_UNAVAILABLE",
      "periodFrom": "2026-09-21T00:00:00Z",
      "periodTo": "2026-10-21T00:00:00Z",
      "documents": {
        "total": 0,
        "processed": 0,
        "failed": 0,
        "deleted": 0
      },
      "queries": {
        "total": 0,
        "succeeded": 0,
        "failed": 0,
        "avgDurationMs": 0.0
      },
      "usage": {
        "modelUsageRequests": 0,
        "totalInputTokens": 0,
        "totalOutputTokens": 0,
        "totalTokens": 0,
        "distinctModels": 0
      },
      "users": {
        "activeUsers": 0,
        "newRegistrations": 0,
        "note": "string"
      }
    }
    ```

#### B. Detailed Analytics (`AnalyticsDetailController.java`)
- **`GET /api/analytics/documents?from={iso}&to={iso}`** &rarr; Detailed document metrics breakdown.
- **`GET /api/analytics/queries?from={iso}&to={iso}`** &rarr; Detailed query volume, latency percentiles, error rate.
- **`GET /api/analytics/usage?from={iso}&to={iso}`** &rarr; Detailed token consumption and model distribution.
- **`GET /api/analytics/users?from={iso}&to={iso}`** &rarr; Distinct user interactions and activity trends.

---

### 3.5 Notification Service (`/api/notifications/**`)

Authenticated user notification center. Recipient is derived directly from JWT principal.

- **`GET /api/notifications?page=0&size=20&unreadOnly=false`**
  - **Response 200** (`PageResponseDto<NotificationResponseDto>`):
    ```json
    {
      "content": [
        {
          "id": "uuid",
          "recipientId": "uuid",
          "type": "DOCUMENT_PROCESSED | PROCESSING_FAILED | SYSTEM_ALERT | ...",
          "title": "string",
          "message": "string",
          "metadata": {},
          "sourceService": "processing-service",
          "read": false,
          "readAt": null,
          "createdAt": "2026-10-21T00:00:00Z"
        }
      ],
      "pageNumber": 0,
      "pageSize": 20,
      "totalElements": 1,
      "totalPages": 1,
      "last": true
    }
    ```
- **`GET /api/notifications/{notificationId}`** &rarr; Single notification detail.
- **`GET /api/notifications/unread-count`** &rarr; `{ "unreadCount": 3 }`.
- **`PATCH /api/notifications/{notificationId}/read`** &rarr; Marks single notification as read.
- **`PATCH /api/notifications/read-all`** &rarr; Marks all unread notifications as read. Response: `{ "updatedCount": 3 }`.
- **`DELETE /api/notifications/{notificationId}`** &rarr; Deletes notification (Response 204).

---

## 4. AI & RAG Services Architecture

### Current Status of RAG Service
- **Service**: Python FastAPI at `ai-services/rag-service/app/api/routes.py`.
- **Endpoints**:
  - `GET /health` &rarr; UP
  - `POST /query/embedding` &rarr; Generates embedding vector for query string
  - `POST /index` &rarr; Indexes text chunks into FAISS / Vector store
  - `POST /search` &rarr; Vector similarity search over chunks
- **Gateway Traversal Gap**: The Gateway (`GatewayRouteController.java`) currently routes `/api/v1/processing/**`, `/api/analytics/**`, `/api/notifications/**`, but **does NOT** configure a route mapping for `rag-service` (e.g. `/api/v1/rag/**` or `/api/v1/chat/**`).
- **Processing Service Integration**: Processing service internally calls `RagServiceClient` at `http://rag-service:8000/index` during document pipeline execution.

---

## 5. Gap Analysis & Blocked Endpoints

The following features present in the original mock frontend lack direct backend controller endpoints:

| Feature / Mock Requirement | Missing Backend Endpoint | Reason & Code Verification | Proposed Frontend Behavior |
|---|---|---|---|
| Current User Session (`/me`) | `GET /api/v1/users/me` or `/api/v1/auth/me` | Neither `AuthController` nor `UserController` defines a `/me` endpoint. | Parse `user` from `LoginResponseDto` upon authentication; persist in storage. Extract userId, email, username, roles from decoded JWT. Use `GET /api/v1/users/{userId}/profile` when profile details are needed. |
| User Directory Listing | `GET /api/v1/users` (list users) | `UserController` has CRUD by ID but no list or paginated endpoint. (`UserSummaryResponseDto` exists in DTO package but no controller mapping exists). | Display informative notice: *"User directory listing is pending backend implementation of GET /api/v1/users endpoint. Direct User Lookup by UUID is supported."* Provide input to lookup user by UUID. |
| Self-Service Password Reset | `POST /api/v1/auth/forgot-password`, `/reset-password` | DTOs exist (`ForgotPasswordRequestDto`, `ResetPasswordRequestDto`), but no controller endpoint is exposed in `AuthController`. | Show informative alert on Forgot Password tab: *"Password reset workflow is awaiting email service integration. Contact your administrator to reset access."* |
| Direct Gateway Chat / RAG | `POST /api/v1/chat` or `POST /api/v1/rag/search` | `GatewayRouteController` does not route to `rag-service`. `rag-service` is an internal backend component. | Display banner in Chat UI: *"Live RAG inference endpoint is currently restricted to internal microservice orchestration. Demonstrating RAG capabilities in sandbox mode."* Allow interactive queries with clear indicator. |
| Binary File Upload (Direct S3/Disk) | Direct multipart upload endpoint | `DocumentController` accepts metadata JSON `DocumentRequest`, not `multipart/form-data`. Processing service initiates pipeline via `documentId`. | Ingestion workflow generates Document metadata in Knowledge Service and initiates Processing Job via `ProcessingJobController.createJob()`. |

---

## 6. Frontend-to-Backend Component Mapping

| Frontend View / Component | Backend API Calls | Data Flow & Behavior | Fallback / Degradation State |
|---|---|---|---|
| **AuthPage (`/login`, `/signup`)** | `POST /api/v1/auth/login`<br>`POST /api/v1/auth/signup` | Form sends `identifier` + `password`. Receives `accessToken`, `refreshToken`, `user`. Stores in `AuthContext` + `localStorage`. Sets Authorization header. | Shows actionable error banner on 401/400. Forgot password view displays backend integration note. |
| **AppShell (`TopBar`, `SidebarShell`)** | `GET /api/notifications/unread-count`<br>`POST /api/v1/auth/logout`<br>`POST /api/v1/auth/refresh` | Displays authenticated user username, avatar initials, role badge. TopBar Bell displays live unread count. Logout revokes token and clears storage. 401 interceptor auto-refreshes token. | If notification service unreachable, bell shows 0 without crashing. |
| **AdminDashboard (`/admin/dashboard`)** | `GET /api/analytics/dashboard`<br>`GET /api/notifications` | Renders Document summary, query volume, system usage, and active users from real analytics event store. | Displays status badge (`dataStatus`: AVAILABLE, EMPTY, etc.). Shows zero-state if database is empty. |
| **AdminAnalytics (`/admin/analytics`)** | `GET /api/analytics/dashboard`<br>`GET /api/analytics/documents`<br>`GET /api/analytics/queries`<br>`GET /api/analytics/usage` | Provides date range filtering (`7d`, `30d`, `90d`). Renders Recharts graphs with real time-series data. | Shows empty state placeholder with informative message if no events recorded yet. |
| **AdminDocuments / Approval Queue (`/admin/documents`)** | `GET /api/v1/documents/knowledge-base/{kbId}`<br>`PATCH /api/v1/documents/{id}/archive`<br>`PATCH /api/v1/documents/{id}/restore` | Displays documents from knowledge base with real processing and lifecycle statuses. | If no knowledge base selected, fetches default or prompts selection. |
| **UserManagement (`/admin/users`)** | `GET /api/v1/users/{userId}`<br>`PATCH /api/v1/users/{userId}/lock`<br>`PATCH /api/v1/users/{userId}/unlock` | Lookup and manage user by UUID; lock/unlock operations. | Clear banner explaining `GET /api/v1/users` list endpoint is pending backend implementation. |
| **ControllerDashboard (`/controller/dashboard`)** | `GET /api/v1/processing/jobs`<br>`GET /api/analytics/dashboard` | Shows active processing jobs, document counts, and pipeline status. | Graceful empty table with reload button. |
| **UploadDocs (`/controller/upload`)** | `POST /api/v1/documents`<br>`POST /api/v1/processing/jobs`<br>`GET /api/v1/processing/jobs/{id}/status` | Creates document metadata entry, then initiates processing job. Polls processing status (`QUEUED` &rarr; `PROCESSING` &rarr; `COMPLETED`). | Real-time progress bar reflecting `progressPercentage` and `stage`. |
| **Testing & ModelDashboard (`/controller/testing`, `/controller/models`)** | System monitoring / Analytics API | Displays configured models and latency metrics. | Clear badge explaining offline testing mode and gateway routing status. |
| **User Chat (`/user/chat`)** | Context from `GET /api/v1/documents`<br>Notification alerts | Displays active document corpus in sidebar. Shows RAG architecture flow and citations. | Banner indicating live gateway RAG proxy endpoint is pending backend release. |
