# File Storage & Management System

A multi-tenant file storage system with REST API backend (Spring Boot) and Next.js frontend.

## Architecture

```
[Client / Frontend (Next.js)]
        |
        v
[Backend API Service (Spring Boot)]
   |          |          |          |        |
   v          v          v          v        v
[PostgreSQL] [MinIO/S3] [Elasticsearch]  [Redis]
 (Metadata)   (Files)   (Search Index)  (Cache/Rate Limit)
        |
        v
[Prometheus + Grafana] (Observability)
```

## Tech Stack

| Layer | Technology |
|---|---|
| **Backend** | [Java 17](https://www.oracle.com/java/), [Spring Boot 3.2](https://spring.io/projects/spring-boot) |
| **Frontend** | [Next.js 15](https://nextjs.org/), [React 19](https://react.dev/), [Tailwind CSS](https://tailwindcss.com/), [shadcn/ui](https://ui.shadcn.com/) |
| **Database** | [PostgreSQL 15](https://www.postgresql.org/) |
| **Object Storage** | [MinIO](https://min.io/) (S3-compatible) |
| **Search** | [Elasticsearch 8.11](https://www.elastic.co/elasticsearch/) |
| **Cache** | [Redis 8.6](https://redis.io/) |
| **Monitoring** | [Prometheus](https://prometheus.io/), [Grafana](https://grafana.com/) |
| **Containerization** | [Docker](https://www.docker.com/), [Docker Compose](https://docs.docker.com/compose/) |

## Features

- **Multi-tenant isolation** — data fully separated per tenant
- **File upload/download** — with presigned URLs for direct MinIO upload
- **Multipart upload** — chunked upload for large files with resume capability
- **File deduplication** — SHA-256 content-addressable storage (per-tenant)
- **Soft delete** — 30-day trash with scheduled background cleanup
- **Full-text search** — Elasticsearch with Vietnamese analyzer and fuzzy matching
- **Rate limiting** — Redis sliding window algorithm per user/endpoint
- **Observability** — Prometheus metrics, structured logging, OpenTelemetry tracing
- **Role-based access control** — admin, editor, viewer roles with JWT (RS256)
- **Responsive frontend** — grid/list views, drag-and-drop upload, dark mode

## Prerequisites

- Docker & Docker Compose
- Node.js 20+
- Java 17

## Quick Start

```bash
# 1. Clone the repository
git clone <repo-url>
cd file-storage-be

# 2. Copy environment variables
cp .env.example .env

# 3. Start all infrastructure services
docker-compose up -d

# 4. Wait for services to be healthy
docker-compose ps

# 5. Start the frontend
cd file-storage-fe
npm install
npm run dev
```
