# File Storage System — Hexagonal Architecture Documentation

## Table of Contents

1. [Why Hexagonal for File Storage](#1--why-hexagonal-for-file-storage)
2. [The Three Zones (with project mapping)](#2--the-three-zones-with-project-mapping)
3. [Ports Explained (with real interfaces)](#3--ports-explained-with-real-interfaces)
4. [Data Flow: Upload File (end-to-end walkthrough)](#4--data-flow-upload-file-end-to-end-walkthrough)
5. [MapStruct: The Boundary Mapper](#5--mapstruct-the-boundary-mapper)
6. [Multi-Tenancy in the Architecture](#6--multi-tenancy-in-the-architecture)
7. [File Lifecycle in the Domain](#7--file-lifecycle-in-the-domain)
8. [What to Remember as a Developer](#8--what-to-remember-as-a-developer)

---

## 1 — Why Hexagonal for File Storage

### What problem does Hexagonal Architecture solve that layered MVC doesn't?

In a traditional layered MVC application, your code is organized by **technical concern**: controllers at the top, services in the middle, repositories at the bottom. Every layer depends on the layer below it, and — critically — your business logic in the service layer ends up **tightly coupled** to the specific database, message broker, or storage technology you chose.

Hexagonal Architecture (also called Ports and Adapters) flips this. It puts your **business logic at the center** and makes all infrastructure — databases, HTTP, message queues, object storage — plug into the center through well-defined interfaces. The business logic doesn't know or care whether it's talking to PostgreSQL, MongoDB, MinIO, or a mock in a test.

### Why is it a good fit for this File Storage system?

This system talks to **many different infrastructure technologies**:

| Technology | Purpose |
|------------|---------|
| PostgreSQL | Metadata persistence (files, tenants, users, audit logs) |
| MinIO | Object storage (actual file bytes, S3-compatible) |
| Redis | Caching hot metadata and multipart session state |
| Elasticsearch | Full-text search across file metadata |
| RabbitMQ | Async event publishing (file uploaded, deleted, purged) |
| Apache Tika | MIME type detection and content analysis |
| ShedLock | Distributed scheduling coordination |

If the domain logic (upload a file, check tenant quota, deduplicate content) were directly coupled to any of these, then **swapping MinIO for AWS S3**, or **replacing Redis with Memcached**, or **switching from RabbitMQ to Kafka** would require rewriting business logic — the part that should never change for infrastructure reasons.

Hexagonal Architecture guarantees: **the domain doesn't import a single infrastructure class**. It only defines interfaces (ports) that say *what* it needs, and adapters implement *how*.

### What would go wrong without Hexagonal Architecture?

**❌ The bad way — business logic in a controller calling JPA directly:**

```java
// BAD: Business logic mixed into a @RestController
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileJpaRepository fileRepo;
    private final TenantJpaRepository tenantRepo;
    private final MinioClient minioClient;

    @PostMapping("/api/v1/files/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        // Business rule embedded in controller
        TenantJpaEntity tenant = tenantRepo.findById(tenantId).orElseThrow();
        if (tenant.getStorageUsedBytes() + file.getSize() > tenant.getMaxStorageBytes()) {
            throw new RuntimeException("Quota exceeded");
        }

        // Infrastructure call with no abstraction
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(tenantId)
            .object(UUID.randomUUID().toString())
            .stream(file.getInputStream(), file.getSize(), -1)
            .build());

        // JPA entity used as domain model AND response
        FileJpaEntity entity = new FileJpaEntity();
        entity.setTenantId(tenantId);
        entity.setOriginalName(file.getOriginalFilename());
        fileRepo.save(entity);

        return ResponseEntity.ok(entity); // JPA entity leaked to HTTP response!
    }
}
```

**Problems with this approach:**
1. **Untestable** — to unit test the quota check, you need a real database and a real MinIO server
2. **Unchangeable** — switching from MinIO to AWS S3 means editing the controller
3. **Leaky** — the JPA entity (with database annotations) is returned as the HTTP response
4. **No reuse** — if a RabbitMQ consumer also needs to upload files, you'd duplicate all this logic

**✅ The Hexagonal way — how this project does it:**

```
FileController (adapter.in.rest.controller)
  ↓ calls interface
UploadFileUseCase (domain.port.in.file)  ← just a Java interface
  ↓ implemented by
UploadFileService (application.usecase.file)  ← pure business logic
  ↓ calls interface
ObjectStoragePort (domain.port.out)  ← just a Java interface
  ↓ implemented by
MinioStorageAdapter (adapter.out.storage)  ← MinIO-specific code isolated here
```

The controller doesn't know about MinIO. The use case doesn't know about JPA entities. The domain model doesn't know about Spring. Each piece can be tested, replaced, and understood independently.

---

## 2 — The Three Zones (with project mapping)

Hexagonal Architecture divides code into three concentric zones. Think of it like an onion: the innermost layer knows nothing about the outer layers.

```
┌─────────────────────────────────────────────────────────────────┐
│                    ZONE 3 — ADAPTERS (outside)                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │                 ZONE 2 — APPLICATION (middle)             │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              ZONE 1 — DOMAIN (inside)               │  │  │
│  │  │                                                     │  │  │
│  │  │   model/  port/in/  port/out/  exception/           │  │  │
│  │  │                                                     │  │  │
│  │  │   Pure Java. No Spring. No JPA. No annotations.     │  │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │   usecase/file/  usecase/tenant/  usecase/user/           │  │
│  │                                                           │  │
│  │   Orchestration. @Service + @Transactional only.          │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  adapter/in/rest/   adapter/in/messaging/                       │
│  adapter/out/persistence/  adapter/out/storage/  ...            │
│                                                                 │
│  All framework code lives here.                                 │
└─────────────────────────────────────────────────────────────────┘
```

### Zone 1 — Domain (the inside)

**What lives here:** Pure Java classes with zero framework dependencies. No `@Entity`, no `@Service`, no `@Autowired`. Just plain Java classes, interfaces, and enums.

**Package:** `com.filestorage.domain`

| Sub-package | What it contains | Examples (planned classes) |
|-------------|-----------------|---------------------------|
| `domain.model` | Domain entities and value objects — the core business concepts | `File.java`, `Tenant.java`, `User.java`, `FileContent.java`, `MultipartSession.java`, `AuditLog.java` |
| `domain.model.enums` | Domain enumerations representing constrained value sets | `FileStatus.java`, `Role.java`, `AuditAction.java` |
| `domain.port.in` | **Inbound port interfaces** — operations the outside world can invoke | Organized by subdomain: `file/`, `tenant/`, `user/` |
| `domain.port.out` | **Outbound port interfaces** — infrastructure capabilities the domain needs | `FileRepository.java`, `ObjectStoragePort.java`, `EventPublisherPort.java`, `CachePort.java`, etc. |
| `domain.exception` | Typed business exceptions carrying semantic meaning | `TenantQuotaExceededException.java`, `FileNotFoundException.java`, `InvalidFileStateException.java` |

**Why ZERO Spring dependencies?** Because the domain is the most important code in the system — it contains the business rules that justify the project's existence. By keeping it dependency-free:

- It compiles and runs without Spring Boot — you can unit test it with plain JUnit and no application context
- It never breaks because of a Spring version upgrade
- It can be extracted into a shared library if another service needs the same domain logic
- A developer can read and understand the business rules without knowing Spring

**Example — `File.java` as a pure domain model:**

```java
// com.filestorage.domain.model.File
// Notice: NO @Entity, NO @Table, NO @Column — just plain Java

public class File {
    private String id;
    private String tenantId;
    private String uploadedByUserId;
    private String originalName;
    private String mimeType;
    private long sizeBytes;
    private String storageKey;
    private String contentHash;
    private FileStatus status;          // domain enum
    private Instant createdAt;
    private Instant updatedAt;

    // Domain behavior — state transition with business rule enforcement
    public void markPendingDelete() {
        if (this.status == FileStatus.DELETED) {
            throw new FileAlreadyDeletedException(this.id);
        }
        if (this.status != FileStatus.ACTIVE) {
            throw new InvalidFileStateException(this.id, this.status, FileStatus.PENDING_DELETE);
        }
        this.status = FileStatus.PENDING_DELETE;
        this.updatedAt = Instant.now();
    }

    public void restore() {
        if (this.status != FileStatus.PENDING_DELETE) {
            throw new InvalidFileStateException(this.id, this.status, FileStatus.ACTIVE);
        }
        this.status = FileStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }
}
```

The `File` domain object **owns its state transitions**. The rule "you can only delete an ACTIVE file" lives right here — not scattered across controllers or database triggers.

### Zone 2 — Application (use cases)

**What lives here:** Orchestration logic. Each use case class coordinates a single business operation by calling the appropriate domain objects and outbound ports. Use cases enforce business rules, own transaction boundaries, and are the only place `@Transactional` appears.

**Package:** `com.filestorage.application.usecase`

| Sub-package | Use Case Classes | What they orchestrate |
|-------------|-----------------|----------------------|
| `usecase.file` | `UploadFileService`, `GetFileService`, `DeleteFileService`, `RestoreFileService`, `SearchFilesService`, `InitiateMultipartUploadService`, `CompleteMultipartUploadService`, `AbortMultipartUploadService` | All file operations |
| `usecase.tenant` | `CreateTenantService`, `GetTenantService`, `DeactivateTenantService` | Tenant lifecycle |
| `usecase.user` | `ProvisionUserService`, `GetUserService`, `DeactivateUserService` | User provisioning |

**How a use case is structured:**

```java
// com.filestorage.application.usecase.file.UploadFileService

@Service                                       // Spring manages it as a bean
@Transactional                                  // Owns the transaction boundary
public class UploadFileService implements UploadFileUseCase {  // implements inbound port

    // Constructor injection — all dependencies are OUTBOUND PORT INTERFACES
    private final TenantRepository tenantRepository;
    private final FileRepository fileRepository;
    private final FileContentRepository fileContentRepository;
    private final ObjectStoragePort objectStoragePort;
    private final TikaAnalyzerPort tikaAnalyzerPort;
    private final EventPublisherPort eventPublisherPort;
    private final AuditLogPort auditLogPort;

    public UploadFileService(TenantRepository tenantRepository,
                             FileRepository fileRepository,
                             /* ... all ports injected ... */) {
        this.tenantRepository = tenantRepository;
        // ...
    }

    @Override
    public File getPresignedUrl(UploadFileCommand command) {
    }
}
```

Key observation: `UploadFileService` doesn't import a single adapter class. It only knows about interfaces defined in `domain.port.out`. Spring's dependency injection wires the real adapters at runtime.

### Zone 3 — Adapters (the outside)

**What lives here:** All framework-specific, infrastructure-specific code. This is where Spring MVC, JPA, MinIO SDK, RabbitMQ, Redis, and Elasticsearch live. Adapters are the **replaceable outer shell** of the application.

**Packages:** `com.filestorage.adapter.in.*` and `com.filestorage.adapter.out.*`

#### Complete Adapter Map

| Adapter | Direction | Package | Technology | Port it implements/calls |
|---------|-----------|---------|------------|--------------------------|
| **REST API** | Inbound | `adapter.in.rest` | Spring MVC | Calls `UploadFileUseCase`, `GetFileUseCase`, `DeleteFileUseCase`, `SearchFilesUseCase`, `CreateTenantUseCase`, etc. |
| **RabbitMQ consumer** | Inbound | `adapter.in.messaging` | Spring AMQP | Calls inbound use case ports (e.g., search indexing in response to file events) |
| **PostgreSQL** | Outbound | `adapter.out.persistence` | Spring Data JPA, Flyway | Implements `FileRepository`, `TenantRepository`, `UserRepository`, `FileContentRepository`, `MultipartSessionRepository`, `AuditLogPort` |
| **MinIO** | Outbound | `adapter.out.storage` | MinIO Java SDK | Implements `ObjectStoragePort` |
| **Elasticsearch** | Outbound | `adapter.out.search` | ES Java Client 8.x | Implements `FileSearchPort` |
| **Redis** | Outbound | `adapter.out.cache` | Spring Data Redis | Implements `CachePort` |
| **RabbitMQ publisher** | Outbound | `adapter.out.messaging` | Spring AMQP | Implements `EventPublisherPort` |
| **Scheduled jobs** | Outbound | `adapter.out.scheduling` | ShedLock + Spring Scheduling | Calls inbound ports (`DeleteFileUseCase`, etc.) — never repositories directly |
| **Apache Tika** | Outbound | `adapter.out.analysis` | Apache Tika, PDFBox, POI | Implements `TikaAnalyzerPort` |
| **Micrometer Metrics** | Outbound | `adapter.out.monitoring` | Micrometer, Prometheus | Exposes storage metrics (upload histogram, dedup rate) |

#### REST Adapter sub-packages

| Sub-package | Purpose | Key Classes |
|-------------|---------|-------------|
| `adapter.in.rest.controller` | HTTP endpoints, delegates to inbound ports | `FileController`, `TenantController`, `UserController`, `MultipartUploadController` |
| `adapter.in.rest.dto.request` | Incoming JSON shapes with Bean Validation | `UploadFileRequest`, `CreateTenantRequest`, `ProvisionUserRequest` |
| `adapter.in.rest.dto.response` | Outgoing JSON shapes (never domain models) | `FileResponse`, `TenantResponse`, `ProblemDetailResponse` |
| `adapter.in.rest.mapper` | MapStruct mappers at the REST boundary | `FileRestMapper`, `TenantRestMapper`, `UserRestMapper` |
| `adapter.in.rest.security` | JWT authentication filter and context | `JwtAuthFilter`, `JwtTokenValidator`, `TenantAwareAuthContext` |
| `adapter.in.rest.advice` | Global exception → HTTP error mapping | `GlobalExceptionHandler` |

#### Persistence Adapter sub-packages

| Sub-package | Purpose | Key Classes |
|-------------|---------|-------------|
| `adapter.out.persistence.entity` | JPA `@Entity` classes (database representation) | `FileJpaEntity`, `TenantJpaEntity`, `AuditLogJpaEntity` |
| `adapter.out.persistence.repository` | Spring Data JPA `@Repository` interfaces | `FileJpaRepository`, `TenantJpaRepository` |
| `adapter.out.persistence.mapper` | MapStruct mappers at the persistence boundary | `FilePersistenceMapper`, `TenantPersistenceMapper` |
| `adapter.out.persistence.adapter` | Adapter classes implementing domain port interfaces | `FileRepositoryAdapter`, `TenantRepositoryAdapter`, `AuditLogAdapter` |

---

## 3 — Ports Explained (with real interfaces)

### What is a Port?

A **port** is a Java interface that defines a boundary between the domain and the outside world. Ports exist so the domain never directly depends on infrastructure code.

There are two kinds:

### Inbound Ports (`domain.port.in`)

**What they are:** Interfaces that define operations the outside world can ask the domain to perform. Think of them as the domain's public API.

**Who implements them:** Use case classes in `application.usecase`.
**Who calls them:** Inbound adapters (controllers, message consumers, schedulers).

```java
// com.filestorage.domain.port.in.file.UploadFileUseCase
public interface UploadFileUseCase {
    File execute(UploadFileCommand command);
}

// The command object carries all input data — tenantId included
public record UploadFileCommand(
    String tenantId,
    String userId,
    String originalFileName,
    byte[] content
) {}
```

```java
// com.filestorage.domain.port.in.file.DeleteFileUseCase
public interface DeleteFileUseCase {
    void execute(String tenantId, String fileId, String userId);
}
```

```java
// com.filestorage.domain.port.in.file.SearchFilesUseCase
public interface SearchFilesUseCase {
    List<File> execute(String tenantId, String query, int page, int size);
}
```

**Why controllers depend on the INTERFACE, not the implementation:**

```java
// FileController depends on UploadFileUseCase (interface), NOT UploadFileService (class)
@RestController
public class FileController {
    private final UploadFileUseCase uploadFileUseCase;  // ← interface type

    public FileController(UploadFileUseCase uploadFileUseCase) {
        this.uploadFileUseCase = uploadFileUseCase;
    }
}
```

This means you can swap `UploadFileService` for a `MockUploadFileService` in tests without touching the controller. The controller doesn't know — and doesn't care — which class implements the interface.

### Outbound Ports (`domain.port.out`)

**What they are:** Interfaces that define what the domain **needs from the outside world**. The domain says "I need to save a file" — it doesn't say "I need PostgreSQL."

**Who defines them:** The domain layer.
**Who implements them:** Outbound adapters.

```java
// com.filestorage.domain.port.out.FileRepository
public interface FileRepository {
    File save(File file);
    Optional<File> findByIdAndTenantId(String id, String tenantId);
    List<File> findByTenantIdAndStatus(String tenantId, FileStatus status);
    void deleteById(String id);
}
```

```java
// com.filestorage.domain.port.out.ObjectStoragePort
public interface ObjectStoragePort {
    String upload(String tenantId, String storageKey, byte[] content, String contentType);
    byte[] download(String tenantId, String storageKey);
    void delete(String tenantId, String storageKey);
    String generatePresignedUrl(String tenantId, String storageKey, Duration expiry);
}
```

```java
// com.filestorage.domain.port.out.EventPublisherPort
public interface EventPublisherPort {
    void publishFileUploaded(String tenantId, String fileId, String userId);
    void publishFileDeleted(String tenantId, String fileId, String userId);
    void publishFilePurged(String tenantId, String fileId);
}
```

```java
// com.filestorage.domain.port.out.AuditLogPort
public interface AuditLogPort {
    void record(AuditLog auditLog);
}
```

**Why the domain defines these interfaces — Dependency Inversion:**

In a traditional layered architecture, the service layer depends on the repository layer: `Service → Repository → Database`. The dependency arrow points downward.

In Hexagonal Architecture, the domain defines the interface, and the adapter implements it: `Adapter → (implements) → Port ← (uses) ← Use Case`. The dependency arrow points **inward**, toward the domain.

This is the **Dependency Inversion Principle** (the D in SOLID): high-level modules (domain) should not depend on low-level modules (database adapters). Both should depend on abstractions (ports).

### The Dependency Rule — Visualized

```
                    DEPENDENCY ARROWS POINT INWARD
                    ==============================

  HTTP Request
       │
       ▼
  ┌─────────────────┐         ┌──────────────────┐
  │  FileController  │         │ JwtAuthFilter    │
  │  (REST Adapter)  │         │ (Security)       │
  └────────┬────────┘         └──────────────────┘
           │  depends on
           ▼
  ┌─────────────────────────┐
  │  UploadFileUseCase      │  ← Inbound Port (interface in domain)
  └────────┬────────────────┘
           │  implemented by
           ▼
  ┌─────────────────────────┐
  │  UploadFileService      │  ← Application Use Case
  │  (application.usecase)  │
  └────────┬────────────────┘
           │  depends on
           ▼
  ┌─────────────────────────┐
  │  FileRepository         │  ← Outbound Port (interface in domain)
  │  ObjectStoragePort      │
  │  EventPublisherPort     │
  └────────┬────────────────┘
           │  implemented by
           ▼
  ┌─────────────────────────┐         ┌──────────────────┐
  │  FileRepositoryAdapter  │         │  MinioStorage     │
  │  (JPA Adapter)          │         │  Adapter          │
  └────────┬────────────────┘         └────────┬─────────┘
           │                                    │
           ▼                                    ▼
      PostgreSQL                             MinIO
```

**The rule:** Adapters depend on the domain. The domain **never** depends on adapters. If you see an `import com.filestorage.adapter.*` statement inside any file under `com.filestorage.domain`, that's an architecture violation — and the planned `HexagonalArchitectureTest.java` (ArchUnit) will catch it automatically.

---

## 4 — Data Flow: Upload File (end-to-end walkthrough)

Here's what happens when a user uploads a file, step by step. For each step: which class, which package, what it does, and which port boundary it crosses.

```
Step   Class                          Package                              Action                                                Port Crossed
─────  ─────────────────────────────  ───────────────────────────────────  ────────────────────────────────────────────────────  ──────────────────────────
 1     JwtAuthFilter                  adapter.in.rest.security             Intercepts HTTP request, validates JWT signature       —
                                                                           via JwtTokenValidator, extracts tenantId + userId
                                                                           + role claims, populates SecurityContext with
                                                                           TenantAwareAuthContext

 2     FileController                 adapter.in.rest.controller           Receives POST /api/v1/files/upload with                —
                                                                           MultipartFile. Reads TenantAwareAuthContext
                                                                           from SecurityContext to get tenantId and userId.

 3     FileRestMapper                 adapter.in.rest.mapper               MapStruct mapper converts MultipartFile +              REST ↔ Domain boundary
                                                                           auth context into UploadFileCommand
                                                                           (domain command object)

 4     UploadFileService              application.usecase.file             Use case invoked via UploadFileUseCase                 UploadFileUseCase
                                                                           interface. @Transactional begins.                      (inbound port)

 5     TenantRepository               domain.port.out                      Use case calls tenantRepository.findById               TenantRepository
       → TenantRepositoryAdapter      adapter.out.persistence.adapter      (tenantId). Adapter queries PostgreSQL,                (outbound port)
                                                                           converts TenantJpaEntity → Tenant domain model.
                                                                           Use case checks: is tenant active? Is quota OK?

 6     TikaAnalyzerPort               domain.port.out                      Use case calls tikaAnalyzerPort                       TikaAnalyzerPort
       → TikaAnalyzerAdapter          adapter.out.analysis                 .detectMimeType(bytes). Adapter uses                   (outbound port)
                                                                           Apache Tika to detect actual MIME type
                                                                           (ignores client-supplied Content-Type for security).

 7     FileContentRepository          domain.port.out                      Use case computes SHA-256 hash of file bytes.          FileContentRepository
       → FileContentRepoAdapter       adapter.out.persistence.adapter      Calls fileContentRepository                            (outbound port)
                                                                           .findByHashAndTenantId(hash, tenantId).
                                                                           If found → dedup hit: reuse existing storageKey,
                                                                           increment refCount. If not → new content.

 8     ObjectStoragePort              domain.port.out                      [Only if new content] Use case calls                   ObjectStoragePort
       → MinioStorageAdapter          adapter.out.storage                  objectStoragePort.upload(tenantId, key, bytes).        (outbound port)
                                                                           Adapter calls MinIO SDK putObject().

 9     FileRepository                 domain.port.out                      Use case constructs File domain object                 FileRepository
       → FileRepositoryAdapter        adapter.out.persistence.adapter      with status=ACTIVE, calls fileRepository.save().       (outbound port)
                                                                           Adapter converts File → FileJpaEntity via
                                                                           FilePersistenceMapper, saves via JPA.

10     EventPublisherPort             domain.port.out                      Use case calls eventPublisherPort                     EventPublisherPort
       → FileEventPublisher           adapter.out.messaging                .publishFileUploaded(tenantId, fileId, userId).       (outbound port)
                                                                           Adapter wraps data in FileUploadedEvent,
                                                                           publishes to RabbitMQ exchange `file.events`
                                                                           with routing key `file.uploaded`.

11     AuditLogPort                   domain.port.out                      Use case constructs AuditLog domain object            AuditLogPort
       → AuditLogAdapter              adapter.out.persistence.adapter      (action=FILE_UPLOADED, tenantId, userId, fileId),     (outbound port)
                                                                           calls auditLogPort.record(). Adapter converts
                                                                           to AuditLogJpaEntity, saves to PostgreSQL.

12     FileRestMapper                 adapter.in.rest.mapper               Back in FileController: MapStruct mapper              Domain ↔ REST boundary
                                                                           converts File domain model → FileResponse DTO.
                                                                           Domain model is NEVER returned as HTTP response.

13     FileController                 adapter.in.rest.controller           Returns ResponseEntity.status(201)                    —
                                                                           .body(fileResponse). HTTP 201 Created.
```

**After the HTTP response (asynchronous):**

The `FileUploadedEvent` published to RabbitMQ is consumed by `FileEventConsumer` (`adapter.in.messaging`), which triggers Elasticsearch indexing via `FileSearchPort` → `ElasticsearchSearchAdapter`. This happens outside the original HTTP request/response cycle.

---

## 5 — MapStruct: The Boundary Mapper

### Why MapStruct is used at EVERY adapter boundary

A core rule of Hexagonal Architecture: **domain models never leak out of the domain**. The domain's `File.java` is not a JPA entity. It's not a JSON response. It's a pure business object. Every time data crosses a boundary (HTTP ↔ Domain, Domain ↔ Database), it must be converted.

MapStruct generates this conversion code at compile time — zero reflection, zero runtime cost, compile-time type safety.

### REST Boundary Mapping

```java
// com.filestorage.adapter.in.rest.mapper.FileRestMapper
@Mapper(componentModel = "spring")
public interface FileRestMapper {

    // Inbound: HTTP request → Domain command
    UploadFileCommand toCommand(UploadFileRequest request, String tenantId, String userId);

    // Outbound: Domain model → HTTP response
    FileResponse toResponse(File file);

    List<FileResponse> toResponseList(List<File> files);
}
```

**What this prevents:**
```java
// ❌ WRONG: returning domain model directly
return ResponseEntity.ok(file);  // Exposes internal domain structure to API consumers

// ✅ CORRECT: mapping to DTO at the boundary
return ResponseEntity.ok(fileRestMapper.toResponse(file));
```

### Persistence Boundary Mapping

```java
// com.filestorage.adapter.out.persistence.mapper.FilePersistenceMapper
@Mapper(componentModel = "spring")
public interface FilePersistenceMapper {

    // Domain → JPA entity (for saving)
    FileJpaEntity toEntity(File file);

    // JPA entity → Domain (for reading)
    File toDomain(FileJpaEntity entity);

    List<File> toDomainList(List<FileJpaEntity> entities);
}
```

**Why this separation matters:** `FileJpaEntity` has `@Entity`, `@Table`, `@Column`, `@ManyToOne` — all JPA-specific annotations. The domain `File` has none. If you passed the JPA entity into the use case, the domain would need to know about JPA. If you returned the domain model from the controller, the API contract would break every time you refactored the domain. MapStruct keeps both worlds cleanly separated.

### Other persistence mappers follow the same pattern:

| Mapper | Package | Converts between |
|--------|---------|-----------------|
| `TenantPersistenceMapper` | `adapter.out.persistence.mapper` | `Tenant` ↔ `TenantJpaEntity` |
| `UserPersistenceMapper` | `adapter.out.persistence.mapper` | `User` ↔ `UserJpaEntity` |
| `FileContentPersistenceMapper` | `adapter.out.persistence.mapper` | `FileContent` ↔ `FileContentJpaEntity` |
| `MultipartSessionPersistenceMapper` | `adapter.out.persistence.mapper` | `MultipartSession` ↔ `MultipartSessionJpaEntity` |
| `AuditLogPersistenceMapper` | `adapter.out.persistence.mapper` | `AuditLog` ↔ `AuditLogJpaEntity` |

## 6 — Multi-Tenancy in the Architecture

This system serves **multiple organizations (tenants)** from a single deployment. Tenant isolation is enforced at every layer — not just the database.

### How tenantId flows through the entire stack

```
JWT Token (issued by Auth0)
  │  contains claim: "tenantId": "tenant_abc123"
  │
  ▼
JwtAuthFilter (adapter.in.rest.security)
  │  Extracts tenantId from JWT claims
  │  Creates TenantAwareAuthContext(tenantId, userId, role)
  │  Sets into SecurityContext
  │
  ▼
FileController (adapter.in.rest.controller)
  │  Reads tenantId from SecurityContext
  │  Passes into UploadFileCommand(tenantId, ...)
  │
  ▼
UploadFileService (application.usecase.file)
  │  Receives tenantId inside the command object
  │  Passes tenantId to EVERY outbound port call
  │
  ▼
FileRepository.findByIdAndTenantId(fileId, tenantId)
ObjectStoragePort.upload(tenantId, key, bytes)
EventPublisherPort.publishFileUploaded(tenantId, fileId, ...)
```

### Key tenant isolation guarantees

1. **Every outbound port method receives `tenantId` as a parameter.** There is no global `TenantContext.getCurrentTenant()` stored in a ThreadLocal. The tenantId is explicitly passed, making it impossible to accidentally forget.

2. **Every JPA query filters by `tenant_id`.** The method is `findByIdAndTenantId`, not `findById`. Even if a user somehow crafts a request with another tenant's file ID, the query returns empty — cross-tenant data access is structurally impossible.

3. **MinIO objects are namespaced by tenant.** Each tenant's files are stored under a tenant-specific prefix or bucket: `objectStoragePort.upload(tenantId, storageKey, bytes)`. The adapter uses `tenantId` as the bucket name or key prefix.

4. **Deduplication (FileContent) is within-tenant only.** The SHA-256 dedup lookup is: `fileContentRepository.findByHashAndTenantId(hash, tenantId)`. Two tenants uploading identical files get separate `FileContent` entries and separate MinIO objects. This is intentional: cross-tenant dedup would create data dependencies between tenants and leak information (tenant A could infer tenant B has the same file).

5. **Audit logs record `tenantId`.** Every audit entry includes the tenant, ensuring compliance reporting is tenant-scoped.

### Why not ThreadLocal-based tenant context?

Some multi-tenant systems use a `ThreadLocal<String>` to store the current tenant ID globally. This is dangerous because:
- Async operations (RabbitMQ consumers, scheduled tasks) run on different threads — the ThreadLocal is empty
- If a developer forgets to set it, queries run without tenant filtering — data leak
- It's implicit and invisible — hard to debug, hard to test

This project uses **explicit parameter passing**: the tenantId is always visible in method signatures. If you forget to pass it, the code won't compile.

---

## 7 — File Lifecycle in the Domain

### State Machine

Files transition through these states as a domain concept. This is enforced by the domain model itself, not by database triggers.

```
                         upload succeeds
    ┌──────────┐       ┌──────────────────┐
    │ PENDING  │──────▶│     ACTIVE       │
    └──────────┘       └──────────────────┘
         │                    │         ▲
         │ upload fails       │ soft    │ restore
         ▼                    │ delete  │
    ┌──────────────┐          ▼         │
    │UPLOAD_FAILED │   ┌───────────────────┐
    └──────────────┘   │ PENDING_DELETE    │
                       └───────────────────┘
                              │
                              │ scheduled purge
                              ▼
                       ┌──────────────────┐
                       │    DELETED       │
                       └──────────────────┘
```

### Where is the state machine enforced?

In the **domain model** (`domain.model.File`), not in database triggers or service code. The `File` object has methods like `markPendingDelete()`, `restore()`, and `markDeleted()` that check preconditions and throw `InvalidFileStateException` if the transition is illegal.

```java
// domain.model.File
public void markPendingDelete() {
    if (this.status != FileStatus.ACTIVE) {
        throw new InvalidFileStateException(this.id, this.status, FileStatus.PENDING_DELETE);
    }
    this.status = FileStatus.PENDING_DELETE;
}

public void restore() {
    if (this.status != FileStatus.PENDING_DELETE) {
        throw new InvalidFileStateException(this.id, this.status, FileStatus.ACTIVE);
    }
    this.status = FileStatus.ACTIVE;
}

public void markDeleted() {
    if (this.status != FileStatus.PENDING_DELETE) {
        throw new InvalidFileStateException(this.id, this.status, FileStatus.DELETED);
    }
    this.status = FileStatus.DELETED;
}
```

### Which use cases drive which transitions?

| Transition | Use Case | What triggers it |
|-----------|----------|-----------------|
| `PENDING → ACTIVE` | `UploadFileService` | Successful upload completion |
| `PENDING → UPLOAD_FAILED` | `UploadFileService` | Exception during upload (MinIO failure, etc.) |
| `ACTIVE → PENDING_DELETE` | `DeleteFileService` | User requests file deletion (soft delete) |
| `PENDING_DELETE → ACTIVE` | `RestoreFileService` | User restores a file within the retention window |
| `PENDING_DELETE → DELETED` | `FilePurgeScheduler` | Scheduled job runs after retention period expires |

### How ShedLock handles the PENDING_DELETE → DELETED purge

`FilePurgeScheduler` (`adapter.out.scheduling`) runs as a `@Scheduled` cron job (e.g., daily at 02:00). In a Kubernetes deployment with multiple pods, every pod has its own scheduler. Without coordination, all pods would try to purge the same files simultaneously.

**ShedLock** prevents this by acquiring a distributed lock in the `shedlock` PostgreSQL table before executing. Only one pod gets the lock; the others skip. The `lockAtMostFor` duration prevents a crashed pod from permanently holding the lock.

The scheduler's flow:
1. Acquire ShedLock → only one pod proceeds
2. Call `FileRepository.findByTenantIdAndStatus(*, PENDING_DELETE)` — find all purgeable files
3. For each file: call `file.markDeleted()` (domain state transition)
4. Delete the MinIO object via `ObjectStoragePort.delete()`
5. Decrement `FileContent.refCount` — if it reaches 0, the content blob is orphaned
6. Update tenant's `storage_used_bytes` via `TenantRepository`
7. Publish `FilePurgedEvent` via `EventPublisherPort`
8. Write audit log via `AuditLogPort`

### Why DELETED is a soft delete

The file metadata row is **kept in PostgreSQL** with `status = DELETED`. The actual bytes are deleted from MinIO, but the metadata (who uploaded it, when, what size, who deleted it) remains. This is for **audit compliance**: you need to answer questions like "who deleted file X and when?" long after the file itself is gone.

The `AuditLog` table provides a separate, immutable history — but keeping the file metadata row gives you a queryable record without joining to audit logs.

---

## 8 — What to Remember as a Developer

These are the 10 rules to follow when adding new features to this codebase.

---

### Rule 1: Where to add a new Use Case

✅ **DO:** Create a new interface in `domain.port.in/{subdomain}/` and a new class in `application.usecase/{subdomain}/`.

```java
// domain.port.in.file.RenameFileUseCase.java
public interface RenameFileUseCase {
    File execute(String tenantId, String fileId, String newName, String userId);
}

// application.usecase.file.RenameFileService.java
@Service
@Transactional
public class RenameFileService implements RenameFileUseCase { ... }
```

❌ **DON'T:** Add the method to an existing use case interface. Each use case is a single operation.

**WHY:** Single Responsibility. One interface per operation means controllers only depend on what they actually use.

---

### Rule 2: Where to add a new repository method

✅ **DO:** Add the method signature to the outbound port interface in `domain.port.out/`, then implement it in the adapter in `adapter.out.persistence.adapter/`.

```java
// domain.port.out.FileRepository.java
List<File> findByTenantIdAndMimeType(String tenantId, String mimeType);

// adapter.out.persistence.adapter.FileRepositoryAdapter.java
@Override
public List<File> findByTenantIdAndMimeType(String tenantId, String mimeType) {
    return filePersistenceMapper.toDomainList(
        fileJpaRepository.findByTenantIdAndMimeType(tenantId, mimeType)
    );
}
```

❌ **DON'T:** Add the method only to `FileJpaRepository` and call it directly from a use case.

**WHY:** The use case must depend on the domain port interface, not the JPA repository. Otherwise you've coupled business logic to JPA.

---

### Rule 3: Where to add a new REST endpoint

✅ **DO:** Add the endpoint in the appropriate controller in `adapter.in.rest.controller/`. Create any new request/response DTOs in `adapter.in.rest.dto/`. Add mapper methods to the corresponding mapper in `adapter.in.rest.mapper/`. The controller calls an inbound port, never a use case class directly.

❌ **DON'T:** Put business logic in the controller method. The controller's job is: extract input → call port → return response.

**WHY:** Controllers are adapters. They translate HTTP into domain operations. Business rules live in use cases.

---

### Rule 4: Where NOT to put business logic

✅ **DO:** Put all business rules (validation, state transitions, calculations) in domain model methods or use case classes.

❌ **DON'T:** Put business logic in:
```java
// ❌ In a controller
if (file.getSize() > tenant.getMaxFileSize()) { throw ... }

// ❌ In a JPA repository adapter
// ❌ In a MapStruct mapper
// ❌ In a @Configuration class
// ❌ In a @Scheduled method (call a use case interface instead)
```

**WHY:** Business rules in adapters are invisible to unit tests, scattered across the codebase, and duplicated when multiple adapters need the same logic.

---

### Rule 5: When to create a new Port vs reuse existing

✅ **DO:** Create a new outbound port when you're integrating a genuinely new infrastructure capability (e.g., `NotificationPort` for email/SMS, `VirusScanPort` for ClamAV).

❌ **DON'T:** Create a new port for every minor infrastructure variation. If Redis is already used via `CachePort`, don't create a `SessionCachePort` — extend `CachePort` or add methods to it if the concept is the same.

**WHY:** Ports represent distinct infrastructure *capabilities*, not distinct infrastructure *implementations*.

---

### Rule 6: How to add a new scheduled task

✅ **DO:** Create a new scheduler class in `adapter.out.scheduling/`. Annotate it with `@Scheduled` and `@SchedulerLock`. Inject an **inbound port interface** and call it.

```java
@Component
public class OrphanContentCleanupScheduler {
    private final CleanupOrphanContentUseCase cleanupUseCase;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "orphan-content-cleanup", lockAtMostFor = "PT30M")
    public void execute() {
        cleanupUseCase.execute();  // calls the inbound port
    }
}
```

❌ **DON'T:** Inject repositories or adapters directly into the scheduler. Schedulers are inbound adapters — they drive use cases, just like controllers do.

**WHY:** Business logic triggered by a schedule vs. an HTTP request should follow the same path through the same use case.

---

### Rule 7: How to add a new RabbitMQ event

✅ **DO:** (1) Add a method to `EventPublisherPort` in `domain.port.out/`. (2) Create the event class in `adapter.out.messaging.event/`. (3) Implement the method in `FileEventPublisher` (`adapter.out.messaging/`). (4) If there's a consumer, add a handler in `adapter.in.messaging/`.

❌ **DON'T:** Publish RabbitMQ messages directly from a use case using `RabbitTemplate`.

**WHY:** The use case calls `eventPublisherPort.publishSomething()`. It has no idea it's RabbitMQ. You could switch to Kafka by writing a new adapter.

---

### Rule 8: Never import adapter classes into domain

✅ **DO:** Keep `domain/` imports clean. Only `java.*` and other `domain.*` packages.

❌ **DON'T:**
```java
// ❌ NEVER in any file under com.filestorage.domain
import com.filestorage.adapter.out.persistence.entity.FileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import io.minio.MinioClient;
```

**WHY:** This is the foundational rule of Hexagonal Architecture. The `HexagonalArchitectureTest` (ArchUnit) in the test suite enforces this at compile time.

---

### Rule 9: Always use MapStruct, never manual mapping

✅ **DO:** Define all conversions in MapStruct `@Mapper` interfaces. Let MapStruct generate the code.

❌ **DON'T:**
```java
// ❌ Manual mapping
FileResponse response = new FileResponse();
response.setId(file.getId());
response.setName(file.getOriginalName());
response.setSize(file.getSizeBytes());
// ... 10 more fields manually
```

**WHY:** Manual mapping is error-prone (miss a field, copy the wrong one), verbose, and not validated at compile time. MapStruct catches missing mappings as compilation warnings/errors.

---

### Rule 10: Always pass tenantId, never rely on global state

✅ **DO:** Include `tenantId` as an explicit parameter in every command, query, port method, and repository call.

```java
// ✅ Explicit
File execute(UploadFileCommand command);  // command contains tenantId
fileRepository.findByIdAndTenantId(fileId, tenantId);
objectStoragePort.upload(tenantId, key, bytes);
```

❌ **DON'T:**
```java
// ❌ Global ThreadLocal
String tenantId = TenantContext.getCurrentTenant();
fileRepository.findById(fileId);  // No tenant filter = data leak risk
```

**WHY:** ThreadLocal tenant contexts break in async contexts (RabbitMQ consumers, scheduled tasks, `@Async` methods) and silently return cross-tenant data when forgotten. Explicit parameters make tenant isolation impossible to forget — if you omit it, the code won't compile.

---

## Appendix — Project File Tree (complete reference)

For quick reference, here is the full planned project structure. Files marked with `[scaffolded]` exist as directory placeholders (`.gitkeep` / `.txt`). Files marked with `[implemented]` contain production code.

```
com.filestorage/
├── FilestorageApplication.java                          [implemented]
│
├── domain/
│   ├── model/
│   │   ├── Tenant.java                                  [scaffolded]
│   │   ├── User.java                                    [scaffolded]
│   │   ├── File.java                                    [scaffolded]
│   │   ├── FileContent.java                             [scaffolded]
│   │   ├── MultipartSession.java                        [scaffolded]
│   │   ├── AuditLog.java                                [scaffolded]
│   │   └── enums/
│   │       ├── Role.java                                [scaffolded]
│   │       ├── FileStatus.java                          [scaffolded]
│   │       └── AuditAction.java                         [scaffolded]
│   ├── port/
│   │   ├── in/
│   │   │   ├── file/     (8 use case interfaces)        [scaffolded]
│   │   │   ├── tenant/   (3 use case interfaces)        [scaffolded]
│   │   │   └── user/     (3 use case interfaces)        [scaffolded]
│   │   └── out/
│   │       ├── TenantRepository.java                    [scaffolded]
│   │       ├── UserRepository.java                      [scaffolded]
│   │       ├── FileRepository.java                      [scaffolded]
│   │       ├── FileContentRepository.java               [scaffolded]
│   │       ├── MultipartSessionRepository.java          [scaffolded]
│   │       ├── AuditLogPort.java                        [scaffolded]
│   │       ├── ObjectStoragePort.java                   [scaffolded]
│   │       ├── FileSearchPort.java                      [scaffolded]
│   │       ├── CachePort.java                           [scaffolded]
│   │       ├── EventPublisherPort.java                  [scaffolded]
│   │       └── TikaAnalyzerPort.java                    [scaffolded]
│   └── exception/
│       ├── DomainException.java                         [scaffolded]
│       ├── TenantNotFoundException.java                 [scaffolded]
│       ├── TenantQuotaExceededException.java            [scaffolded]
│       └── ... (8 more exception classes)               [scaffolded]
│
├── application/
│   └── usecase/
│       ├── file/     (8 service classes)                [scaffolded]
│       ├── tenant/   (3 service classes)                [scaffolded]
│       └── user/     (3 service classes)                [scaffolded]
│
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/   (4 controllers)            [scaffolded]
│   │   │   ├── dto/request/  (5 request DTOs)           [scaffolded]
│   │   │   ├── dto/response/ (7 response DTOs)          [scaffolded]
│   │   │   ├── mapper/       (3 REST mappers)           [scaffolded]
│   │   │   ├── security/     (3 security classes)       [scaffolded]
│   │   │   └── advice/       (GlobalExceptionHandler)   [scaffolded]
│   │   └── messaging/
│   │       └── FileEventConsumer.java                   [scaffolded]
│   └── out/
│       ├── persistence/
│       │   ├── entity/       (6 JPA entities)           [scaffolded]
│       │   ├── repository/   (6 JPA repositories)       [scaffolded]
│       │   ├── mapper/       (6 persistence mappers)    [scaffolded]
│       │   └── adapter/      (6 adapter classes)        [scaffolded]
│       ├── storage/          (MinIO adapter)            [scaffolded]
│       ├── search/           (Elasticsearch adapter)    [scaffolded]
│       ├── cache/            (Redis adapter)            [scaffolded]
│       ├── messaging/        (RabbitMQ publisher)       [scaffolded]
│       ├── scheduling/       (3 scheduled jobs)         [scaffolded]
│       ├── analysis/         (Tika adapter)             [scaffolded]
│       └── monitoring/       (Micrometer metrics)       [scaffolded]
│
└── infrastructure/
    └── config/               (@Configuration classes)   [scaffolded]
```

---

> [!TIP]
> The architecture is enforced at compile time by `HexagonalArchitectureTest.java` in the test suite (`src/test/java/com/filestorage/architecture/`). This uses ArchUnit to verify that domain packages never import adapter packages. Run it as part of your normal test suite — any architecture violation will fail the build.
