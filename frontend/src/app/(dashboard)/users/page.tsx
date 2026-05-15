// src/app/(dashboard)/users/page.tsx
"use client";

import { useState } from "react";
import useSWR from "swr";
import { useRouter } from "next/navigation";
import { Plus, Search, UserCircle2, Pencil, UserMinus, UserPlus, MoreVertical, Trash2, ArrowRight, HardDrive } from "lucide-react";

import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Pagination,
  PaginationContent,
  PaginationItem,
  PaginationLink,
  PaginationNext,
  PaginationPrevious,
} from "@/components/ui/pagination";
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from "@/components/ui/table";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { Switch } from "@/components/ui/switch";
import {
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { listUsers, listTenantUsers } from "@/lib/api/users";
import { getTenantMe } from "@/lib/api/tenants";
import { useAuth } from "@/hooks/use-auth";
import { useDebounce } from "@/hooks/use-debounce";
import { ROLES } from "@/lib/constants";
import {
  CreateUserDialog,
  EditUserDialog,
  DeactivateUserDialog,
  ActivateUserDialog,
} from "@/components/users/user-dialogs";
import { formatBytes } from "@/lib/utils/storage";
import type { UserResponse } from "@/types/user/user";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

const PAGE_SIZE = 20;

function RoleBadge({ role }: { role: string }) {
  if (role === ROLES.SUPER_ADMIN) {
    return (
      <Badge className="bg-violet-100 text-violet-700 hover:bg-violet-100 border-violet-200 border">
        Super Admin
      </Badge>
    );
  }
  if (role === ROLES.TENANT_ADMIN) {
    return <Badge>Tenant Admin</Badge>;
  }
  return <Badge variant="secondary">Member</Badge>;
}

export default function UsersPage() {
  const { user } = useAuth();
  const isSuperAdmin = user?.role === ROLES.SUPER_ADMIN;

  if (isSuperAdmin) {
    return <SuperAdminView />;
  }

  return <TenantAdminDashboard />;
}

function TenantAdminDashboard() {
  const router = useRouter();
  const { user } = useAuth();
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [filterByStatus, setFilterByStatus] = useState("all");
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [deactivateUser, setDeactivateUser] = useState<UserResponse | null>(null);
  const [activateUser, setActivateUser] = useState<UserResponse | null>(null);

  // Security settings states
  const [ssoEnabled, setSsoEnabled] = useState(true);
  const [twoFactorEnabled, setTwoFactorEnabled] = useState(true);

  const debouncedSearch = useDebounce(search, 400);

  const { data, isLoading, mutate } = useSWR(
    ["users", currentPage, debouncedSearch, filterByStatus],
    ([, page, q, status]) =>
      listTenantUsers({
        page,
        limit: 5,
        search: q || undefined,
        isActive: status === "active" ? true : status === "inactive" ? false : undefined
      })
  );

  const { data: tenantMe, mutate: mutateTenantMe } = useSWR("tenantMe", getTenantMe);
  const storageUsedBytes = tenantMe?.storageUsedBytes ?? 0;
  const storageQuotaBytes = tenantMe?.storageQuotaBytes ?? 1; // avoid division by zero
  const storagePercent = tenantMe ? Math.min(100, Math.round((storageUsedBytes / storageQuotaBytes) * 100)) : 0;

  const users = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;
  const totalElements = data?.totalElements ?? 0;

  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleStatusFilterChange(value: string) {
    setFilterByStatus(value);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
    mutateTenantMe();
  }

  function getAvatarFromName(name: string) {
    const encodedName = encodeURIComponent(name);
    return `https://api.dicebear.com/7.x/initials/svg?seed=${encodedName}&backgroundColor=0000ff`;
  }


  return (
    <div className="bg-gray-50 min-h-[calc(100vh-2rem)] p-4 md:p-8">
      {/* Header section matching screenshot exactly */}
      <div className="mb-8">
        <h1 className="text-2xl font-bold text-gray-900 leading-tight">Organization Dashboard</h1>
        <p className="text-base text-gray-500 mt-1">Manage your team, resources, and organization-wide security protocols.</p>
      </div>

      {/* Search and Invite Member */}
      <div className="flex flex-col md:flex-row gap-4 mb-8">
        <div className="relative flex-1">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-5 w-5 -translate-y-1/2 text-gray-400" />
          <Input
            className="h-12 pl-10 border-0 shadow-sm bg-white focus-visible:ring-[#7C3AED] rounded-lg text-base w-full"
            placeholder="Search member"
            value={search}
            onChange={(e) => handleSearchChange(e.target.value)}
          />
        </div>
        <Button
          className="h-12 bg-[#5B5CEF] hover:bg-[#4C4DE0] text-white px-6 font-medium rounded-lg text-base shadow-sm"
          onClick={() => setCreateOpen(true)}
        >
          Invite Member
        </Button>
      </div>

      <div className="grid grid-cols-1 lg:grid-cols-[1fr_340px] gap-6">
        {/* Left Column: Team Members Table */}
        <div className="bg-white rounded-xl border border-gray-200 shadow-sm overflow-hidden flex flex-col">
          <div className="px-6 py-5 border-b border-gray-100 flex flex-col md:flex-row md:items-center justify-between gap-4">
            <div className="flex items-center gap-3">
              <div className="w-10 h-10 bg-gray-50 rounded-lg flex items-center justify-center">
                <UserCircle2 className="w-5 h-5 text-gray-500" />
              </div>
              <div>
                <h2 className="text-lg font-bold text-gray-900">Team Members</h2>
                <p className="text-sm text-gray-500">Manage roles and workspace access for {totalElements} users</p>
              </div>
            </div>

            <div className="w-[180px]">
              <Select value={filterByStatus} onValueChange={handleStatusFilterChange}>
                <SelectTrigger className="w-full h-10 border-gray-200 text-sm">
                  <SelectValue placeholder="Filter by status" />
                </SelectTrigger>
                <SelectContent>
                  <SelectItem value="all">All statuses</SelectItem>
                  <SelectItem value="active">Active</SelectItem>
                  <SelectItem value="inactive">Inactive</SelectItem>
                </SelectContent>
              </Select>
            </div>
          </div>

          <div className="flex-1 overflow-x-auto">
            <Table className="min-w-[600px]">
              <TableHeader>
                <TableRow className="hover:bg-transparent border-b border-gray-100 uppercase">
                  <TableHead className="text-xs font-semibold text-gray-500 py-3 px-6 h-auto tracking-wider">MEMBER</TableHead>
                  <TableHead className="text-xs font-semibold text-gray-500 py-3 h-auto tracking-wider">STATUS</TableHead>
                  <TableHead className="text-xs font-semibold text-gray-500 py-3 h-auto tracking-wider">ROLE ASSIGNMENT</TableHead>
                  <TableHead className="text-xs font-semibold text-gray-500 py-3 h-auto text-right tracking-wider pr-6">ACTIONS</TableHead>
                </TableRow>
              </TableHeader>
              <TableBody>
                {isLoading ? (
                  <TableRow>
                    <TableCell colSpan={4} className="h-32 text-center text-gray-500">Loading...</TableCell>
                  </TableRow>
                ) : users.length === 0 ? (
                  <TableRow>
                    <TableCell colSpan={4} className="h-32 text-center text-gray-500">
                      {debouncedSearch ? `No members found for "${debouncedSearch}"` : "No members found."}
                    </TableCell>
                  </TableRow>
                ) : (
                  users.map((member) => (
                    <TableRow key={member.id} className="hover:bg-gray-50/50 border-b border-gray-50">
                      <TableCell className="px-6 py-4">
                        <div className="flex items-center gap-3">
                          <Avatar className="h-10 w-10 border border-gray-100 shadow-sm relative">
                            <AvatarImage src={getAvatarFromName(member.fullName)} />
                            <AvatarFallback>{member.fullName.charAt(0)}</AvatarFallback>
                            {/* Little badge mimicking design, perhaps mock */}
                            {member.role === ROLES.TENANT_ADMIN && <span className="absolute bottom-0 right-0 w-2.5 h-2.5 bg-yellow-400 border-2 border-white rounded-full"></span>}
                          </Avatar>
                          <div className="flex flex-col">
                            <span className="font-semibold text-gray-900 text-sm leading-tight mb-0.5">{member.fullName}</span>
                            <span className="text-gray-500 text-xs">{member.email}</span>
                          </div>
                        </div>
                      </TableCell>
                      <TableCell className="py-4">
                        {member.isActive ? (
                          <Badge variant="outline" className="bg-white text-gray-700 border-gray-200 font-medium px-3 flex items-center justify-center w-fit">
                            Active
                          </Badge>
                        ) : (
                          <Badge variant="outline" className="bg-gray-50 text-gray-500 border-gray-200 font-medium px-3 flex items-center justify-center w-fit">
                            Inactive
                          </Badge>
                        )}
                      </TableCell>
                      <TableCell className="py-4">
                        <div className="flex shadow-sm rounded-md overflow-hidden bg-gray-50/50 border border-gray-200 w-fit">
                          <div className={`px-4 py-1.5 text-xs font-medium ${member.role === ROLES.TENANT_ADMIN ? 'bg-white text-[#5B5CEF] shadow-sm' : 'text-gray-500'}`}>Admin</div>
                          <div className={`px-4 py-1.5 text-xs font-medium ${member.role !== ROLES.TENANT_ADMIN ? 'bg-white text-[#5B5CEF] shadow-sm' : 'text-gray-500'}`}>Member</div>
                        </div>
                      </TableCell>
                      <TableCell className="py-4 pr-6 text-right">
                        <div className="flex items-center justify-end gap-2">
                          {member.isActive ? (
                            <Button
                              variant="default"
                              size="sm"
                              className="h-8 bg-[#EF4444] hover:bg-[#DC2626] text-white px-3 text-xs font-semibold uppercase tracking-wider"
                              onClick={() => setDeactivateUser(member)}
                            >
                              Delete
                            </Button>
                          ) : (
                            <Button
                              variant="default"
                              size="sm"
                              className="h-8 bg-green-500 hover:bg-green-600 text-white px-3 text-xs font-semibold uppercase tracking-wider"
                              onClick={() => setActivateUser(member)}
                            >
                              Active
                            </Button>
                          )}
                          <Button
                            variant="default"
                            size="sm"
                            className="h-8 bg-[#5B5CEF] hover:bg-[#4C4DE0] text-white px-4 text-xs font-semibold uppercase tracking-wider"
                            onClick={() => setEditUser(member)}
                          >
                            Detail
                          </Button>
                        </div>
                      </TableCell>
                    </TableRow>
                  ))
                )}
              </TableBody>
            </Table>
          </div>

          <div className="px-6 py-4 border-t border-gray-100 flex items-center justify-between text-sm text-gray-500">
            <span>Showing {users.length} of {totalElements} members</span>

            <div className="flex gap-2">
              <Button
                variant="outline"
                className="h-9 px-4 text-sm font-medium"
                disabled={currentPage === 1}
                onClick={() => setCurrentPage((p) => Math.max(1, p - 1))}
              >
                Previous
              </Button>
              <Button
                variant="outline"
                className="h-9 px-4 text-sm font-medium text-gray-900 border-gray-200"
                disabled={currentPage === totalPages || totalPages === 0}
                onClick={() => setCurrentPage((p) => Math.min(totalPages, p + 1))}
              >
                Next
              </Button>
            </div>
          </div>
        </div>

        {/* Right Column: Storage & Security */}
        <div className="flex flex-col gap-6">
          {/* Storage Quota Card */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center justify-between mb-4">
              <div className="flex items-center gap-3">
                <div className="w-8 h-8 rounded-lg bg-blue-50 flex items-center justify-center text-blue-500">
                  <HardDrive className="w-4 h-4" />
                </div>
                <h3 className="font-bold text-gray-900">Storage Quota</h3>
              </div>
            </div>

            <div className="mt-8 flex items-end justify-between">
              <div>
                <span className="text-3xl font-bold text-gray-900 leading-none">{formatBytes(storageUsedBytes)}</span>
                <span className="text-sm text-gray-500 ml-1">used of {formatBytes(storageQuotaBytes)}</span>
              </div>
              <span className="text-[#5B5CEF] font-medium text-sm mb-1">{storagePercent}%</span>
            </div>

            <div className="h-2.5 w-full bg-gray-100 rounded-full overflow-hidden mt-3 relative">
              <div className="h-full bg-[#5B5CEF] rounded-full transition-all duration-500" style={{ width: `${storagePercent}%` }}></div>
            </div>
          </div>

          {/* Security Settings Card */}
          <div className="bg-white rounded-xl border border-gray-200 shadow-sm p-6">
            <div className="flex items-center gap-3 mb-6">
              <div className="w-8 h-8 rounded-lg bg-gray-50 flex flex-col items-center justify-center text-gray-700">
                <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path></svg>
              </div>
              <h3 className="font-bold text-gray-900">Security Settings</h3>
            </div>

            <div className="space-y-6">
              <div className="flex items-center justify-between">
                <div className="flex gap-3">
                  <div className="mt-0.5 text-gray-400">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><path d="M12 22s8-4 8-10V5l-8-3-8 3v7c0 6 8 10 8 10z"></path><path d="M12 8v4"></path><path d="M12 16h.01"></path></svg>
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-gray-900">Single Sign-On (SSO)</h4>
                    <p className="text-xs text-gray-500">Force users to login via Okta or Azure AD</p>
                  </div>
                </div>
                <Switch checked={ssoEnabled} onCheckedChange={setSsoEnabled} className="data-[state=checked]:bg-[#5B5CEF] cursor-pointer" />
              </div>

              <div className="flex items-center justify-between">
                <div className="flex gap-3">
                  <div className="mt-0.5 text-gray-400">
                    <svg viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><rect x="3" y="11" width="18" height="11" rx="2" ry="2"></rect><path d="M7 11V7a5 5 0 0 1 10 0v4"></path></svg>
                  </div>
                  <div>
                    <h4 className="text-sm font-bold text-gray-900">Two-Factor Auth (2FA)</h4>
                    <p className="text-xs text-gray-500">Mandatory 2FA for all team members</p>
                  </div>
                </div>
                <Switch checked={twoFactorEnabled} onCheckedChange={setTwoFactorEnabled} className="data-[state=checked]:bg-[#5B5CEF] cursor-pointer" />
              </div>
            </div>

            <div
              onClick={() => router.push('/audit-logs')}
              className="mt-8 pt-4 border-t border-gray-100 flex items-center justify-between cursor-pointer group hover:bg-gray-50 -mx-6 px-6 -mb-6 pb-6"
            >
              <div className="flex items-center gap-2">
                <svg className="text-gray-400" viewBox="0 0 24 24" width="16" height="16" stroke="currentColor" strokeWidth="2" fill="none" strokeLinecap="round" strokeLinejoin="round"><circle cx="12" cy="12" r="10"></circle><polyline points="12 6 12 12 16 14"></polyline></svg>
                <span className="text-sm font-bold text-gray-700">View Audit Logs</span>
              </div>
              <ArrowRight className="w-4 h-4 text-gray-400 group-hover:text-gray-600 group-hover:translate-x-1 transition-transform" />
            </div>
          </div>
        </div>
      </div>

      <CreateUserDialog
        open={createOpen}
        onOpenChange={setCreateOpen}
        isSuperAdmin={false}
        onSuccess={handleSuccess}
      />

      <EditUserDialog
        user={editUser}
        open={editUser !== null}
        onOpenChange={(open: boolean) => { if (!open) setEditUser(null); }}
        isSuperAdmin={false}
        onSuccess={handleSuccess}
      />

      <DeactivateUserDialog
        user={deactivateUser}
        open={deactivateUser !== null}
        onOpenChange={(open: boolean) => { if (!open) setDeactivateUser(null); }}
        onSuccess={() => {
          setDeactivateUser(null);
          handleSuccess();
        }}
      />

      <ActivateUserDialog
        user={activateUser}
        open={activateUser !== null}
        onOpenChange={(open) => !open && setActivateUser(null)}
        onSuccess={() => {
          setActivateUser(null);
          handleSuccess();
        }}
      />
    </div>
  );
}

function SuperAdminView() {
  const { user } = useAuth();
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [deactivateUser, setDeactivateUser] = useState<UserResponse | null>(null);
  const [activateUser, setActivateUser] = useState<UserResponse | null>(null);

  const isSuperAdmin = user?.role === ROLES.SUPER_ADMIN;
  const debouncedSearch = useDebounce(search, 400);

  const { data, isLoading, mutate } = useSWR(
    ["users", currentPage, isSuperAdmin, debouncedSearch],
    ([, page, , q]) => listUsers({ page, limit: PAGE_SIZE, search: q || undefined })
  );

  const users = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;
  const totalElements = data?.totalElements ?? 0;

  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
  }

  const colSpan = 7;

  return (
    <TooltipProvider>
      <section className="space-y-4 p-4 md:p-8 min-h-[calc(100vh-2rem)]">
        {/* Toolbar */}
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 md:flex-row md:items-center md:justify-between">
          <div className="relative w-full md:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="h-10 pl-9"
              placeholder="Search by name or email..."
              value={search}
              onChange={(e) => handleSearchChange(e.target.value)}
            />
          </div>
          <Button className="gap-2" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            Create New User
          </Button>
        </div>

        {/* Table */}
        <div className="rounded-lg border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Email</TableHead>
                <TableHead>Role</TableHead>
                <TableHead>Tenant</TableHead>
                <TableHead>Storage Quota</TableHead>
                <TableHead>Status</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={colSpan} className="py-10 text-center text-muted-foreground">
                    Loading...
                  </TableCell>
                </TableRow>
              ) : users.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={colSpan} className="py-10 text-center text-muted-foreground">
                    {debouncedSearch ? `No users found for "${debouncedSearch}"` : "No users found."}
                  </TableCell>
                </TableRow>
              ) : (
                users.map((member) => (
                  <TableRow key={member.id} className="group hover:bg-muted/50">
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <UserCircle2 className="h-4 w-4 shrink-0 text-muted-foreground" />
                        <span className="truncate">{member.fullName}</span>
                      </div>
                    </TableCell>
                    <TableCell className="text-muted-foreground">{member.email}</TableCell>
                    <TableCell>
                      <RoleBadge role={member.role} />
                    </TableCell>
                    <TableCell className="text-muted-foreground">
                      {member.tenantName ?? <span className="italic text-muted-foreground/60">System</span>}
                    </TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">
                      {formatBytes(member.storageQuotaBytes)}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={member.isActive ? "default" : "secondary"}
                        className={member.isActive ? "bg-green-100 text-green-700 hover:bg-green-100 border border-green-200" : ""}
                      >
                        {member.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell>
                      <div className="flex items-center justify-end gap-0.5 opacity-0 transition-opacity group-hover:opacity-100">
                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-8 w-8"
                              onClick={() => setEditUser(member)}
                            >
                              <Pencil className="h-4 w-4" />
                              <span className="sr-only">Edit</span>
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Edit user</TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger asChild>
                            {member.isActive ? (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                                onClick={() => setDeactivateUser(member)}
                              >
                                <UserMinus className="h-4 w-4" />
                                <span className="sr-only">Deactivate</span>
                              </Button>
                            ) : (
                              <Button
                                size="icon"
                                variant="ghost"
                                className="h-8 w-8 text-green-600 hover:bg-green-600/10 hover:text-green-600"
                                onClick={() => setActivateUser(member)}
                              >
                                <UserPlus className="h-4 w-4" />
                                <span className="sr-only">Activate</span>
                              </Button>
                            )}
                          </TooltipTrigger>
                          <TooltipContent>
                            {member.isActive ? "Deactivate user" : "Activate user"}
                          </TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          <div className="border-t border-border px-4 py-3 flex items-center justify-between text-sm text-muted-foreground">
            <span>
              Showing {users.length > 0 ? (currentPage - 1) * PAGE_SIZE + 1 : 0}-
              {Math.min(currentPage * PAGE_SIZE, totalElements)} of {totalElements} users
            </span>
            {totalPages > 1 && (
              <Pagination className="mx-0 w-auto">
                <PaginationContent>
                  <PaginationItem>
                    <PaginationPrevious
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage((p) => Math.max(1, p - 1));
                      }}
                      className={currentPage === 1 ? "pointer-events-none opacity-50" : ""}
                    />
                  </PaginationItem>

                  {Array.from({ length: totalPages }, (_, i) => i + 1).map((page) => (
                    <PaginationItem key={page}>
                      <PaginationLink
                        href="#"
                        isActive={page === currentPage}
                        onClick={(e) => {
                          e.preventDefault();
                          setCurrentPage(page);
                        }}
                      >
                        {page}
                      </PaginationLink>
                    </PaginationItem>
                  ))}

                  <PaginationItem>
                    <PaginationNext
                      href="#"
                      onClick={(e) => {
                        e.preventDefault();
                        setCurrentPage((p) => Math.min(totalPages, p + 1));
                      }}
                      className={currentPage === totalPages ? "pointer-events-none opacity-50" : ""}
                    />
                  </PaginationItem>
                </PaginationContent>
              </Pagination>
            )}
          </div>
        </div>

        <CreateUserDialog
          open={createOpen}
          onOpenChange={setCreateOpen}
          isSuperAdmin={true}
          onSuccess={handleSuccess}
        />

        <EditUserDialog
          user={editUser}
          open={editUser !== null}
          onOpenChange={(open: boolean) => { if (!open) setEditUser(null); }}
          isSuperAdmin={true}
          onSuccess={handleSuccess}
        />

        <DeactivateUserDialog
          user={deactivateUser}
          open={deactivateUser !== null}
          onOpenChange={(open: boolean) => { if (!open) setDeactivateUser(null); }}
          onSuccess={handleSuccess}
        />

        <ActivateUserDialog
          user={activateUser}
          open={activateUser !== null}
          onOpenChange={(open: boolean) => { if (!open) setActivateUser(null); }}
          onSuccess={handleSuccess}
        />
      </section>
    </TooltipProvider>
  );
}
