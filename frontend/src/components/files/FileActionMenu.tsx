import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuSeparator,
  DropdownMenuTrigger,
} from "@/components/ui/dropdown-menu";
import {
  MoreVertical,
  Download,
  Pencil,
  Share2,
  MoveRight,
  Info,
  Trash2,
  RotateCcw,
  Link,
} from "lucide-react";

export function FileActionMenu({
  onAction,
  item,
  isTrash = false,
  isViewOnly = false,
}: {
  onAction: (e: React.MouseEvent, action: string, item: any) => void;
  item: any;
  isTrash?: boolean;
  isViewOnly?: boolean;
}) {
  const isFolder = item.type === "folder" || !item.mimeType;
  const isActiveFile = !isFolder && item.status === "ACTIVE";

  const isPendingScanOrError =
    !isFolder &&
    (item.status === "PENDING" ||
      item.status === "SCANNING" ||
      item.status === "UPLOAD_FAILED" ||
      item.status === "INFECTED");

  return (
    <DropdownMenu>
      <DropdownMenuTrigger asChild onClick={(e) => e.stopPropagation()}>
        <button className="flex h-8 w-8 items-center justify-center rounded-full text-gray-400 hover:bg-gray-100 hover:text-gray-600 transition-colors cursor-pointer focus:outline-none">
          <MoreVertical className="h-[18px] w-[18px]" />
        </button>
      </DropdownMenuTrigger>
      <DropdownMenuContent
        align="end"
        className="w-56 rounded-lg py-1.5 shadow-xl border border-gray-200"
      >
        {isPendingScanOrError ? (
          <DropdownMenuItem
            onClick={(e) => onAction(e, "delete-permanent", item)}
            className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50 font-medium"
          >
            <Trash2 className="h-4 w-4" />
            Xóa vĩnh viễn
          </DropdownMenuItem>
        ) : isTrash ? (
          <>
            <DropdownMenuItem
              onClick={(e) => onAction(e, "restore", item)}
              className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
            >
              <RotateCcw className="h-4 w-4 text-gray-500" />
              Khôi phục
            </DropdownMenuItem>
            <DropdownMenuSeparator className="my-1" />
            <DropdownMenuItem
              onClick={(e) => onAction(e, "delete-permanent", item)}
              className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-red-600 focus:text-red-700 focus:bg-red-50"
            >
              <Trash2 className="h-4 w-4" />
              Xóa vĩnh viễn
            </DropdownMenuItem>
          </>
        ) : (
          <>
            {!isFolder && (
              <DropdownMenuItem
                onClick={(e) => onAction(e, "download", item)}
                className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
              >
                <Download className="h-4 w-4 text-gray-500" />
                Tải xuống
              </DropdownMenuItem>
            )}
            {!isViewOnly && (
              <DropdownMenuItem
                onClick={(e) => onAction(e, "rename", item)}
                className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
              >
                <Pencil className="h-4 w-4 text-gray-500" />
                Đổi tên
              </DropdownMenuItem>
            )}
            <DropdownMenuSeparator className="my-1" />
            {isActiveFile && (
              <DropdownMenuItem
                onClick={(e) => onAction(e, "copy-link", item)}
                className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
              >
                <Link className="h-4 w-4 text-gray-500" />
                Sao chép liên kết
              </DropdownMenuItem>
            )}
            {!isViewOnly && (
              <>
                <DropdownMenuItem
                  onClick={(e) => onAction(e, "share", item)}
                  className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                >
                  <Share2 className="h-4 w-4 text-gray-500" />
                  Chia sẻ
                </DropdownMenuItem>
                <DropdownMenuItem
                  onClick={(e) => onAction(e, "move", item)}
                  className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                >
                  <MoveRight className="h-4 w-4 text-gray-500" />
                  Di chuyển
                </DropdownMenuItem>
              </>
            )}
            <DropdownMenuItem
              onClick={(e) => onAction(e, "info", item)}
              className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
            >
              <Info className="h-4 w-4 text-gray-500" />
              {isFolder ? "Thông tin về thư mục" : "Thông tin về tệp"}
            </DropdownMenuItem>
            {!isViewOnly && (
              <>
                <DropdownMenuSeparator className="my-1" />
                <DropdownMenuItem
                  onClick={(e) => onAction(e, "delete", item)}
                  className="gap-3 px-4 py-2.5 cursor-pointer text-[13px] text-gray-700 focus:text-gray-900"
                >
                  <Trash2 className="h-4 w-4 text-gray-500" />
                  Chuyển vào thùng rác
                </DropdownMenuItem>
              </>
            )}
          </>
        )}
      </DropdownMenuContent>
    </DropdownMenu>
  );
}
