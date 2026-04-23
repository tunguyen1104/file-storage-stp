"use client";

import React, { useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Menu } from "lucide-react";

import { DashboardSidebar } from "@/components/layout/sidebar";
import { HeaderUserNav } from "@/components/layout/header-user-nav";
import { ModeToggle } from "@/components/shared/ModeToggle";
import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb";
import { Button } from "@/components/ui/button";
import { Sheet, SheetContent, SheetTrigger } from "@/components/ui/sheet";
import { useAuth } from "@/hooks/use-auth";

const DEFAULT_ROUTE_BY_ROLE = {
  SUPER_ADMIN: "/tenants",
  TENANT_ADMIN: "/users",
  MEMBER: "/files",
} as const;

const ALLOWED_ROUTES_BY_ROLE: Record<string, string[]> = {
  SUPER_ADMIN: ["/tenants", "/users", "/files", "/search", "/profile"],
  TENANT_ADMIN: ["/users", "/files", "/search", "/profile"],
  MEMBER: ["/files", "/search", "/profile"],
};

function toTitle(value: string) {
  return value
    .replace(/-/g, " ")
    .replace(/\b\w/g, (char) => char.toUpperCase());
}

export default function DashboardLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  const pathname = usePathname();
  const router = useRouter();
  const { user, isLoading, logOut } = useAuth();
  const [isMobileSidebarOpen, setIsMobileSidebarOpen] = useState(false);

  useEffect(() => {
    if (isLoading) return;

    if (!user) {
      router.replace("/login");
      return;
    }

    const allowedRoutes = ALLOWED_ROUTES_BY_ROLE[user.role] ?? [];
    const isAllowed = allowedRoutes.some(
      (route) => pathname === route || pathname.startsWith(`${route}/`)
    );

    if (!isAllowed) {
      router.replace(DEFAULT_ROUTE_BY_ROLE[user.role]);
    }
  }, [isLoading, pathname, router, user]);

  const pathSegments = useMemo(() => {
    return pathname.split("/").filter(Boolean);
  }, [pathname]);

  if (isLoading || !user) {
    return <div className="min-h-screen bg-background" />;
  }

  return (
    <div className="min-h-screen bg-background text-foreground">
      <aside className="fixed inset-y-0 left-0 z-30 hidden w-64 border-r border-border bg-card md:block">
        <DashboardSidebar user={user} pathname={pathname} />
      </aside>

      <div className="flex min-h-screen flex-col md:pl-64">
        <header className="sticky top-0 z-20 flex h-16 items-center justify-between border-b border-border bg-card/80 backdrop-blur-md px-4 md:px-6">
          <div className="flex items-center gap-3">
            <Sheet open={isMobileSidebarOpen} onOpenChange={setIsMobileSidebarOpen}>
              <SheetTrigger asChild>
                <Button variant="outline" size="icon" className="md:hidden">
                  <Menu className="h-5 w-5" />
                </Button>
              </SheetTrigger>
              <SheetContent side="left" className="w-72 p-0">
                <DashboardSidebar
                  user={user}
                  pathname={pathname}
                  onNavigate={() => setIsMobileSidebarOpen(false)}
                />
              </SheetContent>
            </Sheet>

            <Breadcrumb>
              <BreadcrumbList>
                <BreadcrumbItem>
                  <BreadcrumbPage>Dashboard</BreadcrumbPage>
                </BreadcrumbItem>
                {pathSegments.map((segment) => (
                  <React.Fragment key={segment}>
                    <BreadcrumbSeparator />
                    <BreadcrumbItem>
                      <BreadcrumbPage>{toTitle(segment)}</BreadcrumbPage>
                    </BreadcrumbItem>
                  </React.Fragment>
                ))}
              </BreadcrumbList>
            </Breadcrumb>
          </div>

          <div className="flex items-center gap-3">
            <ModeToggle />
            <HeaderUserNav />
          </div>
        </header>

        <main className="flex-1 overflow-y-auto p-4 md:p-6">
          {children}
        </main>
      </div>
    </div>
  );
}
