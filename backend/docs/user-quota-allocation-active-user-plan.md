# User Quota Allocation and Active User API Plan

## Muc tieu

Dieu chinh cach tinh quota tenant/user theo cac quy tac moi:

- `Tenant.storageUsedBytes` se duoc dung nhu phan tenant quota dang bi chiem dung, khong con la tong dung luong file thuc te cua tenant.
- Khi user active, toan bo `User.storageQuotaBytes` cua user do duoc tinh vao `Tenant.storageUsedBytes`.
- Khi user inactive, chi phan `User.storageUsedBytes` thuc te cua user do con bi tinh vao `Tenant.storageUsedBytes`; phan quota chua su dung duoc release.
- API update user co the sua thong tin cua ca active va inactive user.
- Can bo sung API activate user.

## Invariant quota moi

Voi moi tenant:

```text
tenantRemainingQuota = tenant.storageQuotaBytes - tenant.storageUsedBytes
```

Y nghia cua `tenant.storageUsedBytes`:

```text
sum(activeUsers.storageQuotaBytes)
+ sum(inactiveUsers.storageUsedBytes)
```

Dieu nay dam bao:

- Active user duoc reserve full quota.
- Inactive user khong giu phan quota chua dung.
- File da upload cua inactive user van chiem quota tenant, vi du lieu van con ton tai.

## Luong create user

File can sua:

- `src/main/java/com/filestorage/application/usecase/user/CreateUserService.java`
- `src/main/java/com/filestorage/adapter/out/persistence/repository/TenantJpaRepository.java`

Plan:

1. Them repository method de lock tenant row khi tao user, vi create user song song co the cung doc mot remaining quota.

```java
@Lock(LockModeType.PESSIMISTIC_WRITE)
@Query("SELECT t FROM Tenant t WHERE t.id = :id")
Optional<Tenant> findByIdForUpdate(@Param("id") UUID id);
```

2. Trong `CreateUserService`, lay tenant bang `findByIdForUpdate`.
3. Resolve `requestedQuota`.
4. Check:

```java
requestedQuota <= tenant.storageQuotaBytes - tenant.storageUsedBytes
```

5. Neu hop le:

```java
tenant.storageUsedBytes += requestedQuota;
user.storageQuotaBytes = requestedQuota;
user.storageUsedBytes = 0;
user.isActive = true;
```

6. Save tenant va user trong cung transaction.

## Luong update user

File can sua:

- `src/main/java/com/filestorage/application/usecase/user/UpdateUserService.java`

Plan:

1. Load target user kem tenant.
2. Check permission nhu hien tai.
3. Cho phep update ca active va inactive user.
4. Cac thong tin khong lien quan quota nhu `fullName`, `roleCode` duoc update nhu hien tai sau khi check permission va role assignment.
5. Neu update quota:

```java
oldQuota = target.storageQuotaBytes;
newQuota = command.storageQuotaBytes;
used = target.storageUsedBytes;
delta = newQuota - oldQuota;
```

6. Neu `newQuota < used` thi throw `QUOTA_BELOW_USED`, ap dung cho ca active va inactive user.
7. Neu target dang active:
   - Active user dang reserve full quota, nen phai check tenant remaining khi tang quota.
   - Neu `delta > tenantRemainingQuota` thi throw `TENANT_QUOTA_EXCEEDED`.
   - Neu hop le:

```java
tenant.storageUsedBytes += delta;
target.storageQuotaBytes = newQuota;
```

8. Neu target dang inactive:
   - Inactive user chi chiem tenant quota bang `target.storageUsedBytes`.
   - Update `target.storageQuotaBytes` khong lam thay doi `tenant.storageUsedBytes`.
   - Khong check tenant remaining tai thoi diem update inactive user, vi phan quota chua dung chi duoc reserve khi activate.
   - Khi activate sau nay, service activate se check va reserve:

```java
quotaToReserve = target.storageQuotaBytes - target.storageUsedBytes;
```

9. Save tenant neu co thay doi tenant usage, save target user trong cung transaction.

## Luong deactivate user

File can sua:

- `src/main/java/com/filestorage/application/usecase/user/DeactivateUserService.java`

Plan:

1. Load target user kem tenant.
2. Check permission nhu hien tai.
3. Giu rule khong cho self-deactivation nhu hien tai.
4. Neu target dang active:

```java
unusedQuota = target.storageQuotaBytes - target.storageUsedBytes;
tenant.storageUsedBytes -= unusedQuota;
target.isActive = false;
```

5. Clamp `tenant.storageUsedBytes` ve `0` neu du lieu cu bi lech:

```java
tenant.storageUsedBytes = Math.max(tenant.storageUsedBytes - unusedQuota, 0);
```

6. Neu target da inactive thi idempotent, khong tru quota lan nua.
7. Van xoa refresh token nhu hien tai.

Vi du:

```text
tenant quota = 100
user quota = 30
user used = 12

Before deactivate:
tenant.storageUsedBytes includes 30

After deactivate:
release = 30 - 12 = 18
tenant.storageUsedBytes still includes 12
```

## Luong activate user

Files can them/sua:

- `src/main/java/com/filestorage/domain/port/in/user/ActivateUserUseCase.java`
- `src/main/java/com/filestorage/application/usecase/user/ActivateUserService.java`
- `src/main/java/com/filestorage/adapter/in/rest/controller/UserController.java`

Plan:

1. Tao inbound port `ActivateUserUseCase`.

```java
public interface ActivateUserUseCase {
    UserView execute(ActivateUserCommand command);

    record ActivateUserCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID targetUserId
    ) {
    }
}
```

2. Tao `ActivateUserService`.
3. Load target user kem role va tenant.
4. Check permission:

```java
UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());
```

5. Neu target da active thi idempotent, tra ve user hien tai.
6. Neu target inactive, can reserve lai phan quota chua dung:

```java
quotaToReserve = target.storageQuotaBytes - target.storageUsedBytes;
```

7. Check:

```java
quotaToReserve <= tenant.storageQuotaBytes - tenant.storageUsedBytes
```

8. Neu khong du thi throw `TENANT_QUOTA_EXCEEDED`.
9. Neu hop le:

```java
tenant.storageUsedBytes += quotaToReserve;
target.isActive = true;
```

10. Save tenant va user trong cung transaction.

## REST API activate user

File can sua:

- `src/main/java/com/filestorage/adapter/in/rest/controller/UserController.java`

Endpoint:

```http
PATCH /api/v1/users/{userId}/activate
```

Authorization:

```java
@PreAuthorize("hasAnyRole('SUPER_ADMIN', 'TENANT_ADMIN')")
```

Controller flow:

```java
SecurityUser actor = currentUserOrThrow();
var result = activateUserUseCase.execute(new ActivateUserUseCase.ActivateUserCommand(
        UUID.fromString(actor.getUserId()),
        UUID.fromString(actor.getTenantId()),
        parseRole(actor.getRole()),
        userId
));

return ResponseEntity.ok(ApiResponse.buildSuccess(userRestMapper.toUserDetailResponse(result)));
```

## Luong upload va scan file

Files can sua/kiem tra:

- `src/main/java/com/filestorage/application/usecase/file/UploadFileService.java`
- `src/main/java/com/filestorage/application/usecase/file/BulkUploadFilesService.java`
- `src/main/java/com/filestorage/application/usecase/file/InitPresignedUploadService.java`
- `src/main/java/com/filestorage/application/usecase/file/InitMultipartUploadService.java`
- `src/main/java/com/filestorage/application/usecase/file/VirusScanResultHandler.java`

Plan:

1. Khong dung tenant quota de check upload theo file bytes nua, vi tenant quota da duoc reserve khi user active.
2. Upload chi check user quota:

```java
user.storageUsedBytes + uploadBytes <= user.storageQuotaBytes
```

3. Khi upload thanh cong hoac scan clean:

```java
user.storageUsedBytes += fileBytes;
```

4. Khong cong `tenant.storageUsedBytes` khi active user upload, vi phan quota cua user da duoc reserve san.
5. Can dam bao inactive user khong upload duoc. Neu security hien tai da chan inactive user login thi khong can them check rieng; neu chua chac, them guard trong upload/init upload:

```java
if (!actor.isActive()) {
    throw new AppException(ErrorCode.ACCESS_DENIED);
}
```

6. Trong `VirusScanResultHandler`, neu user bi deactivate trong luc file dang scan:
   - Khi deactivate, tenant chi con giu `target.storageUsedBytes` tai thoi diem deactivate.
   - Neu scan clean sau do lam tang `user.storageUsedBytes`, can dong thoi tang `tenant.storageUsedBytes` neu uploader dang inactive.
   - Neu uploader dang active thi khong tang tenant.

Pseudo:

```java
user.storageUsedBytes += size;
if (!user.isActive()) {
    tenant.storageUsedBytes += size;
}
```

Can check user quota nhu hien tai truoc khi cong.

## Luong delete file

File can sua:

- `src/main/java/com/filestorage/application/usecase/file/DirectDeleteFileService.java`

Plan:

1. Khi xoa file active hoac pending delete, tru `uploader.storageUsedBytes`.
2. Neu uploader dang inactive, dong thoi tru `tenant.storageUsedBytes`, vi inactive user chi con chiem tenant quota bang dung luong file thuc te.
3. Neu uploader dang active, khong tru tenant usage, vi full user quota van dang duoc reserve.

Pseudo:

```java
uploader.storageUsedBytes -= fileSize;
if (!uploader.isActive()) {
    tenant.storageUsedBytes -= fileSize;
}
```

Clamp ve `0` neu du lieu cu bi lech.

## Luong delete/restore soft delete neu co update storage

Files can kiem tra:

- `src/main/java/com/filestorage/application/usecase/file/DeleteFileService.java`
- `src/main/java/com/filestorage/application/usecase/file/RestoreFileService.java`
- Scheduler purge neu co tru storage khi purge.

Plan:

1. Neu soft delete khong tru storage ngay thi khong can doi.
2. Neu purge sau retention moi tru storage:
   - Tru user storage.
   - Chi tru tenant storage neu uploader inactive.
3. Neu restore lam tang user storage:
   - Chi tang tenant storage neu uploader inactive.
   - Active user da reserve full quota nen khong tang tenant.

## Migration/backfill

Can them Flyway migration vi `tenant.storageUsedBytes` doi y nghia.

File moi:

- `src/main/resources/db/migration/V5__backfill_tenant_allocated_quota.sql`

SQL du kien:

```sql
UPDATE tenants t
SET storage_used_bytes = COALESCE((
    SELECT SUM(
        CASE
            WHEN u.is_active = true THEN u.storage_quota_bytes
            ELSE u.storage_used_bytes
        END
    )
    FROM users u
    WHERE u.tenant_id = t.id
), 0);
```

## Tests can cap nhat

Create user:

- Tao active user thanh cong thi tenant `storageUsedBytes` tang bang `requestedQuota`.
- Tao user vuot `tenantRemainingQuota` thi throw `TENANT_QUOTA_EXCEEDED`.
- Tao nhieu user voi tenant remaining bi gioi han.

Update user:

- Update inactive user thanh cong voi `fullName`, `roleCode`, `storageQuotaBytes`.
- Update quota inactive user khong lam doi `tenant.storageUsedBytes`.
- Update quota inactive user voi `newQuota < user.storageUsedBytes` thi throw `QUOTA_BELOW_USED`.
- Active user tang quota thi tenant `storageUsedBytes` tang theo delta.
- Active user giam quota thi tenant `storageUsedBytes` giam theo delta.
- `newQuota < user.storageUsedBytes` thi throw `QUOTA_BELOW_USED`.
- Tang quota vuot tenant remaining thi throw `TENANT_QUOTA_EXCEEDED`.

Deactivate user:

- Active user deactivate thi release `storageQuotaBytes - storageUsedBytes`.
- Inactive user deactivate lai khong tru them quota.
- Refresh token van bi xoa.

Activate user:

- Inactive user activate thanh cong thi reserve `storageQuotaBytes - storageUsedBytes`.
- Activate khi tenant remaining khong du thi throw `TENANT_QUOTA_EXCEEDED`.
- Active user activate lai idempotent.
- Tenant admin khong activate duoc user khac tenant.

Upload/delete:

- Upload active user chi tang user `storageUsedBytes`, khong tang tenant `storageUsedBytes`.
- Delete file cua active uploader chi giam user `storageUsedBytes`.
- Delete file cua inactive uploader giam ca user `storageUsedBytes` va tenant `storageUsedBytes`.
- Scan clean cua inactive uploader tang ca user va tenant storage used.

Controller:

- `PATCH /api/v1/users/{userId}/activate` tra ve user detail.
- Endpoint chi cho `SUPER_ADMIN` va `TENANT_ADMIN`.

## Thu tu thuc hien de giam rui ro

1. Them tenant lock method.
2. Sua create/update/deactivate user.
3. Them activate use case/service/controller.
4. Sua upload/delete/scan theo semantic moi.
5. Them migration backfill.
6. Cap nhat unit tests.
7. Chay test lien quan:

```bash
./gradlew test --tests '*CreateUserServiceTest' --tests '*UpdateUserServiceTest' --tests '*DeactivateUserServiceTest' --tests '*ActivateUserServiceTest'
```

8. Chay full test neu cac test nho da pass:

```bash
./gradlew test
```
