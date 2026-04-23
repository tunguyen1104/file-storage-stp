"use client";

import { useEffect, useState } from "react";
import { Plus, Search, User } from "lucide-react";

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
import { listUsers, deactivateUser, listTenantUsers } from "@/lib/api/users";
import { UserItem } from "@/types/user/user";
import Link from "next/link";
import { toast } from "sonner";
import { useAuth } from "@/hooks/use-auth";
import { Skeleton } from "@/components/ui/skeleton";

const PAGE_SIZE = 12;

export default function UsersPage() {
  const { user } = useAuth();
  const [currentPage, setCurrentPage] = useState(1);
  const [users, setUsers] = useState<UserItem[]>([]);
  const [totalPages, setTotalPages] = useState(1);
  const [isLoading, setIsLoading] = useState(true);

  useEffect(() => {
    if (!user) return;
    
    let isMounted = true;
    setIsLoading(true);

    const fetchMethod = user.role === "SUPER_ADMIN" ? listUsers : listTenantUsers;

    fetchMethod({ page: currentPage, limit: PAGE_SIZE })
      .then((data) => {
        if (!isMounted) return;
        setUsers(data.items || []);
        setTotalPages(data.totalPages || 1);
      })
      .catch(() => {
        if (!isMounted) return;
        setUsers([]);
        setTotalPages(1);
      })
      .finally(() => {
        if (isMounted) setIsLoading(false);
      });

    return () => {
      isMounted = false;
    };
  }, [currentPage, user]);

  const shouldPaginate = totalPages > 1;

  return (
    <section className="space-y-6">
      <div className="flex flex-col gap-4 rounded-xl border border-border/50 bg-card p-4 shadow-sm md:flex-row md:items-center md:justify-between">
        <div className="relative w-full md:max-w-md">
          <Search className="pointer-events-none absolute left-3.5 top-1/2 h-4 w-4 -translate-y-1/2 text-muted-foreground" />
          <Input className="h-10 bg-muted/50 pl-10 border-border/50 focus-visible:ring-primary/30 transition-all rounded-lg" placeholder="Search users..." />
        </div>
        <Button className="gap-2 shadow-sm" asChild>
          <Link href="/users/create">
            <Plus className="h-4 w-4" />
            Invite New User
          </Link>
        </Button>
      </div>

      <div className="rounded-xl border border-border/50 bg-card shadow-sm overflow-hidden">
        <Table>
          <TableHeader className="bg-muted/30">
            <TableRow className="border-border/50">
              <TableHead>Name</TableHead>
              <TableHead>Email</TableHead>
              <TableHead>Role</TableHead>
              <TableHead>Last Login</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {isLoading ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center">
                  <div className="flex flex-col items-center justify-center space-y-3">
                    <Skeleton className="h-6 w-3/4 max-w-[200px]" />
                    <Skeleton className="h-4 w-1/2 max-w-[150px]" />
                  </div>
                </TableCell>
              </TableRow>
            ) : users.length === 0 ? (
              <TableRow>
                <TableCell colSpan={5} className="py-8 text-center text-muted-foreground">
                  No users found.
                </TableCell>
              </TableRow>
            ) : (
              users.map((member) => (
                <TableRow key={member.id} className="border-border/50 hover:bg-muted/40 transition-colors">
                  <TableCell className="font-medium text-card-foreground">
                    <div className="flex items-center gap-3">
                      <div className="flex h-8 w-8 items-center justify-center rounded-full bg-primary/10 text-primary border border-primary/20">
                        <User className="h-4 w-4" />
                      </div>
                      {member.fullName}
                    </div>
                  </TableCell>
                  <TableCell>{member.email}</TableCell>
                  <TableCell>
                    <Badge variant={(typeof member.role === 'string' ? member.role : member.role.code) === "SUPER_ADMIN" ? "default" : "secondary"}>
                      {typeof member.role === 'string' ? member.role : member.role.name}
                    </Badge>
                  </TableCell>
                  <TableCell className="text-muted-foreground">{member.lastLoginAt || "-"}</TableCell>
                  <TableCell className="text-right">
                    <div className="flex justify-end gap-2">
                      <Button size="sm" variant="outline" className="shadow-xs hover:bg-muted" asChild>
                        <Link href={`/users/${member.id}/edit`}>
                          Edit
                        </Link>
                      </Button>
                      <AlertDialog>
                        <AlertDialogTrigger asChild>
                          <Button size="sm" variant="destructive" className="shadow-xs" disabled={member.isActive === false || member.id === user?.id}>
                            Delete
                          </Button>
                        </AlertDialogTrigger>
                        <AlertDialogContent>
                          <AlertDialogHeader>
                            <AlertDialogTitle>Are you absolutely sure?</AlertDialogTitle>
                            <AlertDialogDescription>
                              This will deactivate the user "{member.fullName}". They will no longer be able to log in or access their files.
                            </AlertDialogDescription>
                          </AlertDialogHeader>
                          <AlertDialogFooter>
                            <AlertDialogCancel>Cancel</AlertDialogCancel>
                            <AlertDialogAction
                              onClick={async () => {
                                try {
                                  await deactivateUser(member.id);
                                  toast.success("User deactivated successfully");
                                  setUsers(users.map((u) => u.id === member.id ? { ...u, isActive: false } : u));
                                } catch (error: any) {
                                  toast.error(error.response?.data?.message || "Failed to deactivate user");
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
              ))
            )}
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
