"use client";

import React, { useEffect, useMemo, useState } from "react";
import { usePathname, useRouter } from "next/navigation";
import { LogOut, Menu } from "lucide-react";

import { DashboardSidebar } from "@/components/layout/sidebar";
import { UploadProvider } from "@/contexts/upload-context";
import { UploadFloatingWidget } from "@/components/upload/UploadFloatingWidget";
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
import { cn } from "@/lib/utils";

const DEFAULT_ROUTE_BY_ROLE = {
  SUPER_ADMIN: "/tenants",
  TENANT_ADMIN: "/users",
  MEMBER: "/files",
} as const;

const ALLOWED_ROUTES_BY_ROLE: Record<string, string[]> = {
  SUPER_ADMIN: ["/tenants", "/users", "/files", "/admin-files", "/admin-shares", "/shared", "/search", "/audit-logs", "/profile"],
  TENANT_ADMIN: ["/users", "/files", "/admin-shares", "/shared", "/search", "/audit-logs", "/profile"],
  MEMBER: ["/files", "/shared", "/search", "/profile"],
};

const SEGMENT_TRANSLATIONS: Record<string, string> = {
  "files": "Tài liệu của tôi",
  "shared": "Được chia sẻ",
  "users": "Quản lý người dùng",
  "tenants": "Quản lý tổ chức",
  "admin-files": "Quản lý tệp tin",
  "admin-shares": "Quản lý chia sẻ",
  "audit-logs": "Nhật ký hoạt động",
  "profile": "Thông tin cá nhân",
  "security": "Bảo mật",
  "upload": "Tải lên",
  "search": "Tìm kiếm",
};

function getBreadcrumbLabel(segment: string): string {
  if (SEGMENT_TRANSLATIONS[segment.toLowerCase()]) {
    return SEGMENT_TRANSLATIONS[segment.toLowerCase()];
  }
  return segment
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
  const [isSidebarCollapsed, setIsSidebarCollapsed] = useState(false);

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
    return <div className="min-h-screen bg-gray-50" />;
  }

  return (
    <UploadProvider>
    <div className="flex h-screen overflow-hidden bg-gray-50 text-gray-900">
      <aside className={cn(
        "fixed inset-y-0 left-0 z-30 hidden border-r border-gray-200 bg-white md:flex md:flex-col transition-all duration-300",
        isSidebarCollapsed ? "w-[80px]" : "w-64"
      )}>
        <DashboardSidebar 
          user={user} 
          pathname={pathname} 
          isCollapsed={isSidebarCollapsed}
          onToggleCollapse={() => setIsSidebarCollapsed(!isSidebarCollapsed)}
        />
      </aside>

      <div className={cn(
        "flex flex-1 flex-col overflow-hidden transition-all duration-300",
        isSidebarCollapsed ? "md:pl-[80px]" : "md:pl-64"
      )}>
        <header className="sticky top-0 md:hidden z-20 flex h-16 items-center justify-between border-b border-gray-200 bg-white px-4 md:px-6">
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
                  <BreadcrumbPage>Hệ thống</BreadcrumbPage>
                </BreadcrumbItem>
                {pathSegments.map((segment) => (
                  <React.Fragment key={segment}>
                    <BreadcrumbSeparator />
                    <BreadcrumbItem>
                      <BreadcrumbPage>{getBreadcrumbLabel(segment)}</BreadcrumbPage>
                    </BreadcrumbItem>
                  </React.Fragment>
                ))}
              </BreadcrumbList>
            </Breadcrumb>
          </div>

          <Button
            variant="outline"
            className="gap-2"
            onClick={() => void logOut()}
          >
            <LogOut className="h-4 w-4" />
            Đăng xuất
          </Button>
        </header>

        <main className={cn(
          "flex-1 overflow-hidden",
          !pathname.startsWith("/files") && !pathname.startsWith("/shared") && "overflow-y-auto p-4 md:p-6"
        )}>
          {children}
        </main>
      </div>
    </div>
    <UploadFloatingWidget />
    </UploadProvider>
  );
}
