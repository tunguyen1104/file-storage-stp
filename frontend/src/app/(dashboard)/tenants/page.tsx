export default function TenantsPage() {
  // TODO: Use useTenants() hook
  // TODO: Render <TenantCard /> grid or table
  // NOTE: Only accessible by SUPER_ADMIN (enforced by middleware)
  return (
    <div>
      <h1 className="text-2xl font-bold">Tenants</h1>
      <p className="text-muted-foreground">Manage platform tenants.</p>
    </div>
  );
}
