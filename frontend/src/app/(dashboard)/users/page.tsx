export default function UsersPage() {
  // TODO: Use useUsers() hook
  // TODO: Render <UserTable /> with role badges
  // NOTE: Accessible by SUPER_ADMIN and TENANT_ADMIN (enforced by middleware)
  return (
    <div>
      <h1 className="text-2xl font-bold">Users</h1>
      <p className="text-muted-foreground">Manage users and role assignments.</p>
    </div>
  );
}
