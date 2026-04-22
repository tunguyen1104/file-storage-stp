# MinIO Backend Migration Checklist

This checklist follows the rollout order: single upload -> multipart upload -> presigned download.

## 0) Foundation (must be done first)

- [x] Add typed storage config (`MinioProperties`, `StorageProperties`) under `infrastructure.config`.
- [x] Add MinIO client bean wiring in `StorageConfig`.
- [x] Standardize storage key format to `tenantId/userId/fileId.ext` (full path in DB).
- [x] Keep one shared bucket for all tenants; enforce tenant isolation via key prefix.
- [x] Keep bucket private-only and access via presigned URLs.

## 1) Single upload (`POST /uploads/single`)

- [ ] Build `storageKey` as `tenantId/userId/fileId.ext` in use case before calling storage port.
- [ ] Validate max file size with config (`app.storage.single-upload-max-mb`), currently 100 MB.
- [ ] Call `ObjectStoragePort.upload(storageKey, bytes, contentType)` for new content.
- [ ] Persist full `storageKey` into `files.storage_key`.
- [ ] Keep existing quota checks and audit log semantics unchanged.
- [ ] Add/adjust unit tests for upload success and `FILE_TOO_LARGE` at >100 MB.

## 2) Multipart upload (`POST /uploads/multipart/*`)

- [ ] On `initiate`, pre-generate `fileId`, build final `storageKey`, store it in session/file row.
- [ ] Use MinIO multipart upload APIs in adapter (initiate/upload part/complete/abort).
- [ ] Persist part ETags in `multipart_upload_sessions.uploaded_parts`.
- [ ] On `complete`, mark file `ACTIVE`, keep `storageKey` unchanged.
- [ ] On `abort`/expiry, ensure MinIO multipart session is canceled and file is `UPLOAD_FAILED`.
- [ ] Add tests for part ordering, part mismatch, and abort flow.

## 3) Presigned download (`GET /files/:fileId/download`)

- [ ] Read `ttlMinutes` from FE query param.
- [ ] If `ttlMinutes` is missing, use `app.storage.default-download-ttl-minutes` (default 15).
- [ ] Generate URL via `ObjectStoragePort.generatePresignedDownloadUrl(storageKey, ttl)`.
- [ ] Return `{ downloadUrl, expiresAt }` where `expiresAt = now + ttl`.
- [ ] Keep audit behavior: log `FILE_DOWNLOAD` when URL is generated.

## 4) Verification and rollout

- [ ] Add adapter-focused tests (key builder, upload, presign fallback TTL).
- [ ] Add integration test using MinIO test container or local MinIO.
- [ ] Smoke test on dev compose: upload single, multipart, and download via presigned URL.
- [ ] Prepare rollback plan: disable new storage adapter via feature flag/profile if needed.

