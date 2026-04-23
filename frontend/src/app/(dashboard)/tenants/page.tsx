"use client";

import { useEffect, useState } from "react";
import { Building2, Plus, Search } from "lucide-react";

import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
  AlertDialogTrigger,
} from "@/components/ui/alert-dialog";
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
import { listTenants, deactivateTenant } from "@/lib/api/tenants";
import { TenantItem } from "@/types/tenant/tenant";
import Link from "next/link";
import { toast } from "sonner";

const PAGE_SIZE = 12;

export default function TenantsPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [tenants, setTenants] = useState<TenantItem[]>([]);
  const [totalPages, setTotalPages] = useState(1);

  useEffect(() => {
    let isMounted = true;

    listTenants({ page: currentPage, limit: PAGE_SIZE })
      .then((data) => {
        if (!isMounted) return;
        setTenants(data.items || []);
        setTotalPages(data.totalPages || 1);
      })
      .catch(() => {
        if (!isMounted) return;
        setTenants([]);
        setTotalPages(1);
      });

    return () => {
      isMounted = false;
    };
  }, [currentPage]);

  const shouldPaginate = totalPages > 1;

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 rounded-xl border border-border/50 bg-card p-4 shadow-sm md:flex-row md:items-center md:justify-between">
        <div className="relative w-full md:max-w-md">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input className="h-10 bg-muted/50 pl-10 border-border/50 focus-visible:ring-primary/30 transition-all rounded-lg" placeholder="Search tenant..." />
        </div>
        <Button className="gap-2 shadow-sm" asChild>
          <Link href="/tenants/create">
            <Plus className="h-4 w-4" />
            Create New Tenant
          </Link>
        </Button>
      </div>

      <div className="rounded-xl border border-border/50 bg-card shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-muted/30">
            <TableRow className="border-border/50">
              <TableHead>Name</TableHead>
              <TableHead>Domain/Slug</TableHead>
              <TableHead>Status</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {tenants.map((tenant) => (
              <TableRow key={tenant.id} className="border-border/50 hover:bg-muted/40 transition-colors">
                <TableCell className="font-medium text-card-foreground">
                  <div className="flex items-center gap-3">
                    <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary border border-primary/20">
                      <Building2 className="h-4 w-4" />
                    </div>
                    {tenant.name}
                  </div>
                </TableCell>
                <TableCell className="text-muted-foreground">{tenant.slug}</TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={
                      tenant.isActive !== false
                        ? "border-emerald-200 bg-emerald-50 text-emerald-700 shadow-xs"
                        : "border-destructive/20 bg-destructive/10 text-destructive shadow-xs"
                    }
                  >
                    {tenant.isActive !== false ? "Active" : "Suspended"}
                  </Badge>
                </TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-2">
                    <Button size="sm" variant="outline" className="shadow-xs hover:bg-muted" asChild>
                      <Link href={`/tenants/${tenant.id}/edit`}>
                        Edit
                      </Link>
                    </Button>
                    <AlertDialog>
                      <AlertDialogTrigger asChild>
                        <Button size="sm" variant="destructive" className="shadow-xs" disabled={tenant.isActive === false}>
                          Delete
                        </Button>
                      </AlertDialogTrigger>
                      <AlertDialogContent>
                        <AlertDialogHeader>
                          <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                          <AlertDialogDescription>
                            This will deactivate the tenant "{tenant.name}". Users in this tenant will no longer be able to log in or access their files.
                          </AlertDialogDescription>
                        </AlertDialogHeader>
                        <AlertDialogFooter>
                          <AlertDialogCancel>Cancel</AlertDialogCancel>
                          <AlertDialogAction
                            onClick={async () => {
                              try {
                                await deactivateTenant(tenant.id);
                                toast.success("Tenant deactivated successfully");
                                setTenants(tenants.map((t) => t.id === tenant.id ? { ...t, isActive: false } : t));
                              } catch (error: any) {
                                toast.error(error.response?.data?.message || "Failed to deactivate tenant");
                              }
                            }}
                            className="bg-red-600 hover:bg-red-700 focus:ring-red-600"
                          >
                            Yes, deactivate
                          </AlertDialogAction>
                        </AlertDialogFooter>
                      </AlertDialogContent>
                    </AlertDialog>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {shouldPaginate && (
        <div className="border-t border-border/50 bg-muted/10 px-4 py-3">
          <Pagination>
            <PaginationContent>
              <PaginationItem>
                <PaginationPrevious
                  href="#"
                  onClick={(event) => {
                    event.preventDefault();
                    setCurrentPage((page) => Math.max(1, page - 1));
                  }}
                  className={currentPage === 1 ? "pointer-events-none opacity-50" : ""}
                />
              </PaginationItem>

              {Array.from({ length: totalPages }, (_, index) => index + 1).map((page) => (
                <PaginationItem key={page}>
                  <PaginationLink
                    href="#"
                    isActive={page === currentPage}
                    onClick={(event) => {
                      event.preventDefault();
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
                  onClick={(event) => {
                    event.preventDefault();
                    setCurrentPage((page) => Math.min(totalPages, page + 1));
                  }}
                  className={currentPage === totalPages ? "pointer-events-none opacity-50" : ""}
                />
              </PaginationItem>
            </PaginationContent>
          </Pagination>
        </div>
        )}
      </div>
    </section>
  );
}
