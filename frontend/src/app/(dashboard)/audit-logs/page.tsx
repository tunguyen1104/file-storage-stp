export default function AuditLogsPage() {
  // TODO: Use useAuditLogs() hook
  // TODO: Render <AuditLogTable /> with filters
  // NOTE: Only accessible by SUPER_ADMIN and TENANT_ADMIN (enforced by middleware)
  return (
    <div>
      <h1 className="text-2xl font-bold">Nhật ký hoạt động</h1>
      <p className="text-muted-foreground">Xem nhật ký hoạt động của hệ thống và lịch sử kiểm tra.</p>
    </div>
  );
}
