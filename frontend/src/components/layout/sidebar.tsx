"use client";

import Link from "next/link";
import {
  Building2,
  FolderOpen,
  Search,
  Shield,
  Users,
} from "lucide-react";

import type { AuthUser } from "@/contexts/auth-context";
import type { Role } from "@/lib/constants";
import { cn } from "@/lib/utils";

type NavItem = {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
};

const NAV_ITEMS_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { href: "/tenants", label: "Tenants", icon: Building2 },
    { href: "/users", label: "Users", icon: Users },
    { href: "/files", label: "Files", icon: FolderOpen },
    { href: "/search", label: "Search", icon: Search },
  ],
  TENANT_ADMIN: [
    { href: "/users", label: "Users", icon: Users },
    { href: "/files", label: "Files", icon: FolderOpen },
    { href: "/search", label: "Search", icon: Search },
  ],
  MEMBER: [
    { href: "/files", label: "Files", icon: FolderOpen },
    { href: "/search", label: "Search", icon: Search },
  ],
};

type DashboardSidebarProps = {
  user: AuthUser;
  pathname: string;
  onNavigate?: () => void;
  className?: string;
};

export function DashboardSidebar({
  user,
  pathname,
  onNavigate,
  className,
}: DashboardSidebarProps) {
  const navItems = NAV_ITEMS_BY_ROLE[user.role] ?? [];

  return (
    <div className={cn("flex h-full flex-col bg-white", className)}>
      <div className="flex items-center gap-3 border-b px-4 py-4">
        <div className="flex h-10 w-10 items-center justify-center rounded-lg bg-blue-100 text-blue-700">
          <Shield className="h-5 w-5" />
        </div>
        <div>
          <p className="text-sm font-semibold text-gray-900">File Storage</p>
          <p className="text-xs text-gray-500">Multi-tenant Console</p>
        </div>
      </div>

      <nav className="flex-1 space-y-1 px-3 py-4">
        {navItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
          const Icon = item.icon;

          return (
            <Link
              key={item.href}
              href={item.href}
              onClick={onNavigate}
              className={cn(
                "flex items-center gap-3 rounded-md px-3 py-2 text-sm transition",
                isActive
                  ? "bg-blue-50 text-blue-700"
                  : "text-gray-600 hover:bg-gray-100 hover:text-gray-900"
              )}
            >
              <Icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

      <div className="border-t p-4">
        <div className="flex items-center gap-3 rounded-lg bg-gray-50 p-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-full bg-blue-100 text-blue-700">
            {user.name.charAt(0)}
          </div>
          <div className="min-w-0">
            <p className="truncate text-sm font-medium text-gray-900">{user.name}</p>
            <p className="truncate text-xs uppercase tracking-wide text-gray-500">
              {user.role.replace("_", " ")}
            </p>
          </div>
        </div>
      </div>
    </div>
  );
}
