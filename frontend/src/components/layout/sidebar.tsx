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
    <div className={cn("flex h-full flex-col bg-card", className)}>
      <div className="flex items-center gap-3 border-b border-border px-4 py-5">
        <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-primary/10 text-primary shadow-sm border border-primary/20">
          <Shield className="h-5 w-5" />
        </div>
        <div>
          <p className="text-sm font-bold text-card-foreground">File Storage</p>
          <p className="text-xs font-medium text-muted-foreground">Enterprise Console</p>
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
                "flex items-center gap-3 rounded-lg px-3 py-2.5 text-sm font-medium transition-all duration-200",
                isActive
                  ? "bg-primary/10 text-primary shadow-sm"
                  : "text-muted-foreground hover:bg-muted hover:text-foreground"
              )}
            >
              <Icon className="h-4 w-4" />
              {item.label}
            </Link>
          );
        })}
      </nav>

    </div>
  );
}
