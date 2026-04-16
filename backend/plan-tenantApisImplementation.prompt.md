## Plan: Tenant APIs Hexagonal (Backend)

Triển khai 5 Tenant APIs theo spec với kiến trúc hexagonal đang dùng ở `User`: controller chỉ điều phối, nghiệp vụ nằm trong use case services, persistence đi qua repository hiện có. Kế hoạch ưu tiên khóa authorization `SUPER_ADMIN`, rule slug/quota/system-tenant, và chuẩn hóa response + error code để review nhanh, hạn chế regression.

### Checklist triển khai
- [ ] Chốt contract API từ `docs/user-tenant-folder-api-specification.md` và map base path `/api/v1/tenants`.
- [ ] Tạo inbound ports tenant trong `domain/port/in/tenant`.
- [ ] Tạo domain view + mapper persistence cho tenant.
- [ ] Implement application services + policy kiểm quyền/rule nghiệp vụ.
- [ ] Tạo REST adapter (controller/dto/mapper) + bổ sung error/i18n + test.

### Steps
1. [ ] Chốt contract API từ `docs/user-tenant-folder-api-specification.md` và map sang base path `/api/v1/tenants` trong `TenantController`.
2. [ ] Tạo inbound ports `CreateTenantUseCase`, `ListTenantsUseCase`, `GetTenantUseCase`, `UpdateTenantUseCase`, `DeactivateTenantUseCase` tại `src/main/java/com/filestorage/domain/port/in/tenant`.
3. [ ] Tạo domain view + mapper persistence: `src/main/java/com/filestorage/adapter/out/persistence/mapper/TenantViewMapper.java`, `TenantView`/`TenantPageView` ở `src/main/java/com/filestorage/domain/model/tenant`.
4. [ ] Implement application services + policy kiểm quyền/rule nghiệp vụ trong `src/main/java/com/filestorage/application/usecase/tenant`, tận dụng `src/main/java/com/filestorage/adapter/out/persistence/repository/TenantJpaRepository.java`.
5. [ ] Tạo REST adapter (controller/dto/mapper) và bổ sung error/i18n/test tại `src/main/java/com/filestorage/adapter/in/rest`, `src/main/java/com/filestorage/shared/exception/ErrorCode.java`, `src/main/resources/messages.properties`, `src/test/java`.

### File cần tạo/sửa
1. Tạo mới: `src/main/java/com/filestorage/adapter/in/rest/controller/TenantController.java`, `src/main/java/com/filestorage/adapter/in/rest/mapper/TenantRestMapper.java`, `src/main/java/com/filestorage/adapter/in/rest/dto/request/CreateTenantRequest.java`, `src/main/java/com/filestorage/adapter/in/rest/dto/request/UpdateTenantRequest.java`, `src/main/java/com/filestorage/adapter/in/rest/dto/response/TenantResponse.java`.
2. Tạo mới: `src/main/java/com/filestorage/domain/port/in/tenant/CreateTenantUseCase.java`, `src/main/java/com/filestorage/domain/port/in/tenant/ListTenantsUseCase.java`, `src/main/java/com/filestorage/domain/port/in/tenant/GetTenantUseCase.java`, `src/main/java/com/filestorage/domain/port/in/tenant/UpdateTenantUseCase.java`, `src/main/java/com/filestorage/domain/port/in/tenant/DeactivateTenantUseCase.java`.
3. Tạo mới: `src/main/java/com/filestorage/application/usecase/tenant/CreateTenantService.java`, `src/main/java/com/filestorage/application/usecase/tenant/ListTenantsService.java`, `src/main/java/com/filestorage/application/usecase/tenant/GetTenantService.java`, `src/main/java/com/filestorage/application/usecase/tenant/UpdateTenantService.java`, `src/main/java/com/filestorage/application/usecase/tenant/DeactivateTenantService.java`, `src/main/java/com/filestorage/application/usecase/tenant/TenantAccessPolicy.java`, `src/main/java/com/filestorage/application/usecase/tenant/TenantSearchSpecifications.java`.
4. Tạo mới: `src/main/java/com/filestorage/domain/model/tenant/TenantView.java`, `src/main/java/com/filestorage/domain/model/tenant/TenantPageView.java`, `src/main/java/com/filestorage/adapter/out/persistence/mapper/TenantViewMapper.java`, `src/test/java/com/filestorage/adapter/in/rest/controller/TenantControllerTest.java`, các service tests trong `src/test/java/com/filestorage/application/usecase/tenant`.
5. Chỉnh sửa: `src/main/java/com/filestorage/adapter/out/persistence/repository/TenantJpaRepository.java`, `src/main/java/com/filestorage/shared/exception/ErrorCode.java`, `src/main/resources/messages.properties`.

### Rule cần khóa trong service:
Chỉ SUPER_ADMIN mới được thao tác.
slug unique toàn cục (POST/PATCH).
storageQuotaBytes >= storageUsedBytes (PATCH).
DELETE là soft-delete (isActive=false) + idempotent.
Không cho delete system tenant (slug=system theo seed hiện tại).

### Error code + i18n
   Thêm/bảo đảm có:
   TENANT_NOT_FOUND
   SLUG_ALREADY_EXISTS
   QUOTA_BELOW_USED
   SYSTEM_TENANT_CANNOT_BE_DELETED
   HTTP status theo spec: 404, 409, 400, 409.

### Further Considerations
1. Rule tenant hệ thống cần chốt: giữ slug `system` (đang seed)
2. Response list tenant  tái dùng `PageResponse` hiện có
3. Ưu tiên Option A: làm đủ 5 APIs + unit/controller tests

