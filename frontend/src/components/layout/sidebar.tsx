"use client";

import Link from "next/link";
import { useSearchParams } from "next/navigation";
import {
  Building2,
  FolderOpen,
  Users,
  HardDrive,
  ChevronLeft,
  ChevronRight,
  LogOut,
  User,
  ChevronsUpDown,
  Shield,
  ClipboardList,
  Trash2,
  Share2,
} from "lucide-react";

import type { AuthUser } from "@/contexts/auth-context";
import { useAuth } from "@/hooks/use-auth";
import type { Role } from "@/lib/constants";
import { cn } from "@/lib/utils";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";

type NavItem = {
  href: string;
  label: string;
  icon: React.ComponentType<{ className?: string }>;
};

type NavSection = {
  items: NavItem[];
  separator?: boolean; // separator before this section
};

const ROLE_LABELS: Record<Role, string> = {
  SUPER_ADMIN: "Quản trị hệ thống",
  TENANT_ADMIN: "Quản trị tổ chức",
  MEMBER: "Thành viên",
};

const NAV_SECTIONS_BY_ROLE: Record<Role, NavSection[]> = {
  SUPER_ADMIN: [
    {
      items: [
        { href: "/tenants", label: "Quản lý tổ chức", icon: Building2 },
        { href: "/users", label: "Quản lý người dùng", icon: Users },
        { href: "/admin-files", label: "Quản lý tệp tin", icon: FolderOpen },
        { href: "/admin-shares", label: "Quản lý chia sẻ", icon: Share2 },
      ],
    },
    {
      separator: true,
      items: [
        { href: "/files", label: "Tài liệu của tôi", icon: HardDrive },
        { href: "/files?tab=trash", label: "Thùng rác", icon: Trash2 },
        { href: "/shared", label: "Được chia sẻ", icon: Share2 },
      ],
    },
  ],
  TENANT_ADMIN: [
    {
      items: [
        { href: "/users", label: "Thành viên nhóm", icon: Users },
        { href: "/admin-shares", label: "Quản lý chia sẻ", icon: Share2 },
        { href: "/audit-logs", label: "Nhật ký hoạt động", icon: ClipboardList },
      ],
    },
    {
      separator: true,
      items: [
        { href: "/files", label: "Tài liệu của tôi", icon: FolderOpen },
        { href: "/files?tab=trash", label: "Thùng rác", icon: Trash2 },
        { href: "/shared", label: "Được chia sẻ", icon: Share2 },
      ],
    },
  ],
  MEMBER: [
    {
      items: [
        { href: "/files", label: "Tài liệu của tôi", icon: FolderOpen },
        { href: "/files?tab=trash", label: "Thùng rác", icon: Trash2 },
        { href: "/shared", label: "Được chia sẻ", icon: Share2 },
      ],
    },
  ],
};

type DashboardSidebarProps = {
  user: AuthUser;
  pathname: string;
  onNavigate?: () => void;
  className?: string;
  isCollapsed?: boolean;
  onToggleCollapse?: () => void;
};

export function DashboardSidebar({
  user,
  pathname,
  onNavigate,
  className,
  isCollapsed = false,
  onToggleCollapse,
}: DashboardSidebarProps) {
  const { logOut } = useAuth();
  const searchParams = useSearchParams();
  const sections = NAV_SECTIONS_BY_ROLE[user.role] ?? [];

  function isItemActive(href: string): boolean {
    const base = href.split("?")[0];
    if (href.includes("?tab=")) {
      const tabValue = new URLSearchParams(href.slice(href.indexOf("?"))).get("tab");
      return pathname === base && searchParams.get("tab") === tabValue;
    }
    // Plain path: active if matches or is a sub-path, AND no tab param
    return (pathname === base || pathname.startsWith(`${base}/`)) && !searchParams.get("tab");
  }

  return (
    <div className={cn("flex h-full flex-col bg-white border-r border-gray-200 transition-all duration-300 relative", className)}>
      {/* Logo */}
      <div className={cn(
        "flex items-center py-6 px-6 relative",
        isCollapsed ? "justify-center px-0 pb-4" : "gap-2 pb-4"
      )}>
        <div className="flex items-center justify-center min-w-8 w-8 h-8 bg-[#7C3AED] rounded-lg text-white shrink-0">
          <HardDrive className="w-5 h-5" />
        </div>
        {!isCollapsed && (
          <span className="text-xl font-bold text-[#7C3AED] whitespace-nowrap overflow-hidden">
            DataFlow Pro
          </span>
        )}
      </div>

      {/* Role label + collapse toggle */}
      <div className={cn(
        "flex items-center mt-2 mb-4",
        isCollapsed ? "justify-center px-0" : "px-6 justify-between"
      )}>
        {!isCollapsed && (
          <span className="text-xs font-bold text-gray-500 tracking-wider whitespace-nowrap">
            {ROLE_LABELS[user.role] ?? user.role}
          </span>
        )}
        {onToggleCollapse && (
          <button
            onClick={onToggleCollapse}
            className={cn(
              "text-gray-400 hover:text-gray-600 focus:outline-none",
              isCollapsed && "mx-auto"
            )}
          >
            {isCollapsed ? <ChevronRight className="w-5 h-5" /> : <ChevronLeft className="w-5 h-5" />}
          </button>
        )}
      </div>

      {/* Navigation */}
      <nav className="flex-1 px-4 py-2 overflow-hidden space-y-1">
        {sections.map((section, sIdx) => (
          <div key={sIdx}>
            {/* Separator line before section (if applicable) */}
            {section.separator && !isCollapsed && (
              <div className="my-3 border-t border-gray-100" />
            )}
            {section.separator && isCollapsed && (
              <div className="my-3 border-t border-gray-100 mx-2" />
            )}

            {section.items.map((item) => {
              const isActive = isItemActive(item.href);
              const Icon = item.icon;

              return (
                <Link
                  key={item.href}
                  href={item.href}
                  onClick={onNavigate}
                  title={isCollapsed ? item.label : undefined}
                  className={cn(
                    "flex items-center rounded-lg px-3 py-2.5 text-sm font-medium transition whitespace-nowrap",
                    isCollapsed ? "justify-center px-0 h-10 w-10 mx-auto" : "gap-3",
                    isActive
                      ? "bg-[#F3E8FF] text-[#7C3AED]"
                      : "text-gray-600 hover:bg-[#F3E8FF]/50 hover:text-[#7C3AED]"
                  )}
                >
                  <Icon className="h-4 w-4 shrink-0" />
                  {!isCollapsed && <span>{item.label}</span>}
                </Link>
              );
            })}
          </div>
        ))}
      </nav>

      {/* Bottom user section */}
      <div className="p-4 border-t border-gray-200">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <div className={cn(
              "flex items-center w-full hover:bg-gray-50 rounded-lg cursor-pointer transition-colors focus:outline-none min-w-0",
              isCollapsed ? "justify-center p-0" : "justify-between p-2"
            )}>
              <div className="flex items-center gap-3 min-w-0 flex-1">
                <Avatar className="h-9 w-9 shadow-sm border border-gray-200 shrink-0">
                  <AvatarImage src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${user.email}`} />
                  <AvatarFallback>{user.name.charAt(0)}</AvatarFallback>
                </Avatar>
                {!isCollapsed && (
                  <div className="flex flex-col text-left overflow-hidden min-w-0 flex-1">
                    <span className="text-sm font-medium text-gray-900 leading-none truncate">{user.name}</span>
                    <span className="text-xs text-gray-500 mt-1 truncate">{user.email}</span>
                  </div>
                )}
              </div>
              {!isCollapsed && <ChevronsUpDown className="h-4 w-4 text-gray-400 shrink-0 ml-2" />}
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[200px]" sideOffset={12}>
            <DropdownMenuItem asChild className="cursor-pointer">
              <Link href="/profile" className="flex items-center gap-2 w-full">
                <User className="h-4 w-4 text-gray-500" />
                <span>Thông tin cá nhân</span>
              </Link>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="cursor-pointer gap-2" onClick={() => void logOut()}>
              <LogOut className="h-4 w-4 text-gray-500" />
              <span>Đăng xuất</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );
}
