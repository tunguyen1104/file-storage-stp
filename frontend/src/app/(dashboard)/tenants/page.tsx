"use client";

import { useEffect, useMemo, useState } from "react";
import { Building2, Plus, Search } from "lucide-react";

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

type Tenant = {
  id: string;
  name: string;
  domain: string;
  status: "Active" | "Pending" | "Suspended";
  createdDate: string;
};

const PAGE_SIZE = 12;
const PAGINATION_THRESHOLD = 12;

export default function TenantsPage() {
  const [currentPage, setCurrentPage] = useState(1);
  const [tenants, setTenants] = useState<Tenant[]>([]);

  useEffect(() => {
    let isMounted = true;

    fetch("/mock-data/tenants.json")
      .then((response) => response.json())
      .then((data: Tenant[]) => {
        if (!isMounted) return;
        setTenants(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (isMounted) setTenants([]);
      });

    return () => {
      isMounted = false;
    };
  }, []);

  const shouldPaginate = tenants.length >= PAGINATION_THRESHOLD;

  const totalPages = shouldPaginate ? Math.ceil(tenants.length / PAGE_SIZE) : 1;

  const paginatedTenants = useMemo(() => {
    if (!shouldPaginate) return tenants;
    const startIndex = (currentPage - 1) * PAGE_SIZE;
    return tenants.slice(startIndex, startIndex + PAGE_SIZE);
  }, [currentPage, shouldPaginate, tenants]);

  useEffect(() => {
    if (currentPage > totalPages) {
      setCurrentPage(1);
    }
  }, [currentPage, totalPages]);

  return (
    <section className="space-y-4">
      <div className="flex flex-col gap-3 rounded-lg border border-gray-200 bg-white p-4 md:flex-row md:items-center md:justify-between">
        <div className="relative w-full md:max-w-sm">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <Input className="h-10 bg-white pl-9" placeholder="Search tenant..." />
        </div>
        <Button className="gap-2">
          <Plus className="h-4 w-4" />
          Create New Tenant
        </Button>
      </div>

      <div className="rounded-lg border border-gray-200 bg-white">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Name</TableHead>
              <TableHead>Domain</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Created Date</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {paginatedTenants.map((tenant) => (
              <TableRow key={tenant.id} className="hover:bg-gray-50">
                <TableCell className="font-medium">
                  <div className="flex items-center gap-2">
                    <Building2 className="h-4 w-4 text-blue-600" />
                    {tenant.name}
                  </div>
                </TableCell>
                <TableCell>{tenant.domain}</TableCell>
                <TableCell>
                  <Badge
                    variant="outline"
                    className={
                      tenant.status === "Active"
                        ? "border-green-200 bg-green-50 text-green-700"
                        : tenant.status === "Pending"
                          ? "border-yellow-200 bg-yellow-50 text-yellow-700"
                          : "border-red-200 bg-red-50 text-red-700"
                    }
                  >
                    {tenant.status}
                  </Badge>
                </TableCell>
                <TableCell>{tenant.createdDate}</TableCell>
                <TableCell className="text-right">
                  <div className="flex justify-end gap-2">
                    <Button size="sm" variant="outline">
                      Edit
                    </Button>
                    <Button size="sm" variant="destructive">
                      Delete
                    </Button>
                  </div>
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>

        {shouldPaginate && (
        <div className="border-t border-gray-200 px-4 py-3">
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
