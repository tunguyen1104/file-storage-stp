# Hệ thống Lưu trữ File — Tài liệu Kiến trúc Hexagonal

## Mục lục

1. [Tại sao dùng Hexagonal cho File Storage](#1--tại-sao-dùng-hexagonal-cho-file-storage)
2. [Ba Vùng (kèm mapping project thực tế)](#2--ba-vùng-kèm-mapping-project-thực-tế)
3. [Ports là gì (kèm interface thực tế)](#3--ports-là-gì-kèm-interface-thực-tế)
4. [Luồng dữ liệu: Upload File (walkthrough end-to-end)](#4--luồng-dữ-liệu-upload-file-walkthrough-end-to-end)
5. [MapStruct: Bộ chuyển đổi tại Boundary](#5--mapstruct-bộ-chuyển-đổi-tại-boundary)
6. [Multi-Tenancy trong Kiến trúc](#6--multi-tenancy-trong-kiến-trúc)
7. [Vòng đời File trong Domain](#7--vòng-đời-file-trong-domain)
8. [Những điều cần nhớ khi phát triển](#8--những-điều-cần-nhớ-khi-phát-triển)

---

## 1 — Tại sao dùng Hexagonal cho File Storage

### Hexagonal Architecture giải quyết vấn đề gì mà MVC phân lớp truyền thống không làm được?

Trong ứng dụng MVC phân lớp truyền thống, code được tổ chức theo **mối quan tâm kỹ thuật**: controller ở trên, service ở giữa, repository ở dưới. Mỗi lớp phụ thuộc vào lớp bên dưới, và — điều quan trọng nhất — business logic trong tầng service bị **gắn chặt** với database, message broker, hay công nghệ storage mà bạn đã chọn.

Hexagonal Architecture (còn gọi là Ports and Adapters) đảo ngược điều này. Nó đặt **business logic vào trung tâm** và biến tất cả hạ tầng — database, HTTP, message queue, object storage — thành các thành phần cắm vào trung tâm thông qua các interface được định nghĩa rõ ràng. Business logic không biết và không quan tâm đến việc nó đang nói chuyện với PostgreSQL, MongoDB, MinIO hay một mock trong test.

### Tại sao phù hợp với hệ thống File Storage này?

Hệ thống này giao tiếp với **nhiều công nghệ hạ tầng khác nhau**:

| Công nghệ | Mục đích |
|-----------|---------|
| PostgreSQL | Lưu metadata (files, tenants, users, audit logs) |
| MinIO | Object storage (byte thực của file, tương thích S3) |
| Redis | Cache metadata nóng và trạng thái multipart session |
| Elasticsearch | Tìm kiếm full-text trong metadata file |
| RabbitMQ | Publish event bất đồng bộ (file uploaded, deleted, purged) |
| Apache Tika | Phát hiện MIME type và phân tích nội dung |
| ShedLock | Điều phối scheduled job phân tán |

Nếu domain logic (upload file, kiểm tra quota tenant, dedup nội dung) bị gắn trực tiếp với bất kỳ thành phần nào trong số này, thì **đổi MinIO sang AWS S3**, hay **thay Redis bằng Memcached**, hay **chuyển từ RabbitMQ sang Kafka** đều sẽ yêu cầu viết lại business logic — phần không bao giờ nên thay đổi vì lý do hạ tầng.

Hexagonal Architecture đảm bảo: **domain không import một class hạ tầng nào**. Nó chỉ định nghĩa interface (port) nói lên *cần gì*, và adapter thực thi *làm thế nào*.

### Điều gì sẽ xảy ra nếu không có Hexagonal Architecture?

**❌ Cách tệ — business logic trong controller gọi JPA trực tiếp:**

```java
// TỆ: Business logic trộn lẫn vào @RestController
@RestController
@RequiredArgsConstructor
public class FileController {

    private final FileJpaRepository fileRepo;
    private final TenantJpaRepository tenantRepo;
    private final MinioClient minioClient;

    @PostMapping("/api/v1/files/upload")
    public ResponseEntity<?> upload(@RequestParam MultipartFile file,
                                    @RequestHeader("X-Tenant-Id") String tenantId) {
        // Business rule nhúng trong controller
        TenantJpaEntity tenant = tenantRepo.findById(tenantId).orElseThrow();
        if (tenant.getStorageUsedBytes() + file.getSize() > tenant.getMaxStorageBytes()) {
            throw new RuntimeException("Quota exceeded");
        }

        // Gọi hạ tầng không có abstraction
        minioClient.putObject(PutObjectArgs.builder()
            .bucket(tenantId)
            .object(UUID.randomUUID().toString())
            .stream(file.getInputStream(), file.getSize(), -1)
            .build());

        // JPA entity vừa là domain model vừa là response
        FileJpaEntity entity = new FileJpaEntity();
        entity.setTenantId(tenantId);
        entity.setOriginalName(file.getOriginalFilename());
        fileRepo.save(entity);

        return ResponseEntity.ok(entity); // JPA entity bị lộ ra HTTP response!
    }
}
```

**Vấn đề với cách này:**
1. **Không test được** — để unit test kiểm tra quota, bạn cần database thật và MinIO thật
2. **Không thể thay đổi** — đổi MinIO sang AWS S3 phải sửa controller
3. **Rò rỉ** — JPA entity (với annotation database) được trả về làm HTTP response
4. **Không tái sử dụng được** — nếu một RabbitMQ consumer cũng cần upload file, bạn phải copy logic này

**✅ Cách Hexagonal — cách project này làm:**

```
FileController (adapter.in.rest.controller)
  ↓ gọi interface
UploadFileUseCase (domain.port.in.file)  ← chỉ là Java interface
  ↓ được implement bởi
UploadFileService (application.usecase.file)  ← pure business logic
  ↓ gọi interface
ObjectStoragePort (domain.port.out)  ← chỉ là Java interface
  ↓ được implement bởi
MinioStorageAdapter (adapter.out.storage)  ← code MinIO-specific bị cô lập tại đây
```

Controller không biết về MinIO. Use case không biết về JPA entity. Domain model không biết về Spring. Mỗi phần đều có thể test, thay thế và hiểu độc lập.

---

## 2 — Ba Vùng (kèm mapping project thực tế)

Hexagonal Architecture chia code thành ba vùng đồng tâm. Hãy hình dung như củ hành: lớp trong cùng không biết gì về các lớp ngoài.

```
┌─────────────────────────────────────────────────────────────────┐
│                  VÙNG 3 — ADAPTERS (bên ngoài)                  │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │              VÙNG 2 — APPLICATION (ở giữa)                │  │
│  │  ┌─────────────────────────────────────────────────────┐  │  │
│  │  │              VÙNG 1 — DOMAIN (bên trong)            │  │  │
│  │  │                                                     │  │  │
│  │  │   model/  port/in/  port/out/  exception/           │  │  │
│  │  │                                                     │  │  │
│  │  │   Pure Java. Không Spring. Không JPA. Không annotation.│ │  │
│  │  └─────────────────────────────────────────────────────┘  │  │
│  │                                                           │  │
│  │   usecase/file/  usecase/tenant/  usecase/user/           │  │
│  │                                                           │  │
│  │   Orchestration. Chỉ dùng @Service + @Transactional.     │  │
│  └───────────────────────────────────────────────────────────┘  │
│                                                                 │
│  adapter/in/rest/   adapter/in/messaging/                       │
│  adapter/out/persistence/  adapter/out/storage/  ...            │
│                                                                 │
│  Toàn bộ code framework nằm ở đây.                              │
└─────────────────────────────────────────────────────────────────┘
```

### Vùng 1 — Domain (bên trong)

**Cái gì nằm ở đây:** Các class Java thuần không có dependency framework. Không `@Entity`, không `@Service`, không `@Autowired`. Chỉ là class Java, interface và enum thuần túy.

**Package:** `com.filestorage.domain`

| Sub-package | Nội dung | Ví dụ (class dự kiến) |
|-------------|---------|----------------------|
| `domain.model` | Domain entity và value object — các khái niệm business cốt lõi | `File.java`, `Tenant.java`, `User.java`, `FileContent.java`, `MultipartSession.java`, `AuditLog.java` |
| `domain.model.enums` | Enum domain biểu diễn tập giá trị bị ràng buộc | `FileStatus.java`, `Role.java`, `AuditAction.java` |
| `domain.port.in` | **Interface inbound port** — các thao tác mà thế giới bên ngoài có thể gọi | Tổ chức theo subdomain: `file/`, `tenant/`, `user/` |
| `domain.port.out` | **Interface outbound port** — khả năng hạ tầng mà domain cần | `FileRepository.java`, `ObjectStoragePort.java`, `EventPublisherPort.java`, `CachePort.java`, v.v. |
| `domain.exception` | Exception business có kiểu cụ thể mang ý nghĩa ngữ nghĩa | `TenantQuotaExceededException.java`, `FileNotFoundException.java`, `InvalidFileStateException.java` |

**Tại sao KHÔNG có dependency Spring?** Vì domain là code quan trọng nhất trong hệ thống — nó chứa các business rule biện hộ cho sự tồn tại của project. Bằng cách giữ cho nó không có dependency:

- Nó biên dịch và chạy mà không cần Spring Boot — bạn có thể unit test với JUnit thuần, không cần application context
- Nó không bao giờ bị lỗi do nâng cấp phiên bản Spring
- Nó có thể được trích xuất thành shared library nếu service khác cần cùng domain logic
- Developer có thể đọc và hiểu business rule mà không cần biết Spring

**Ví dụ — `File.java` là pure domain model:**

```java
// com.filestorage.domain.model.File
// Chú ý: KHÔNG có @Entity, @Table, @Column — chỉ là plain Java

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

    // Domain behavior — chuyển trạng thái với business rule enforcement
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

Domain object `File` **sở hữu các chuyển đổi trạng thái của nó**. Rule "chỉ có thể xóa file ACTIVE" nằm ngay tại đây — không bị phân tán qua controller hay database trigger.

### Vùng 2 — Application (use cases)

**Cái gì nằm ở đây:** Logic điều phối (orchestration). Mỗi use case class phối hợp một thao tác business bằng cách gọi các domain object và outbound port phù hợp. Use case thực thi business rule, sở hữu transaction boundary, và là nơi duy nhất `@Transactional` xuất hiện.

**Package:** `com.filestorage.application.usecase`

| Sub-package | Use Case Class | Điều phối gì |
|-------------|---------------|-------------|
| `usecase.file` | `UploadFileService`, `GetFileService`, `DeleteFileService`, `RestoreFileService`, `SearchFilesService`, `InitiateMultipartUploadService`, `CompleteMultipartUploadService`, `AbortMultipartUploadService` | Tất cả thao tác file |
| `usecase.tenant` | `CreateTenantService`, `GetTenantService`, `DeactivateTenantService` | Vòng đời tenant |
| `usecase.user` | `ProvisionUserService`, `GetUserService`, `DeactivateUserService` | Cấp phát user |

**Cấu trúc của một use case:**

```java
// com.filestorage.application.usecase.file.UploadFileService

@Service                                       // Spring quản lý như bean
@Transactional                                  // Sở hữu transaction boundary
public class UploadFileService implements UploadFileUseCase {  // implement inbound port

    // Constructor injection — tất cả dependency là OUTBOUND PORT INTERFACE
    private final TenantRepository tenantRepository;
    private final FileRepository fileRepository;
    private final FileContentRepository fileContentRepository;
    private final ObjectStoragePort objectStoragePort;
    private final TikaAnalyzerPort tikaAnalyzerPort;
    private final EventPublisherPort eventPublisherPort;
    private final AuditLogPort auditLogPort;

    public UploadFileService(TenantRepository tenantRepository,
                             FileRepository fileRepository,
                             /* ... tất cả port được inject ... */) {
        this.tenantRepository = tenantRepository;
        // ...
    }

    @Override
    public File execute(UploadFileCommand command) {
    }
}
```

Điểm quan trọng: `UploadFileService` không import một class adapter nào. Nó chỉ biết về interface được định nghĩa trong `domain.port.out`. Spring's dependency injection sẽ wire adapter thực tế vào lúc runtime.

### Vùng 3 — Adapters (bên ngoài)

**Cái gì nằm ở đây:** Toàn bộ code framework-specific, infrastructure-specific. Đây là nơi Spring MVC, JPA, MinIO SDK, RabbitMQ, Redis và Elasticsearch sinh sống. Adapter là **vỏ ngoài có thể thay thế** của ứng dụng.

**Package:** `com.filestorage.adapter.in.*` và `com.filestorage.adapter.out.*`

#### Bản đồ Adapter đầy đủ

| Adapter | Chiều | Package | Công nghệ | Port nó implement/gọi |
|---------|-------|---------|-----------|----------------------|
| **REST API** | Inbound | `adapter.in.rest` | Spring MVC | Gọi `UploadFileUseCase`, `GetFileUseCase`, `DeleteFileUseCase`, `SearchFilesUseCase`, `CreateTenantUseCase`, v.v. |
| **RabbitMQ consumer** | Inbound | `adapter.in.messaging` | Spring AMQP | Gọi inbound use case port (ví dụ: index tìm kiếm khi nhận file event) |
| **PostgreSQL** | Outbound | `adapter.out.persistence` | Spring Data JPA, Flyway | Implement `FileRepository`, `TenantRepository`, `UserRepository`, `FileContentRepository`, `MultipartSessionRepository`, `AuditLogPort` |
| **MinIO** | Outbound | `adapter.out.storage` | MinIO Java SDK | Implement `ObjectStoragePort` |
| **Elasticsearch** | Outbound | `adapter.out.search` | ES Java Client 8.x | Implement `FileSearchPort` |
| **Redis** | Outbound | `adapter.out.cache` | Spring Data Redis | Implement `CachePort` |
| **RabbitMQ publisher** | Outbound | `adapter.out.messaging` | Spring AMQP | Implement `EventPublisherPort` |
| **Scheduled jobs** | Outbound | `adapter.out.scheduling` | ShedLock + Spring Scheduling | Gọi inbound port (`DeleteFileUseCase`, v.v.) — không bao giờ gọi repository trực tiếp |
| **Apache Tika** | Outbound | `adapter.out.analysis` | Apache Tika, PDFBox, POI | Implement `TikaAnalyzerPort` |
| **Micrometer Metrics** | Outbound | `adapter.out.monitoring` | Micrometer, Prometheus | Expose storage metrics (upload histogram, tỷ lệ dedup) |

#### Sub-package của REST Adapter

| Sub-package | Mục đích | Class chính |
|-------------|---------|------------|
| `adapter.in.rest.controller` | HTTP endpoint, delegate đến inbound port | `FileController`, `TenantController`, `UserController`, `MultipartUploadController` |
| `adapter.in.rest.dto.request` | JSON request với Bean Validation | `UploadFileRequest`, `CreateTenantRequest`, `ProvisionUserRequest` |
| `adapter.in.rest.dto.response` | JSON response (không bao giờ là domain model) | `FileResponse`, `TenantResponse`, `ProblemDetailResponse` |
| `adapter.in.rest.mapper` | MapStruct mapper tại REST boundary | `FileRestMapper`, `TenantRestMapper`, `UserRestMapper` |
| `adapter.in.rest.security` | JWT filter và context | `JwtAuthFilter`, `JwtTokenValidator`, `TenantAwareAuthContext` |
| `adapter.in.rest.advice` | Mapping exception toàn cục → HTTP error | `GlobalExceptionHandler` |

#### Sub-package của Persistence Adapter

| Sub-package | Mục đích | Class chính |
|-------------|---------|------------|
| `adapter.out.persistence.entity` | JPA `@Entity` class (biểu diễn database) | `FileJpaEntity`, `TenantJpaEntity`, `AuditLogJpaEntity` |
| `adapter.out.persistence.repository` | Spring Data JPA `@Repository` interface | `FileJpaRepository`, `TenantJpaRepository` |
| `adapter.out.persistence.mapper` | MapStruct mapper tại persistence boundary | `FilePersistenceMapper`, `TenantPersistenceMapper` |
| `adapter.out.persistence.adapter` | Class adapter implement domain port interface | `FileRepositoryAdapter`, `TenantRepositoryAdapter`, `AuditLogAdapter` |

---

## 3 — Ports là gì (kèm interface thực tế)

### Port là gì?

**Port** là Java interface định nghĩa ranh giới giữa domain và thế giới bên ngoài. Port tồn tại để domain không bao giờ phụ thuộc trực tiếp vào code hạ tầng.

Có hai loại:

### Inbound Port (`domain.port.in`)

**Là gì:** Interface định nghĩa các thao tác mà thế giới bên ngoài có thể yêu cầu domain thực hiện. Hãy xem chúng như public API của domain.

**Ai implement:** Class use case trong `application.usecase`.
**Ai gọi:** Inbound adapter (controller, message consumer, scheduler).

```java
// com.filestorage.domain.port.in.file.UploadFileUseCase
public interface UploadFileUseCase {
    File execute(UploadFileCommand command);
}

// Command object chứa tất cả dữ liệu đầu vào — kể cả tenantId
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

**Tại sao controller phụ thuộc vào INTERFACE, không phải implementation:**

```java
// FileController phụ thuộc vào UploadFileUseCase (interface), KHÔNG PHẢI UploadFileService (class)
@RestController
public class FileController {
    private final UploadFileUseCase uploadFileUseCase;  // ← kiểu interface

    public FileController(UploadFileUseCase uploadFileUseCase) {
        this.uploadFileUseCase = uploadFileUseCase;
    }
}
```

Điều này có nghĩa bạn có thể thay `UploadFileService` bằng `MockUploadFileService` trong test mà không cần đụng vào controller. Controller không biết — và không cần biết — class nào implement interface đó.

### Outbound Port (`domain.port.out`)

**Là gì:** Interface định nghĩa những gì domain **cần từ thế giới bên ngoài**. Domain nói "tôi cần lưu một file" — nó không nói "tôi cần PostgreSQL."

**Ai định nghĩa:** Tầng domain.
**Ai implement:** Outbound adapter.

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

**Tại sao domain định nghĩa interface này — Dependency Inversion:**

Trong kiến trúc phân lớp truyền thống, tầng service phụ thuộc vào tầng repository: `Service → Repository → Database`. Mũi tên dependency trỏ xuống dưới.

Trong Hexagonal Architecture, domain định nghĩa interface và adapter implement nó: `Adapter → (implement) → Port ← (dùng) ← Use Case`. Mũi tên dependency trỏ **vào trong**, hướng về domain.

Đây là **Dependency Inversion Principle** (chữ D trong SOLID): module bậc cao (domain) không nên phụ thuộc vào module bậc thấp (database adapter). Cả hai nên phụ thuộc vào abstraction (port).

### Quy tắc Dependency — Minh họa trực quan

```
                    MŨI TÊN DEPENDENCY TRỎ VÀO TRONG
                    ==================================

  HTTP Request
       │
       ▼
  ┌─────────────────┐         ┌──────────────────┐
  │  FileController  │         │ JwtAuthFilter    │
  │  (REST Adapter)  │         │ (Security)       │
  └────────┬────────┘         └──────────────────┘
           │  phụ thuộc vào
           ▼
  ┌─────────────────────────┐
  │  UploadFileUseCase      │  ← Inbound Port (interface trong domain)
  └────────┬────────────────┘
           │  được implement bởi
           ▼
  ┌─────────────────────────┐
  │  UploadFileService      │  ← Application Use Case
  │  (application.usecase)  │
  └────────┬────────────────┘
           │  phụ thuộc vào
           ▼
  ┌─────────────────────────┐
  │  FileRepository         │  ← Outbound Port (interface trong domain)
  │  ObjectStoragePort      │
  │  EventPublisherPort     │
  └────────┬────────────────┘
           │  được implement bởi
           ▼
  ┌─────────────────────────┐         ┌──────────────────┐
  │  FileRepositoryAdapter  │         │  MinioStorage     │
  │  (JPA Adapter)          │         │  Adapter          │
  └────────┬────────────────┘         └────────┬─────────┘
           │                                    │
           ▼                                    ▼
      PostgreSQL                             MinIO
```

**Quy tắc:** Adapter phụ thuộc vào domain. Domain **không bao giờ** phụ thuộc vào adapter. Nếu bạn thấy câu lệnh `import com.filestorage.adapter.*` trong bất kỳ file nào dưới `com.filestorage.domain`, đó là vi phạm kiến trúc — và `HexagonalArchitectureTest.java` (ArchUnit) đã lên kế hoạch sẽ bắt lỗi này tự động.

---

## 4 — Luồng dữ liệu: Upload File (walkthrough end-to-end)

Đây là những gì xảy ra khi user upload file, từng bước một. Mỗi bước: class nào, package nào, làm gì, và port boundary nào bị vượt qua.

```
Bước   Class                          Package                              Hành động                                             Port vượt qua
─────  ─────────────────────────────  ───────────────────────────────────  ────────────────────────────────────────────────────  ──────────────────────────
 1     JwtAuthFilter                  adapter.in.rest.security             Chặn HTTP request, validate JWT signature              —
                                                                           qua JwtTokenValidator, trích xuất tenantId + userId
                                                                           + role claims, đặt vào SecurityContext với
                                                                           TenantAwareAuthContext

 2     FileController                 adapter.in.rest.controller           Nhận POST /api/v1/files/upload với                     —
                                                                           MultipartFile. Đọc TenantAwareAuthContext
                                                                           từ SecurityContext để lấy tenantId và userId.

 3     FileRestMapper                 adapter.in.rest.mapper               MapStruct mapper chuyển MultipartFile +                REST ↔ Domain boundary
                                                                           auth context thành UploadFileCommand
                                                                           (domain command object)

 4     UploadFileService              application.usecase.file             Use case được gọi qua UploadFileUseCase                UploadFileUseCase
                                                                           interface. @Transactional bắt đầu.                     (inbound port)

 5     TenantRepository               domain.port.out                      Use case gọi tenantRepository.findById                 TenantRepository
       → TenantRepositoryAdapter      adapter.out.persistence.adapter      (tenantId). Adapter query PostgreSQL,                  (outbound port)
                                                                           chuyển TenantJpaEntity → Tenant domain model.
                                                                           Use case kiểm tra: tenant có active không? Quota OK?

 6     TikaAnalyzerPort               domain.port.out                      Use case gọi tikaAnalyzerPort                         TikaAnalyzerPort
       → TikaAnalyzerAdapter          adapter.out.analysis                 .detectMimeType(bytes). Adapter dùng                   (outbound port)
                                                                           Apache Tika để phát hiện MIME type thực tế
                                                                           (bỏ qua Content-Type client cung cấp vì bảo mật).

 7     FileContentRepository          domain.port.out                      Use case tính SHA-256 hash của byte file.              FileContentRepository
       → FileContentRepoAdapter       adapter.out.persistence.adapter      Gọi fileContentRepository                              (outbound port)
                                                                           .findByHashAndTenantId(hash, tenantId).
                                                                           Nếu tìm thấy → dedup hit: dùng lại storageKey,
                                                                           tăng refCount. Nếu không → nội dung mới.

 8     ObjectStoragePort              domain.port.out                      [Chỉ nếu nội dung mới] Use case gọi                   ObjectStoragePort
       → MinioStorageAdapter          adapter.out.storage                  objectStoragePort.upload(tenantId, key, bytes).        (outbound port)
                                                                           Adapter gọi MinIO SDK putObject().

 9     FileRepository                 domain.port.out                      Use case tạo File domain object                        FileRepository
       → FileRepositoryAdapter        adapter.out.persistence.adapter      với status=ACTIVE, gọi fileRepository.save().          (outbound port)
                                                                           Adapter chuyển File → FileJpaEntity qua
                                                                           FilePersistenceMapper, lưu qua JPA.

10     EventPublisherPort             domain.port.out                      Use case gọi eventPublisherPort                       EventPublisherPort
       → FileEventPublisher           adapter.out.messaging                .publishFileUploaded(tenantId, fileId, userId).       (outbound port)
                                                                           Adapter wrap dữ liệu vào FileUploadedEvent,
                                                                           publish lên RabbitMQ exchange `file.events`
                                                                           với routing key `file.uploaded`.

11     AuditLogPort                   domain.port.out                      Use case tạo AuditLog domain object                   AuditLogPort
       → AuditLogAdapter              adapter.out.persistence.adapter      (action=FILE_UPLOADED, tenantId, userId, fileId),     (outbound port)
                                                                           gọi auditLogPort.record(). Adapter chuyển
                                                                           thành AuditLogJpaEntity, lưu vào PostgreSQL.

12     FileRestMapper                 adapter.in.rest.mapper               Quay lại FileController: MapStruct mapper             Domain ↔ REST boundary
                                                                           chuyển File domain model → FileResponse DTO.
                                                                           Domain model KHÔNG BAO GIỜ được trả về là HTTP response.

13     FileController                 adapter.in.rest.controller           Trả về ResponseEntity.status(201)                     —
                                                                           .body(fileResponse). HTTP 201 Created.
```

**Sau HTTP response (bất đồng bộ):**

`FileUploadedEvent` được publish lên RabbitMQ được `FileEventConsumer` (`adapter.in.messaging`) consume, kích hoạt việc index Elasticsearch qua `FileSearchPort` → `ElasticsearchSearchAdapter`. Điều này xảy ra ngoài request/response HTTP ban đầu.

---

## 5 — MapStruct: Bộ chuyển đổi tại Boundary

### Tại sao MapStruct được dùng tại MỌI adapter boundary

Một quy tắc cốt lõi của Hexagonal Architecture: **domain model không bao giờ bị rò rỉ ra khỏi domain**. `File.java` của domain không phải JPA entity. Không phải JSON response. Nó là pure business object. Mỗi khi dữ liệu vượt qua ranh giới (HTTP ↔ Domain, Domain ↔ Database), nó phải được chuyển đổi.

MapStruct tạo code chuyển đổi này tại compile time — không có reflection, không có runtime cost, type-safe tại compile time.

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

**Điều này ngăn chặn:**
```java
// ❌ SAI: trả về domain model trực tiếp
return ResponseEntity.ok(file);  // Lộ cấu trúc domain nội bộ cho API consumer

// ✅ ĐÚNG: map sang DTO tại boundary
return ResponseEntity.ok(fileRestMapper.toResponse(file));
```

### Persistence Boundary Mapping

```java
// com.filestorage.adapter.out.persistence.mapper.FilePersistenceMapper
@Mapper(componentModel = "spring")
public interface FilePersistenceMapper {

    // Domain → JPA entity (để lưu)
    FileJpaEntity toEntity(File file);

    // JPA entity → Domain (để đọc)
    File toDomain(FileJpaEntity entity);

    List<File> toDomainList(List<FileJpaEntity> entities);
}
```

**Tại sao sự tách biệt này quan trọng:** `FileJpaEntity` có `@Entity`, `@Table`, `@Column`, `@ManyToOne` — tất cả annotation JPA-specific. Domain `File` không có gì cả. Nếu bạn truyền JPA entity vào use case, domain sẽ phải biết về JPA. Nếu bạn trả domain model từ controller, API contract sẽ bị phá vỡ mỗi khi bạn refactor domain. MapStruct giữ cả hai thế giới được tách biệt rõ ràng.

### Các persistence mapper khác đều theo cùng pattern:

| Mapper | Package | Chuyển đổi giữa |
|--------|---------|----------------|
| `TenantPersistenceMapper` | `adapter.out.persistence.mapper` | `Tenant` ↔ `TenantJpaEntity` |
| `UserPersistenceMapper` | `adapter.out.persistence.mapper` | `User` ↔ `UserJpaEntity` |
| `FileContentPersistenceMapper` | `adapter.out.persistence.mapper` | `FileContent` ↔ `FileContentJpaEntity` |
| `MultipartSessionPersistenceMapper` | `adapter.out.persistence.mapper` | `MultipartSession` ↔ `MultipartSessionJpaEntity` |
| `AuditLogPersistenceMapper` | `adapter.out.persistence.mapper` | `AuditLog` ↔ `AuditLogJpaEntity` |

---

## 6 — Multi-Tenancy trong Kiến trúc

Hệ thống này phục vụ **nhiều tổ chức (tenant)** từ một deployment duy nhất. Tenant isolation được thực thi ở mọi lớp — không chỉ database.

### Cách tenantId chảy qua toàn bộ stack

```
JWT Token (được cấp bởi Auth0)
  │  chứa claim: "tenantId": "tenant_abc123"
  │
  ▼
JwtAuthFilter (adapter.in.rest.security)
  │  Trích xuất tenantId từ JWT claims
  │  Tạo TenantAwareAuthContext(tenantId, userId, role)
  │  Đặt vào SecurityContext
  │
  ▼
FileController (adapter.in.rest.controller)
  │  Đọc tenantId từ SecurityContext
  │  Truyền vào UploadFileCommand(tenantId, ...)
  │
  ▼
UploadFileService (application.usecase.file)
  │  Nhận tenantId bên trong command object
  │  Truyền tenantId vào MỌI lời gọi outbound port
  │
  ▼
FileRepository.findByIdAndTenantId(fileId, tenantId)
ObjectStoragePort.upload(tenantId, key, bytes)
EventPublisherPort.publishFileUploaded(tenantId, fileId, ...)
```

### Các đảm bảo tenant isolation quan trọng

1. **Mọi method outbound port đều nhận `tenantId` làm tham số.** Không có `TenantContext.getCurrentTenant()` toàn cục được lưu trong ThreadLocal. tenantId được truyền tường minh, khiến việc vô tình quên nó là không thể.

2. **Mọi JPA query đều lọc theo `tenant_id`.** Method là `findByIdAndTenantId`, không phải `findById`. Dù user có cố tạo request với file ID của tenant khác, query vẫn trả về rỗng — truy cập dữ liệu cross-tenant là không thể về mặt cấu trúc.

3. **Đối tượng MinIO được namespace theo tenant.** File của mỗi tenant được lưu dưới prefix hoặc bucket đặc thù cho tenant: `objectStoragePort.upload(tenantId, storageKey, bytes)`. Adapter dùng `tenantId` làm tên bucket hoặc key prefix.

4. **Dedup (FileContent) chỉ trong phạm vi tenant.** Lookup dedup SHA-256 là: `fileContentRepository.findByHashAndTenantId(hash, tenantId)`. Hai tenant upload file giống hệt nhau nhận `FileContent` entry và đối tượng MinIO riêng biệt. Đây là cố ý: cross-tenant dedup sẽ tạo dependency dữ liệu giữa các tenant và rò rỉ thông tin (tenant A có thể suy ra tenant B có cùng file).

5. **Audit log ghi `tenantId`.** Mọi audit entry đều có tenant, đảm bảo báo cáo compliance được scoped theo tenant.

### Tại sao không dùng ThreadLocal-based tenant context?

Một số hệ thống multi-tenant dùng `ThreadLocal<String>` để lưu tenant ID hiện tại toàn cục. Điều này nguy hiểm vì:
- Thao tác async (RabbitMQ consumer, scheduled task) chạy trên thread khác — ThreadLocal rỗng
- Nếu developer quên đặt nó, query chạy mà không có tenant filter — rò rỉ dữ liệu
- Nó ngầm định và vô hình — khó debug, khó test

Project này dùng **explicit parameter passing**: tenantId luôn hiển thị trong chữ ký method. Nếu bạn quên truyền nó, code sẽ không biên dịch được.

---

## 7 — Vòng đời File trong Domain

### State Machine

File chuyển đổi qua các trạng thái này như một khái niệm domain. Điều này được thực thi bởi bản thân domain model, không phải database trigger.

```
                         upload thành công
    ┌──────────┐       ┌──────────────────┐
    │ PENDING  │──────▶│     ACTIVE       │
    └──────────┘       └──────────────────┘
         │                    │         ▲
         │ upload thất bại    │ soft    │ restore
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

### State machine được thực thi ở đâu?

Trong **domain model** (`domain.model.File`), không phải database trigger hay service code. Object `File` có các method như `markPendingDelete()`, `restore()` và `markDeleted()` kiểm tra điều kiện tiên quyết và ném `InvalidFileStateException` nếu chuyển đổi không hợp lệ.

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

### Use case nào điều khiển chuyển đổi nào?

| Chuyển đổi | Use Case | Kích hoạt bởi |
|-----------|----------|--------------|
| `PENDING → ACTIVE` | `UploadFileService` | Upload hoàn tất thành công |
| `PENDING → UPLOAD_FAILED` | `UploadFileService` | Exception trong quá trình upload (lỗi MinIO, v.v.) |
| `ACTIVE → PENDING_DELETE` | `DeleteFileService` | User yêu cầu xóa file (soft delete) |
| `PENDING_DELETE → ACTIVE` | `RestoreFileService` | User khôi phục file trong cửa sổ retention |
| `PENDING_DELETE → DELETED` | `FilePurgeScheduler` | Scheduled job chạy sau khi retention period hết hạn |

### Cách ShedLock xử lý purge PENDING_DELETE → DELETED

`FilePurgeScheduler` (`adapter.out.scheduling`) chạy như một `@Scheduled` cron job (ví dụ: hàng ngày lúc 02:00). Trong Kubernetes deployment với nhiều pod, mỗi pod có scheduler riêng. Không có điều phối, tất cả pod sẽ cố purge cùng file đồng thời.

**ShedLock** ngăn điều này bằng cách acquire distributed lock trong bảng `shedlock` PostgreSQL trước khi thực thi. Chỉ một pod lấy được lock; các pod khác bỏ qua. `lockAtMostFor` ngăn pod bị crash giữ lock vĩnh viễn.

Luồng của scheduler:
1. Acquire ShedLock → chỉ một pod tiếp tục
2. Gọi `FileRepository.findByTenantIdAndStatus(*, PENDING_DELETE)` — tìm tất cả file có thể purge
3. Với mỗi file: gọi `file.markDeleted()` (chuyển đổi trạng thái domain)
4. Xóa đối tượng MinIO qua `ObjectStoragePort.delete()`
5. Giảm `FileContent.refCount` — nếu về 0, content blob trở thành orphan
6. Cập nhật `storage_used_bytes` của tenant qua `TenantRepository`
7. Publish `FilePurgedEvent` qua `EventPublisherPort`
8. Ghi audit log qua `AuditLogPort`

### Tại sao DELETED là soft delete

Row metadata file được **giữ trong PostgreSQL** với `status = DELETED`. Byte thực tế được xóa khỏi MinIO, nhưng metadata (ai upload, khi nào, kích thước bao nhiêu, ai xóa) vẫn còn. Đây là để **tuân thủ audit**: bạn cần trả lời câu hỏi như "ai xóa file X và khi nào?" từ lâu sau khi file đó đã mất.

Bảng `AuditLog` cung cấp lịch sử bất biến riêng biệt — nhưng giữ row metadata file cho bạn record có thể query mà không cần join với audit log.

---

## 8 — Những điều cần nhớ khi phát triển

Đây là 10 quy tắc cần tuân theo khi thêm tính năng mới vào codebase này.

---

### Quy tắc 1: Thêm Use Case mới ở đâu

✅ **LÀM:** Tạo interface mới trong `domain.port.in/{subdomain}/` và class mới trong `application.usecase/{subdomain}/`.

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

❌ **KHÔNG LÀM:** Thêm method vào use case interface đã có. Mỗi use case là một thao tác duy nhất.

**TẠI SAO:** Single Responsibility. Mỗi interface một thao tác nghĩa là controller chỉ phụ thuộc vào thứ nó thực sự dùng.

---

### Quy tắc 2: Thêm repository method mới ở đâu

✅ **LÀM:** Thêm chữ ký method vào outbound port interface trong `domain.port.out/`, sau đó implement nó trong adapter tại `adapter.out.persistence.adapter/`.

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

❌ **KHÔNG LÀM:** Chỉ thêm method vào `FileJpaRepository` và gọi trực tiếp từ use case.

**TẠI SAO:** Use case phải phụ thuộc vào domain port interface, không phải JPA repository. Nếu không bạn đã couple business logic với JPA.

---

### Quy tắc 3: Thêm REST endpoint mới ở đâu

✅ **LÀM:** Thêm endpoint vào controller phù hợp trong `adapter.in.rest.controller/`. Tạo request/response DTO mới trong `adapter.in.rest.dto/`. Thêm mapper method vào mapper tương ứng trong `adapter.in.rest.mapper/`. Controller gọi inbound port, không bao giờ gọi use case class trực tiếp.

❌ **KHÔNG LÀM:** Đặt business logic vào controller method. Nhiệm vụ của controller là: trích xuất input → gọi port → trả về response.

**TẠI SAO:** Controller là adapter. Chúng dịch HTTP thành domain operation. Business rule sống trong use case.

---

### Quy tắc 4: Không đặt business logic ở đâu

✅ **LÀM:** Đặt tất cả business rule (validation, chuyển đổi trạng thái, tính toán) trong domain model method hoặc use case class.

❌ **KHÔNG LÀM:** Đặt business logic trong:
```java
// ❌ Trong controller
if (file.getSize() > tenant.getMaxFileSize()) { throw ... }

// ❌ Trong JPA repository adapter
// ❌ Trong MapStruct mapper
// ❌ Trong @Configuration class
// ❌ Trong @Scheduled method (gọi use case interface thay vào đó)
```

**TẠI SAO:** Business rule trong adapter vô hình với unit test, rải rác qua codebase, và bị duplicate khi nhiều adapter cần cùng logic.

---

### Quy tắc 5: Khi nào tạo Port mới vs dùng lại cái cũ

✅ **LÀM:** Tạo outbound port mới khi bạn đang tích hợp khả năng hạ tầng thực sự mới (ví dụ: `NotificationPort` cho email/SMS, `VirusScanPort` cho ClamAV).

❌ **KHÔNG LÀM:** Tạo port mới cho mọi biến thể hạ tầng nhỏ. Nếu Redis đã được dùng qua `CachePort`, đừng tạo `SessionCachePort` — mở rộng `CachePort` hoặc thêm method vào nó nếu khái niệm là như nhau.

**TẠI SAO:** Port đại diện cho *khả năng* hạ tầng riêng biệt, không phải *implementation* hạ tầng riêng biệt.

---

### Quy tắc 6: Cách thêm scheduled task mới

✅ **LÀM:** Tạo class scheduler mới trong `adapter.out.scheduling/`. Annotate với `@Scheduled` và `@SchedulerLock`. Inject **inbound port interface** và gọi nó.

```java
@Component
public class OrphanContentCleanupScheduler {
    private final CleanupOrphanContentUseCase cleanupUseCase;

    @Scheduled(cron = "0 0 3 * * *")
    @SchedulerLock(name = "orphan-content-cleanup", lockAtMostFor = "PT30M")
    public void execute() {
        cleanupUseCase.execute();  // gọi inbound port
    }
}
```

❌ **KHÔNG LÀM:** Inject repository hoặc adapter trực tiếp vào scheduler. Scheduler là inbound adapter — chúng drive use case, giống như controller vậy.

**TẠI SAO:** Business logic được kích hoạt bởi schedule so với HTTP request nên đi theo cùng một đường qua cùng một use case.

---

### Quy tắc 7: Cách thêm RabbitMQ event mới

✅ **LÀM:** (1) Thêm method vào `EventPublisherPort` trong `domain.port.out/`. (2) Tạo event class trong `adapter.out.messaging.event/`. (3) Implement method trong `FileEventPublisher` (`adapter.out.messaging/`). (4) Nếu có consumer, thêm handler trong `adapter.in.messaging/`.

❌ **KHÔNG LÀM:** Publish RabbitMQ message trực tiếp từ use case dùng `RabbitTemplate`.

**TẠI SAO:** Use case gọi `eventPublisherPort.publishSomething()`. Nó không biết đó là RabbitMQ. Bạn có thể chuyển sang Kafka bằng cách viết adapter mới.

---

### Quy tắc 8: Không bao giờ import adapter class vào domain

✅ **LÀM:** Giữ import trong `domain/` sạch. Chỉ `java.*` và các package `domain.*` khác.

❌ **KHÔNG LÀM:**
```java
// ❌ KHÔNG BAO GIỜ trong bất kỳ file nào dưới com.filestorage.domain
import com.filestorage.adapter.out.persistence.entity.FileJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import io.minio.MinioClient;
```

**TẠI SAO:** Đây là quy tắc nền tảng của Hexagonal Architecture. `HexagonalArchitectureTest` (ArchUnit) trong test suite thực thi điều này tại compile time.

---

### Quy tắc 9: Luôn dùng MapStruct, không bao giờ map thủ công

✅ **LÀM:** Định nghĩa tất cả conversion trong MapStruct `@Mapper` interface. Để MapStruct tạo code.

❌ **KHÔNG LÀM:**
```java
// ❌ Map thủ công
FileResponse response = new FileResponse();
response.setId(file.getId());
response.setName(file.getOriginalName());
response.setSize(file.getSizeBytes());
// ... 10 field nữa thủ công
```

**TẠI SAO:** Map thủ công dễ xảy ra lỗi (bỏ sót field, copy nhầm), dài dòng và không được validate tại compile time. MapStruct bắt mapping thiếu sót như warning/error lúc biên dịch.

---

### Quy tắc 10: Luôn truyền tenantId, không bao giờ dựa vào global state

✅ **LÀM:** Bao gồm `tenantId` như tham số tường minh trong mọi command, query, port method và repository call.

```java
// ✅ Tường minh
File execute(UploadFileCommand command);  // command chứa tenantId
fileRepository.findByIdAndTenantId(fileId, tenantId);
objectStoragePort.upload(tenantId, key, bytes);
```

❌ **KHÔNG LÀM:**
```java
// ❌ Global ThreadLocal
String tenantId = TenantContext.getCurrentTenant();
fileRepository.findById(fileId);  // Không có tenant filter = nguy cơ rò rỉ dữ liệu
```

**TẠI SAO:** ThreadLocal tenant context bị lỗi trong async context (RabbitMQ consumer, scheduled task, method `@Async`) và âm thầm trả về dữ liệu cross-tenant khi bị quên. Tham số tường minh khiến tenant isolation không thể bị bỏ quên — nếu bạn bỏ qua nó, code sẽ không biên dịch được.

---

## Phụ lục — Cây thư mục Project (tham khảo đầy đủ)

Để tham khảo nhanh, đây là cấu trúc project dự kiến đầy đủ. File đánh dấu `[scaffolded]` tồn tại như directory placeholder (`.gitkeep` / `.txt`). File đánh dấu `[implemented]` chứa production code.

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
│   │   │   ├── file/     (8 use case interface)         [scaffolded]
│   │   │   ├── tenant/   (3 use case interface)         [scaffolded]
│   │   │   └── user/     (3 use case interface)         [scaffolded]
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
│       ├── TenantNotFoundException.java                  [scaffolded]
│       ├── TenantQuotaExceededException.java            [scaffolded]
│       └── ... (8 exception class nữa)                  [scaffolded]
│
├── application/
│   └── usecase/
│       ├── file/     (8 service class)                  [scaffolded]
│       ├── tenant/   (3 service class)                  [scaffolded]
│       └── user/     (3 service class)                  [scaffolded]
│
├── adapter/
│   ├── in/
│   │   ├── rest/
│   │   │   ├── controller/   (4 controller)             [scaffolded]
│   │   │   ├── dto/request/  (5 request DTO)            [scaffolded]
│   │   │   ├── dto/response/ (7 response DTO)           [scaffolded]
│   │   │   ├── mapper/       (3 REST mapper)            [scaffolded]
│   │   │   ├── security/     (3 security class)         [scaffolded]
│   │   │   └── advice/       (GlobalExceptionHandler)   [scaffolded]
│   │   └── messaging/
│   │       └── FileEventConsumer.java                   [scaffolded]
│   └── out/
│       ├── persistence/
│       │   ├── entity/       (6 JPA entity)             [scaffolded]
│       │   ├── repository/   (6 JPA repository)         [scaffolded]
│       │   ├── mapper/       (6 persistence mapper)     [scaffolded]
│       │   └── adapter/      (6 adapter class)          [scaffolded]
│       ├── storage/          (MinIO adapter)            [scaffolded]
│       ├── search/           (Elasticsearch adapter)    [scaffolded]
│       ├── cache/            (Redis adapter)            [scaffolded]
│       ├── messaging/        (RabbitMQ publisher)       [scaffolded]
│       ├── scheduling/       (3 scheduled job)          [scaffolded]
│       ├── analysis/         (Tika adapter)             [scaffolded]
│       └── monitoring/       (Micrometer metrics)       [scaffolded]
│
└── infrastructure/
    └── config/               (class @Configuration)     [scaffolded]
```

> [!TIP]
> Kiến trúc được thực thi tại compile time bởi `HexagonalArchitectureTest.java` trong test suite (`src/test/java/com/filestorage/architecture/`). File này dùng ArchUnit để xác minh rằng domain package không bao giờ import adapter package. Chạy nó như một phần của test suite bình thường — bất kỳ vi phạm kiến trúc nào cũng sẽ làm fail build.
