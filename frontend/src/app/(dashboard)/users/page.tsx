// src/app/(dashboard)/users/page.tsx
"use client";

import { useState } from "react";
import useSWR from "swr";
import { Plus, Search, UserCircle2, Pencil, UserMinus } from "lucide-react";

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
  Tooltip,
  TooltipContent,
  TooltipProvider,
  TooltipTrigger,
} from "@/components/ui/tooltip";
import { listUsers, listTenantUsers } from "@/lib/api/users";
import { useAuth } from "@/hooks/use-auth";
import { useDebounce } from "@/hooks/use-debounce";
import { ROLES } from "@/lib/constants";
import {
  CreateUserDialog,
  EditUserDialog,
  DeactivateUserDialog,
} from "@/components/users/user-dialogs";
import type { UserResponse } from "@/types/user/user";

const PAGE_SIZE = 20;
const GB = 1024 * 1024 * 1024;

function formatStorageGb(bytes: number): string {
  if (!bytes) return "—";
  const gb = bytes / GB;
  if (gb >= 1) return `${parseFloat(gb.toFixed(1))} GB`;
  const mb = bytes / (1024 * 1024);
  return `${Math.round(mb)} MB`;
}

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
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [editUser, setEditUser] = useState<UserResponse | null>(null);
  const [deactivateUser, setDeactivateUser] = useState<UserResponse | null>(null);

  const isSuperAdmin = user?.role === ROLES.SUPER_ADMIN;
  const debouncedSearch = useDebounce(search, 400);

  const { data, isLoading, mutate } = useSWR(
    ["users", currentPage, isSuperAdmin, debouncedSearch],
    ([, page, , q]) =>
      isSuperAdmin
        ? listUsers({ page, limit: PAGE_SIZE, search: q || undefined })
        : listTenantUsers({ page, limit: PAGE_SIZE, search: q || undefined })
  );

  const users = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
  }

  const colSpan = isSuperAdmin ? 7 : 6;

  return (
    <TooltipProvider>
      <section className="space-y-4">
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
                {isSuperAdmin && <TableHead>Tenant</TableHead>}
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
                    {isSuperAdmin && (
                      <TableCell className="text-muted-foreground">
                        {member.tenantName ?? <span className="italic text-muted-foreground/60">System</span>}
                      </TableCell>
                    )}
                    <TableCell className="tabular-nums text-muted-foreground">
                      {formatStorageGb(member.storageQuotaBytes)}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant={member.isActive ? "default" : "secondary"}
                        className={member.isActive ? "bg-green-100 text-green-700 hover:bg-green-100 border border-green-200" : ""}
                      >
                        {member.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    {/* Actions — visible on row hover */}
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
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => setDeactivateUser(member)}
                              disabled={!member.isActive}
                            >
                              <UserMinus className="h-4 w-4" />
                              <span className="sr-only">Deactivate</span>
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>
                            {member.isActive ? "Deactivate user" : "Already inactive"}
                          </TooltipContent>
                        </Tooltip>
                      </div>
                    </TableCell>
                  </TableRow>
                ))
              )}
            </TableBody>
          </Table>

          {totalPages > 1 && (
            <div className="border-t border-border px-4 py-3">
              <Pagination>
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
            </div>
          )}
        </div>

        <CreateUserDialog
          open={createOpen}
          onOpenChange={setCreateOpen}
          isSuperAdmin={isSuperAdmin}
          onSuccess={handleSuccess}
        />

        <EditUserDialog
          user={editUser}
          open={editUser !== null}
          onOpenChange={(open) => { if (!open) setEditUser(null); }}
          onSuccess={handleSuccess}
        />

        <DeactivateUserDialog
          user={deactivateUser}
          open={deactivateUser !== null}
          onOpenChange={(open) => { if (!open) setDeactivateUser(null); }}
          onSuccess={handleSuccess}
        />
      </section>
    </TooltipProvider>
  );
}
