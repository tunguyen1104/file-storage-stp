import { useState } from "react";
import { Loader2, ShieldAlert } from "lucide-react";
import { toast } from "sonner";
import { Dialog, DialogContent, DialogDescription, DialogFooter, DialogHeader, DialogTitle } from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { RadioGroup, RadioGroupItem } from "@/components/ui/radio-group";
import { Label } from "@/components/ui/label";
import { adminBulkRevoke } from "@/lib/api/share";
import type { UserResponse } from "@/types/user/user";

interface BulkRevokeSharesDialogProps {
  user: UserResponse | null;
  open: boolean;
  onOpenChange: (open: boolean) => void;
  onSuccess?: () => void;
}

export function BulkRevokeSharesDialog({
  user,
  open,
  onOpenChange,
  onSuccess,
}: BulkRevokeSharesDialogProps) {
  const [scope, setScope] = useState<"GRANTED" | "RECEIVED" | "BOTH">("BOTH");
  const [isSubmitting, setIsSubmitting] = useState(false);

  if (!user) return null;

  const handleSubmit = async () => {
    setIsSubmitting(true);
    try {
      const res = await adminBulkRevoke(user.id, scope);
      toast.success(
        `Đã thu hồi thành công ${res.revokedCount} liên kết chia sẻ của user ${user.fullName}!`
      );
      onOpenChange(false);
      if (onSuccess) onSuccess();
    } catch (err: any) {
      toast.error(err instanceof Error ? err.message : "Thu hồi liên kết chia sẻ thất bại");
    } finally {
      setIsSubmitting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-lg border border-red-100 bg-white/95 backdrop-blur-md shadow-2xl rounded-2xl overflow-hidden p-6 transition-all duration-300">
        <DialogHeader className="space-y-3">
          <div className="flex items-center gap-3">
            <div className="w-12 h-12 bg-red-50 rounded-full flex items-center justify-center text-red-500 animate-pulse shrink-0">
              <ShieldAlert className="w-6 h-6" />
            </div>
            <div>
              <DialogTitle className="text-xl font-bold text-gray-900 leading-none">
                Thu hồi liên kết chia sẻ hàng loạt
              </DialogTitle>
              <DialogDescription className="text-sm text-gray-500 mt-1.5">
                Thu hồi quyền truy cập/chia sẻ tài nguyên liên quan đến{" "}
                <span className="font-semibold text-gray-800">{user.fullName}</span> ({user.email})
              </DialogDescription>
            </div>
          </div>
        </DialogHeader>

        <div className="py-2 space-y-4">
          <div className="bg-amber-50/70 border border-amber-200/50 rounded-xl p-4 text-sm text-amber-800 space-y-1.5 shadow-sm">
            <h4 className="font-semibold flex items-center gap-1.5">
              Cảnh báo bảo mật quan trọng
            </h4>
            <p className="text-xs text-amber-700/90 leading-relaxed">
              Hành động này sẽ hủy bỏ ngay lập tức các quyền truy cập tài nguyên chia sẻ được chọn dưới đây. Đối tượng bị ảnh hưởng sẽ không thể truy cập được nữa.
            </p>
          </div>

          <div className="space-y-3">
            <Label className="text-sm font-bold text-gray-700">Chọn phạm vi thu hồi:</Label>
            <RadioGroup
              value={scope}
              onValueChange={(val) => setScope(val as "GRANTED" | "RECEIVED" | "BOTH")}
              className="grid gap-3"
            >
              {/* Option 1: GRANTED */}
              <div
                onClick={() => setScope("GRANTED")}
                className={`flex items-start gap-3 rounded-xl border p-4 cursor-pointer transition-all duration-200 hover:bg-gray-50 ${scope === "GRANTED"
                    ? "border-red-500 bg-red-50/20 shadow-sm"
                    : "border-gray-200 bg-white"
                  }`}
              >
                <RadioGroupItem value="GRANTED" id="scope-granted" className="mt-1" />
                <div className="space-y-1">
                  <Label htmlFor="scope-granted" className="font-semibold text-gray-900 cursor-pointer">
                    Quyền đã chia sẻ (GRANTED)
                  </Label>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    Thu hồi tất cả các liên kết chia sẻ do <span className="font-semibold">{user.fullName}</span> tạo ra. Những người dùng khác đang nhận tài nguyên từ user này sẽ bị mất quyền truy cập lập tức.
                  </p>
                </div>
              </div>

              {/* Option 2: RECEIVED */}
              <div
                onClick={() => setScope("RECEIVED")}
                className={`flex items-start gap-3 rounded-xl border p-4 cursor-pointer transition-all duration-200 hover:bg-gray-50 ${scope === "RECEIVED"
                    ? "border-red-500 bg-red-50/20 shadow-sm"
                    : "border-gray-200 bg-white"
                  }`}
              >
                <RadioGroupItem value="RECEIVED" id="scope-received" className="mt-1" />
                <div className="space-y-1">
                  <Label htmlFor="scope-received" className="font-semibold text-gray-900 cursor-pointer">
                    Quyền được nhận (RECEIVED)
                  </Label>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    Thu hồi tất cả các quyền chia sẻ mà <span className="font-semibold">{user.fullName}</span> được nhận từ người khác. User này sẽ mất quyền truy cập vào tất cả tài nguyên được share cho họ lập tức.
                  </p>
                </div>
              </div>

              {/* Option 3: BOTH */}
              <div
                onClick={() => setScope("BOTH")}
                className={`flex items-start gap-3 rounded-xl border p-4 cursor-pointer transition-all duration-200 hover:bg-gray-50 ${scope === "BOTH"
                    ? "border-red-500 bg-red-50/20 shadow-sm"
                    : "border-gray-200 bg-white"
                  }`}
              >
                <RadioGroupItem value="BOTH" id="scope-both" className="mt-1" />
                <div className="space-y-1">
                  <Label htmlFor="scope-both" className="font-semibold text-gray-900 cursor-pointer">
                    Cả hai chiều chia sẻ (BOTH)
                  </Label>
                  <p className="text-xs text-gray-500 leading-relaxed">
                    Hủy bỏ đồng thời cả quyền đã chia sẻ và quyền được nhận. Khuyên dùng khi tắt tài khoản hoặc offboard nhân viên hoàn toàn khỏi hệ thống.
                  </p>
                </div>
              </div>
            </RadioGroup>
          </div>
        </div>

        <DialogFooter className="gap-2 sm:gap-0 mt-4">
          <Button
            type="button"
            variant="outline"
            onClick={() => onOpenChange(false)}
            disabled={isSubmitting}
            className="rounded-xl border-gray-200 text-gray-700 hover:bg-gray-50 h-11 px-5"
          >
            Hủy bỏ
          </Button>
          <Button
            type="button"
            onClick={handleSubmit}
            disabled={isSubmitting}
            className="rounded-xl bg-red-600 hover:bg-red-700 text-white font-medium shadow-md shadow-red-100 hover:shadow-lg transition-all duration-200 h-11 px-5"
          >
            {isSubmitting ? (
              <>
                <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                Đang xử lý...
              </>
            ) : (
              "Xác nhận thu hồi"
            )}
          </Button>
        </DialogFooter>
      </DialogContent>
    </Dialog>
  );
}
