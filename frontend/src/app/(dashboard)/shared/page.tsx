"use client";

import { useState, Suspense } from "react";
import { useSearchParams, useRouter } from "next/navigation";
import {
  Share2,
  Users,
  Clock,
  FolderOpen,
  FileImage,
  FileText,
  FileArchive,
  FileVideo,
  FileAudio,
  File as FileIcon,
  Folder as FolderIcon,
  Link2,
  Mail,
  MoreVertical,
  Eye,
  Download,
  Trash2,
  Copy,
  ExternalLink,
  Search,
  UserCircle2,
  ShieldCheck,
  ShieldOff,
} from "lucide-react";
import { cn } from "@/lib/utils";
import { Input } from "@/components/ui/input";
import { Badge } from "@/components/ui/badge";
import { Button } from "@/components/ui/button";
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import { Avatar, AvatarFallback, AvatarImage } from "@/components/ui/avatar";

// ─── Helpers ──────────────────────────────────────────────────────

function renderFileIcon(mimeType: string | null, size = "h-5 w-5") {
  if (!mimeType) return <FolderIcon className={cn(size, "text-blue-500 fill-blue-100")} />;
  if (mimeType.startsWith("image/")) return <FileImage className={cn(size, "text-sky-500")} />;
  if (mimeType.startsWith("video/")) return <FileVideo className={cn(size, "text-purple-500")} />;
  if (mimeType.startsWith("audio/")) return <FileAudio className={cn(size, "text-orange-500")} />;
  if (mimeType.includes("zip") || mimeType.includes("tar") || mimeType.includes("rar"))
    return <FileArchive className={cn(size, "text-amber-500")} />;
  if (mimeType.includes("pdf") || mimeType.includes("document") || mimeType.startsWith("text/"))
    return <FileText className={cn(size, "text-red-500")} />;
  return <FileIcon className={cn(size, "text-gray-400")} />;
}

function formatBytes(bytes: number): string {
  if (!bytes) return "—";
  if (bytes < 1024) return `${bytes} B`;
  if (bytes < 1024 * 1024) return `${(bytes / 1024).toFixed(1)} KB`;
  if (bytes < 1024 * 1024 * 1024) return `${(bytes / (1024 * 1024)).toFixed(1)} MB`;
  return `${(bytes / (1024 * 1024 * 1024)).toFixed(1)} GB`;
}

function timeAgo(dateStr: string): string {
  const diff = Date.now() - new Date(dateStr).getTime();
  const mins = Math.floor(diff / 60000);
  if (mins < 60) return `${mins}m ago`;
  const hrs = Math.floor(mins / 60);
  if (hrs < 24) return `${hrs}h ago`;
  const days = Math.floor(hrs / 24);
  if (days < 30) return `${days}d ago`;
  return new Date(dateStr).toLocaleDateString();
}

function getAvatar(name: string) {
  return `https://api.dicebear.com/7.x/initials/svg?seed=${encodeURIComponent(name)}&backgroundColor=7C3AED`;
}

// ─── Mock data ────────────────────────────────────────────────────

const SHARED_WITH_ME: SharedItem[] = [
  {
    id: "s1",
    name: "Q1 2025 Report.pdf",
    mimeType: "application/pdf",
    sizeBytes: 2400000,
    sharedBy: { name: "Alice Nguyen", email: "alice@example.com" },
    permission: "VIEW",
    sharedAt: new Date(Date.now() - 1000 * 60 * 45).toISOString(),
    type: "file",
  },
  {
    id: "s2",
    name: "Design Assets",
    mimeType: null,
    sizeBytes: 0,
    sharedBy: { name: "Bob Tran", email: "bob@example.com" },
    permission: "EDIT",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 3).toISOString(),
    type: "folder",
  },
  {
    id: "s3",
    name: "Product Demo.mp4",
    mimeType: "video/mp4",
    sizeBytes: 145000000,
    sharedBy: { name: "Carol Le", email: "carol@example.com" },
    permission: "VIEW",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 24).toISOString(),
    type: "file",
  },
  {
    id: "s4",
    name: "Team Photos",
    mimeType: null,
    sizeBytes: 0,
    sharedBy: { name: "David Pham", email: "david@example.com" },
    permission: "EDIT",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 48).toISOString(),
    type: "folder",
  },
  {
    id: "s5",
    name: "Budget Tracker.xlsx",
    mimeType: "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
    sizeBytes: 820000,
    sharedBy: { name: "Eve Hoang", email: "eve@example.com" },
    permission: "VIEW",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 72).toISOString(),
    type: "file",
  },
];

const MY_SHARES: MyShareItem[] = [
  {
    id: "m1",
    name: "Project Proposal.docx",
    mimeType: "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
    sizeBytes: 350000,
    sharedWith: [
      { name: "Alice Nguyen", email: "alice@example.com" },
      { name: "Bob Tran", email: "bob@example.com" },
    ],
    permission: "VIEW",
    sharedAt: new Date(Date.now() - 1000 * 60 * 30).toISOString(),
    type: "file",
    linkEnabled: true,
  },
  {
    id: "m2",
    name: "Marketing Materials",
    mimeType: null,
    sizeBytes: 0,
    sharedWith: [{ name: "Carol Le", email: "carol@example.com" }],
    permission: "EDIT",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 5).toISOString(),
    type: "folder",
    linkEnabled: false,
  },
  {
    id: "m3",
    name: "Annual Report 2024.pdf",
    mimeType: "application/pdf",
    sizeBytes: 5200000,
    sharedWith: [
      { name: "David Pham", email: "david@example.com" },
      { name: "Eve Hoang", email: "eve@example.com" },
      { name: "Frank Vo", email: "frank@example.com" },
    ],
    permission: "VIEW",
    sharedAt: new Date(Date.now() - 1000 * 60 * 60 * 26).toISOString(),
    type: "file",
    linkEnabled: true,
  },
];

// ─── Types ────────────────────────────────────────────────────────

interface SharedItem {
  id: string;
  name: string;
  mimeType: string | null;
  sizeBytes: number;
  sharedBy: { name: string; email: string };
  permission: "VIEW" | "EDIT";
  sharedAt: string;
  type: "file" | "folder";
}

interface MyShareItem {
  id: string;
  name: string;
  mimeType: string | null;
  sizeBytes: number;
  sharedWith: { name: string; email: string }[];
  permission: "VIEW" | "EDIT";
  sharedAt: string;
  type: "file" | "folder";
  linkEnabled: boolean;
}

// ─── Permission Badge ─────────────────────────────────────────────

function PermissionBadge({ permission }: { permission: "VIEW" | "EDIT" }) {
  return permission === "EDIT" ? (
    <span className="inline-flex items-center gap-1 rounded-full bg-emerald-50 border border-emerald-200 px-2 py-0.5 text-[11px] font-semibold text-emerald-700">
      <ShieldCheck className="h-3 w-3" />
      Can edit
    </span>
  ) : (
    <span className="inline-flex items-center gap-1 rounded-full bg-blue-50 border border-blue-200 px-2 py-0.5 text-[11px] font-semibold text-blue-600">
      <Eye className="h-3 w-3" />
      View only
    </span>
  );
}

// ─── Shared With Me Tab ───────────────────────────────────────────

function SharedWithMeTab({ search }: { search: string }) {
  const filtered = SHARED_WITH_ME.filter((i) =>
    i.name.toLowerCase().includes(search.toLowerCase()) ||
    i.sharedBy.name.toLowerCase().includes(search.toLowerCase())
  );

  if (filtered.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-20">
        <Share2 className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">
          {search ? `Không tìm thấy kết quả cho "${search}"` : "Chưa có tệp nào được chia sẻ với bạn."}
        </p>
      </div>
    );
  }

  return (
    <div className="overflow-hidden rounded-xl border border-gray-200 bg-white">
      <table className="w-full text-sm">
        <thead className="border-b border-gray-100 bg-gray-50">
          <tr>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 w-[38%]">Tên</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden md:table-cell w-[22%]">Chia sẻ bởi</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden sm:table-cell w-[15%]">Quyền</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 hidden sm:table-cell w-[12%]">Kích thước</th>
            <th className="px-4 py-3 text-left text-xs font-semibold text-gray-500 w-[10%]">Ngày nhận</th>
            <th className="w-10" />
          </tr>
        </thead>
        <tbody>
          {filtered.map((item) => (
            <tr
              key={item.id}
              className="group border-b border-gray-50 transition-colors hover:bg-gray-50 cursor-pointer"
            >
              <td className="px-4 py-3">
                <div className="flex items-center gap-2.5 min-w-0">
                  {renderFileIcon(item.mimeType, "h-5 w-5 shrink-0")}
                  <span className="truncate font-medium text-gray-800">{item.name}</span>
                  {item.type === "folder" && (
                    <Badge variant="secondary" className="text-[10px] py-0 px-1.5 shrink-0">Folder</Badge>
                  )}
                </div>
              </td>
              <td className="px-4 py-3 hidden md:table-cell">
                <div className="flex items-center gap-2">
                  <Avatar className="h-6 w-6 shrink-0">
                    <AvatarImage src={getAvatar(item.sharedBy.name)} />
                    <AvatarFallback className="text-[10px]">{item.sharedBy.name[0]}</AvatarFallback>
                  </Avatar>
                  <div className="min-w-0">
                    <p className="text-xs font-medium text-gray-700 truncate">{item.sharedBy.name}</p>
                    <p className="text-[11px] text-gray-400 truncate">{item.sharedBy.email}</p>
                  </div>
                </div>
              </td>
              <td className="px-4 py-3 hidden sm:table-cell">
                <PermissionBadge permission={item.permission} />
              </td>
              <td className="px-4 py-3 hidden sm:table-cell text-gray-400 tabular-nums text-xs">
                {formatBytes(item.sizeBytes)}
              </td>
              <td className="px-4 py-3 text-gray-400 text-xs whitespace-nowrap">
                {timeAgo(item.sharedAt)}
              </td>
              <td className="px-1 py-3">
                <DropdownMenu>
                  <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
                    <button className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100">
                      <MoreVertical className="h-4 w-4" />
                    </button>
                  </DropdownMenuTrigger>
                  <DropdownMenuContent align="end" className="w-48">
                    <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer">
                      <Eye className="h-4 w-4 text-gray-500" />
                      Xem
                    </DropdownMenuItem>
                    <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer">
                      <Download className="h-4 w-4 text-gray-500" />
                      Tải xuống
                    </DropdownMenuItem>
                    <DropdownMenuSeparator />
                    <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer text-red-600 focus:text-red-700 focus:bg-red-50">
                      <ShieldOff className="h-4 w-4" />
                      Rời khỏi chia sẻ
                    </DropdownMenuItem>
                  </DropdownMenuContent>
                </DropdownMenu>
              </td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}

// ─── My Shares Tab ────────────────────────────────────────────────

function MySharesTab({ search }: { search: string }) {
  const filtered = MY_SHARES.filter((i) =>
    i.name.toLowerCase().includes(search.toLowerCase())
  );

  if (filtered.length === 0) {
    return (
      <div className="flex flex-col items-center justify-center gap-3 rounded-xl border border-dashed border-gray-200 bg-white py-20">
        <Users className="h-10 w-10 text-gray-300" />
        <p className="text-sm text-gray-400">
          {search ? `Không tìm thấy kết quả cho "${search}"` : "Bạn chưa chia sẻ tệp nào."}
        </p>
      </div>
    );
  }

  return (
    <div className="space-y-3">
      {filtered.map((item) => (
        <div
          key={item.id}
          className="group rounded-xl border border-gray-200 bg-white p-4 hover:border-gray-300 hover:shadow-sm transition-all"
        >
          <div className="flex items-start gap-3">
            {/* Icon */}
            <div className="mt-0.5 shrink-0">
              {renderFileIcon(item.mimeType, "h-8 w-8")}
            </div>

            {/* Main info */}
            <div className="flex-1 min-w-0">
              <div className="flex items-center gap-2 flex-wrap">
                <span className="font-semibold text-gray-900 text-sm truncate">{item.name}</span>
                {item.type === "folder" && (
                  <Badge variant="secondary" className="text-[10px] py-0 px-1.5 shrink-0">Folder</Badge>
                )}
                <PermissionBadge permission={item.permission} />
                {item.linkEnabled && (
                  <span className="inline-flex items-center gap-1 rounded-full bg-violet-50 border border-violet-200 px-2 py-0.5 text-[11px] font-semibold text-violet-700">
                    <Link2 className="h-3 w-3" />
                    Link enabled
                  </span>
                )}
              </div>

              <div className="mt-1 flex items-center gap-3 text-xs text-gray-400">
                <span className="flex items-center gap-1">
                  <Clock className="h-3 w-3" />
                  {timeAgo(item.sharedAt)}
                </span>
                {item.sizeBytes > 0 && <span>{formatBytes(item.sizeBytes)}</span>}
              </div>

              {/* Shared with avatars */}
              <div className="mt-3 flex items-center gap-2">
                <div className="flex -space-x-2">
                  {item.sharedWith.slice(0, 4).map((u, idx) => (
                    <Avatar key={idx} className="h-7 w-7 border-2 border-white shadow-sm" title={u.name}>
                      <AvatarImage src={getAvatar(u.name)} />
                      <AvatarFallback className="text-[10px] bg-[#7C3AED] text-white">{u.name[0]}</AvatarFallback>
                    </Avatar>
                  ))}
                  {item.sharedWith.length > 4 && (
                    <div className="h-7 w-7 rounded-full border-2 border-white bg-gray-100 flex items-center justify-center text-[10px] font-semibold text-gray-500 shadow-sm">
                      +{item.sharedWith.length - 4}
                    </div>
                  )}
                </div>
                <div className="flex flex-wrap gap-1">
                  {item.sharedWith.slice(0, 2).map((u, idx) => (
                    <span key={idx} className="text-xs text-gray-500">{u.name}{idx < Math.min(1, item.sharedWith.length - 1) ? "," : ""}</span>
                  ))}
                  {item.sharedWith.length > 2 && (
                    <span className="text-xs text-gray-400">và {item.sharedWith.length - 2} người khác</span>
                  )}
                </div>
              </div>
            </div>

            {/* Actions */}
            <DropdownMenu>
              <DropdownMenuTrigger asChild>
                <button className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors opacity-0 group-hover:opacity-100 focus:opacity-100 shrink-0">
                  <MoreVertical className="h-4 w-4" />
                </button>
              </DropdownMenuTrigger>
              <DropdownMenuContent align="end" className="w-52">
                <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer">
                  <UserCircle2 className="h-4 w-4 text-gray-500" />
                  Quản lý người dùng
                </DropdownMenuItem>
                <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer">
                  <Copy className="h-4 w-4 text-gray-500" />
                  Sao chép liên kết
                </DropdownMenuItem>
                <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer">
                  <ExternalLink className="h-4 w-4 text-gray-500" />
                  Mở tệp
                </DropdownMenuItem>
                <DropdownMenuSeparator />
                <DropdownMenuItem className="gap-2 text-[13px] cursor-pointer text-red-600 focus:text-red-700 focus:bg-red-50">
                  <Trash2 className="h-4 w-4" />
                  Hủy chia sẻ tất cả
                </DropdownMenuItem>
              </DropdownMenuContent>
            </DropdownMenu>
          </div>
        </div>
      ))}
    </div>
  );
}

// ─── Invite / share mini-CTA card ────────────────────────────────

function ShareCTACard() {
  return (
    <div className="rounded-xl border border-dashed border-violet-300 bg-violet-50/60 p-5 flex items-center gap-4">
      <div className="h-10 w-10 rounded-full bg-[#7C3AED]/10 flex items-center justify-center shrink-0">
        <Mail className="h-5 w-5 text-[#7C3AED]" />
      </div>
      <div className="flex-1 min-w-0">
        <p className="text-sm font-semibold text-gray-800">Chia sẻ tệp hoặc thư mục</p>
        <p className="text-xs text-gray-500 mt-0.5">Gửi lời mời tới đồng nghiệp để cộng tác cùng nhau.</p>
      </div>
      <Button size="sm" className="bg-[#7C3AED] hover:bg-[#6D28D9] text-white gap-2 shrink-0">
        <Share2 className="h-4 w-4" />
        Chia sẻ ngay
      </Button>
    </div>
  );
}

// ─── Inner page ───────────────────────────────────────────────────

function SharedPageInner() {
  const searchParams = useSearchParams();
  const router = useRouter();
  const tab = (searchParams.get("view") as "received" | "sent") ?? "received";
  const [search, setSearch] = useState("");

  const isReceived = tab === "received";

  const STATS = [
    {
      label: "Được chia sẻ với tôi",
      value: SHARED_WITH_ME.length,
      icon: Share2,
      color: "bg-blue-50 text-blue-600",
    },
    {
      label: "Tôi đã chia sẻ",
      value: MY_SHARES.length,
      icon: Users,
      color: "bg-violet-50 text-violet-600",
    },
    {
      label: "Có quyền chỉnh sửa",
      value: SHARED_WITH_ME.filter((i) => i.permission === "EDIT").length,
      icon: ShieldCheck,
      color: "bg-emerald-50 text-emerald-600",
    },
  ];

  return (
    <div className="flex h-full flex-col overflow-hidden bg-gray-50">
      {/* ── Top bar ── */}
      <div className="flex items-center border-b border-gray-200 bg-white px-6 py-3 shrink-0">
        <div className="relative flex-1 max-w-lg">
          <Search className="pointer-events-none absolute left-3 top-1/2 h-4 w-4 -translate-y-1/2 text-gray-400" />
          <Input
            className="h-9 border-gray-200 bg-gray-50 pl-9 text-sm placeholder:text-gray-400 focus-visible:ring-[#7C3AED]/30"
            placeholder="Tìm kiếm tệp được chia sẻ..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
          />
        </div>
      </div>

      {/* ── Scrollable body ── */}
      <div className="flex-1 overflow-y-auto px-6 py-5 space-y-5 min-h-0">
        {/* Stats row */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
          {STATS.map((s) => {
            const Icon = s.icon;
            return (
              <div key={s.label} className="flex items-center gap-3 rounded-xl border border-gray-200 bg-white px-5 py-4">
                <div className={cn("flex h-10 w-10 items-center justify-center rounded-lg shrink-0", s.color)}>
                  <Icon className="h-5 w-5" />
                </div>
                <div>
                  <p className="text-2xl font-bold text-gray-900 leading-none">{s.value}</p>
                  <p className="text-xs text-gray-500 mt-0.5">{s.label}</p>
                </div>
              </div>
            );
          })}
        </div>

        {/* Share CTA */}
        <ShareCTACard />

        {/* Tabs */}
        <div className="flex flex-col gap-4">
          <div className="flex items-center gap-0.5 rounded-xl border border-gray-200 bg-white p-1 w-fit">
            <button
              onClick={() => router.replace("/shared?view=received")}
              className={cn(
                "flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-all",
                isReceived
                  ? "bg-[#7C3AED] text-white shadow-sm"
                  : "text-gray-500 hover:text-gray-700"
              )}
            >
              <Share2 className="h-4 w-4" />
              Được chia sẻ với tôi
              <span className={cn(
                "ml-1 rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                isReceived ? "bg-white/20 text-white" : "bg-gray-100 text-gray-500"
              )}>
                {SHARED_WITH_ME.length}
              </span>
            </button>
            <button
              onClick={() => router.replace("/shared?view=sent")}
              className={cn(
                "flex items-center gap-2 rounded-lg px-4 py-2 text-sm font-medium transition-all",
                !isReceived
                  ? "bg-[#7C3AED] text-white shadow-sm"
                  : "text-gray-500 hover:text-gray-700"
              )}
            >
              <Users className="h-4 w-4" />
              Tôi đã chia sẻ
              <span className={cn(
                "ml-1 rounded-full px-1.5 py-0.5 text-[10px] font-bold",
                !isReceived ? "bg-white/20 text-white" : "bg-gray-100 text-gray-500"
              )}>
                {MY_SHARES.length}
              </span>
            </button>
          </div>

          {/* Tab content */}
          {isReceived ? (
            <SharedWithMeTab search={search} />
          ) : (
            <MySharesTab search={search} />
          )}
        </div>
      </div>
    </div>
  );
}

// ─── Export ───────────────────────────────────────────────────────

export default function SharedPage() {
  return (
    <Suspense
      fallback={
        <div className="flex h-full items-center justify-center">
          <div className="h-8 w-8 animate-spin rounded-full border-4 border-[#7C3AED] border-t-transparent" />
        </div>
      }
    >
      <SharedPageInner />
    </Suspense>
  );
}
