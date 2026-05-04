"use client";

import Link from "next/link";
import {
  Building2,
  FolderOpen,
  Search,
  Users,
  HardDrive,
  ChevronLeft,
  ChevronRight,
  LogOut,
  User,
  ChevronsUpDown,
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

const NAV_ITEMS_BY_ROLE: Record<Role, NavItem[]> = {
  SUPER_ADMIN: [
    { href: "/tenants", label: "Tenant Management", icon: Users },
    { href: "/users", label: "User Management", icon: HardDrive },
    { href: "/files", label: "File Management", icon: FolderOpen },
  ],
  TENANT_ADMIN: [
    { href: "/users", label: "User Management", icon: Users },
    { href: "/files", label: "File Management", icon: FolderOpen },
  ],
  MEMBER: [
    { href: "/files", label: "File Management", icon: FolderOpen },
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
  const navItems = NAV_ITEMS_BY_ROLE[user.role] ?? [];

  return (
    <div className={cn("flex h-full flex-col bg-white border-r border-gray-200 transition-all duration-300 relative", className)}>
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

      <div className={cn(
        "flex items-center mt-2 mb-4",
        isCollapsed ? "justify-center px-0" : "px-6 justify-between"
      )}>
        {!isCollapsed && (
          <span className="text-xs font-bold text-gray-500 tracking-wider whitespace-nowrap">
            {user.role.replace("_", " ")}
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

      <nav className="flex-1 space-y-1 px-4 py-2 overflow-hidden">
        {navItems.map((item) => {
          const isActive = pathname === item.href || pathname.startsWith(`${item.href}/`);
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
                  : "text-[#7C3AED] hover:bg-[#F3E8FF]/50"
              )}
            >
              <Icon className="h-4 w-4 shrink-0" />
              {!isCollapsed && <span>{item.label}</span>}
            </Link>
          );
        })}
      </nav>

      <div className="p-4 border-t border-gray-200">
        <DropdownMenu>
          <DropdownMenuTrigger asChild>
            <div className={cn(
              "flex items-center w-full hover:bg-gray-50 rounded-lg cursor-pointer transition-colors focus:outline-none",
              isCollapsed ? "justify-center p-0" : "justify-between p-2"
            )}>
              <div className="flex items-center gap-3">
                <Avatar className="h-9 w-9 shadow-sm border border-gray-200 shrink-0">
                  <AvatarImage src={`https://api.dicebear.com/7.x/avataaars/svg?seed=${user.email}`} />
                  <AvatarFallback>{user.name.charAt(0)}</AvatarFallback>
                </Avatar>
                {!isCollapsed && (
                  <div className="flex flex-col text-left overflow-hidden">
                    <span className="text-sm font-medium text-gray-900 leading-none truncate">{user.name}</span>
                    <span className="text-xs text-gray-500 mt-1 truncate">{user.email}</span>
                  </div>
                )}
              </div>
              {!isCollapsed && <ChevronsUpDown className="h-4 w-4 text-gray-400 shrink-0" />}
            </div>
          </DropdownMenuTrigger>
          <DropdownMenuContent align="end" className="w-[200px]" sideOffset={12}>
            <DropdownMenuItem className="cursor-pointer gap-2">
              <User className="h-4 w-4 text-gray-500" />
              <span>Profile</span>
            </DropdownMenuItem>
            <DropdownMenuSeparator />
            <DropdownMenuItem className="cursor-pointer gap-2" onClick={() => void logOut()}>
              <LogOut className="h-4 w-4 text-gray-500" />
              <span>Log out</span>
            </DropdownMenuItem>
          </DropdownMenuContent>
        </DropdownMenu>
      </div>
    </div>
  );
}
