# OMRAGUL

Offline Multimodal Retrieval-Augmented Generation Unified Library (OMRAGUL) is an enterprise-grade, offline-first knowledge management and multimodal RAG platform built with Java Spring Boot microservices, Python FastAPI AI services, and a React frontend.

---

## 1. Architecture Overview

- **Edge Gateway:** `gateway-service` (Port 8080) — Reverse proxy, CORS, JWT authentication filter, header sanitization.
- **Identity & Auth:** `identity-service` (Port 8081) — RS256 JWT token issuance, refresh tokens, user management.
- **Knowledge Core:** `knowledge-service` (Port 8082) — Knowledge bases, folders, documents, metadata, permissions, PostgreSQL Flyway migrations.
- **Orchestration:** `processing-service` (Port 8083) — Asynchronous document processing pipeline, status synchronization.
- **Analytics:** `analytics-service` (Port 8084) — Usage tracking and reporting.
- **Notifications:** `notification-service` (Port 8085) — Asynchronous alerting and user notifications.
- **AI Services:**
  - `embedding-service` (Port 8001) — Vector embeddings (`bge-m3`).
  - `rag-service` (Port 8002) — FAISS vector search and retrieval-augmented generation.
  - `multimodal-service` (Port 8003) — OCR, PDF text extraction, Whisper audio transcription.
  - `agent-service` (Port 8004) — Autonomous agent workflows and LLM reasoning.
- **Frontend:** React + Vite Single Page Application (Port 3000).
- **Infrastructure:** PostgreSQL 17, Redis 8, Ollama local LLM runtime, pgAdmin 4.

---

## 2. Centralized Environment Configuration

All environment configuration is centralized at the **repository root**. Service-level `.env` files and duplicate `.env.example` templates have been consolidated into the single authoritative root template.

### Setup Instructions

#### Step 1: Create your local environment configuration
Copy the tracked `.env.example` template to `.env` in the repository root:

```bash
cp .env.example .env
```

> **IMPORTANT:**
> Never commit `.env` or any file containing real credentials to Git. The `.gitignore` file is strictly configured to protect `.env`, `.env.*`, and private keys.

#### Step 2: Configure required local values
Edit `.env` to set strong, unique passwords for local development:

```env
# Database Credentials
POSTGRES_USER=postgres
POSTGRES_PASSWORD=your_secure_local_postgres_password
DB_PASSWORD=your_secure_local_postgres_password

# Redis Credentials
REDIS_PASSWORD=your_secure_local_redis_password

# Internal Service Authentication Secret
INTERNAL_SERVICE_SECRET=your_secure_random_internal_cluster_secret
```

#### Step 3: Provision Local RSA Keys for JWT Authentication
Identity Service requires an RS256 RSA private key to sign JWTs. Other microservices verify tokens using the corresponding public key (`certs/dev_public_key.pem`).

Create the ignored `secrets/` directory and generate a 2048-bit RSA private key:

```bash
mkdir -p secrets
openssl genrsa -out secrets/private_key.pem 2048
```

The `secrets/` directory is excluded by `.gitignore` and is mounted read-only into Identity Service in Docker Compose.

---

## 3. Required Environment Variables Reference

| Category | Variable | Description | Default / Example |
| :--- | :--- | :--- | :--- |
| **Application** | `SPRING_PROFILES_ACTIVE` | Active Spring profile | `docker` |
| **Database** | `POSTGRES_PORT` | PostgreSQL host port | `5432` |
| | `POSTGRES_USER` | PostgreSQL superuser | `postgres` |
| | `POSTGRES_PASSWORD` | PostgreSQL password (**Mandatory**) | *No default* |
| | `DB_PASSWORD` | Shared datasource password alias | *Matches POSTGRES_PASSWORD* |
| **Redis** | `REDIS_PORT` | Redis host port | `6379` |
| | `REDIS_PASSWORD` | Redis auth password (**Mandatory**) | *No default* |
| **Internal Trust** | `INTERNAL_SERVICE_SECRET` | Cluster internal auth secret (**Mandatory**) | *Fail-fast if missing or default* |
| **JWT** | `IDENTITY_JWT_PRIVATE_KEY_PATH` | Path to RSA private key | `file:/secrets/private_key.pem` |
| | `IDENTITY_JWT_PUBLIC_KEY_PATH` | Path to RSA public key | `classpath:certs/dev_public_key.pem` |
| | `IDENTITY_JWT_ISSUER` | Expected JWT issuer claim | `http://localhost:8080` |
| **AI Models** | `EMBEDDING_MODEL` | HuggingFace/Ollama embedding model | `bge-m3` |
| | `EMBEDDING_DIMENSION` | Embedding vector dimensionality | `1024` |
| | `RAG_OLLAMA_MODEL` | Ollama model for RAG queries | `qwen3:4b` |
| | `MULTIMODAL_OLLAMA_MODEL`| Vision model for multimodal tasks | `qwen3-vl:4b` |
| | `WHISPER_MODEL` | Faster-Whisper model for audio | `small` |
| **Frontend** | `VITE_API_GATEWAY_URL` | Public Gateway URL for browser | `http://localhost:8080` |

---

## 4. Running the Platform

### Start all core services with Docker Compose:
```bash
docker compose -f docker-compose.dev.yml up -d
```

### Validate Docker Compose configuration:
```bash
docker compose -f docker-compose.dev.yml config -q
```

### Enable optional management profiles:
```bash
# Start pgAdmin database manager
docker compose -f docker-compose.dev.yml --profile tools up -d

# Start Nginx edge proxy
docker compose -f docker-compose.dev.yml --profile edge up -d
```

---

## 5. Running Automated Tests

All automated unit and slice tests execute using isolated test configurations (H2 in-memory databases and test-only keys) and do not depend on production environment variables.

### Knowledge Service:
```bash
cd backend/knowledge-service
./mvnw clean test
```

### Processing Service:
```bash
cd backend/processing-service
./mvnw clean test
```

### Gateway Service:
```bash
cd backend/gateway-service
./mvnw clean test
```

### Frontend Build:
```bash
cd frontend
npm run build
```
