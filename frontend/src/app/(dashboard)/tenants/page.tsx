// src/app/(dashboard)/tenants/page.tsx
"use client";

import { useState } from "react";
import useSWR from "swr";
import { Building2, Plus, Search, Pencil, Ban } from "lucide-react";

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
import { listTenants } from "@/lib/api/tenants";
import { useDebounce } from "@/hooks/use-debounce";
import {
  CreateTenantDialog,
  EditTenantDialog,
  DeactivateTenantDialog,
} from "@/components/tenants/tenant-dialogs";
import type { TenantResponse } from "@/types/tenant/tenant";
import { DEFAULT_PAGE_SIZE } from "@/lib/constants";
import { formatStorageGb } from "@/lib/utils/storage";

export default function TenantsPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [search, setSearch] = useState("");
  const [createOpen, setCreateOpen] = useState(false);
  const [editTenant, setEditTenant] = useState<TenantResponse | null>(null);
  const [deactivateTenant, setDeactivateTenant] = useState<TenantResponse | null>(null);

  const debouncedSearch = useDebounce(search, 400);

  const { data, isLoading, mutate } = useSWR(
    ["tenants", currentPage, debouncedSearch],
    ([, page, q]) => listTenants({ page, limit: DEFAULT_PAGE_SIZE, search: q || undefined })
  );

  const tenants = data?.data ?? [];
  const totalPages = data?.totalPages ?? 1;

  function handleSearchChange(value: string) {
    setSearch(value);
    setCurrentPage(1);
  }

  function handleSuccess() {
    mutate();
  }

  return (
    <TooltipProvider>
      <section className="space-y-4">
        {/* Toolbar */}
        <div className="flex flex-col gap-3 rounded-lg border border-border bg-card p-4 md:flex-row md:items-center md:justify-between">
          <div className="relative w-full md:max-w-sm">
            <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
            <Input
              className="h-10 pl-9"
              placeholder="Search by name or slug..."
              value={search}
              onChange={(e) => handleSearchChange(e.target.value)}
            />
          </div>
          <Button className="gap-2" onClick={() => setCreateOpen(true)}>
            <Plus className="h-4 w-4" />
            Create New Tenant
          </Button>
        </div>

        {/* Table */}
        <div className="rounded-lg border border-border bg-card">
          <Table>
            <TableHeader>
              <TableRow>
                <TableHead>Name</TableHead>
                <TableHead>Slug</TableHead>
                <TableHead>Storage Quota</TableHead>
                <TableHead>Status</TableHead>
                <TableHead>Created</TableHead>
                <TableHead className="w-[80px]" />
              </TableRow>
            </TableHeader>
            <TableBody>
              {isLoading ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                    Loading...
                  </TableCell>
                </TableRow>
              ) : tenants.length === 0 ? (
                <TableRow>
                  <TableCell colSpan={6} className="py-10 text-center text-muted-foreground">
                    {debouncedSearch
                      ? `No tenants found for "${debouncedSearch}"`
                      : "No tenants found."}
                  </TableCell>
                </TableRow>
              ) : (
                tenants.map((tenant) => (
                  <TableRow key={tenant.id} className="group hover:bg-muted/50">
                    <TableCell className="font-medium">
                      <div className="flex items-center gap-2">
                        <Building2 className="h-4 w-4 shrink-0 text-muted-foreground" />
                        <span className="truncate">{tenant.name}</span>
                      </div>
                    </TableCell>
                    <TableCell>
                      <code className="rounded bg-muted px-1.5 py-0.5 text-xs font-mono text-muted-foreground">
                        {tenant.slug}
                      </code>
                    </TableCell>
                    <TableCell className="tabular-nums text-muted-foreground">
                      {formatStorageGb(tenant.storageQuotaBytes)}
                    </TableCell>
                    <TableCell>
                      <Badge
                        variant="outline"
                        className={
                          tenant.isActive
                            ? "border-green-200 bg-green-50 text-green-700"
                            : "border-red-200 bg-red-50 text-red-700"
                        }
                      >
                        {tenant.isActive ? "Active" : "Inactive"}
                      </Badge>
                    </TableCell>
                    <TableCell className="text-sm text-muted-foreground">
                      {new Date(tenant.createdAt).toLocaleDateString()}
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
                              onClick={() => setEditTenant(tenant)}
                            >
                              <Pencil className="h-4 w-4" />
                              <span className="sr-only">Edit</span>
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>Edit tenant</TooltipContent>
                        </Tooltip>

                        <Tooltip>
                          <TooltipTrigger asChild>
                            <Button
                              size="icon"
                              variant="ghost"
                              className="h-8 w-8 text-destructive hover:bg-destructive/10 hover:text-destructive"
                              onClick={() => setDeactivateTenant(tenant)}
                              disabled={!tenant.isActive}
                            >
                              <Ban className="h-4 w-4" />
                              <span className="sr-only">Deactivate</span>
                            </Button>
                          </TooltipTrigger>
                          <TooltipContent>
                            {tenant.isActive ? "Deactivate tenant" : "Already inactive"}
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

        <CreateTenantDialog
          open={createOpen}
          onOpenChange={setCreateOpen}
          onSuccess={handleSuccess}
        />

        <EditTenantDialog
          tenant={editTenant}
          open={editTenant !== null}
          onOpenChange={(open) => { if (!open) setEditTenant(null); }}
          onSuccess={handleSuccess}
        />

        <DeactivateTenantDialog
          tenant={deactivateTenant}
          open={deactivateTenant !== null}
          onOpenChange={(open) => { if (!open) setDeactivateTenant(null); }}
          onSuccess={handleSuccess}
        />
      </section>
    </TooltipProvider>
  );
}
