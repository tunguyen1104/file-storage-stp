"use client";

import { useState } from "react";
import { Download, Loader2 } from "lucide-react";

import { Button } from "@/components/ui/button";
import {
  AlertDialog,
  AlertDialogAction,
  AlertDialogCancel,
  AlertDialogContent,
  AlertDialogDescription,
  AlertDialogFooter,
  AlertDialogHeader,
  AlertDialogTitle,
} from "@/components/ui/alert-dialog";
import { buildAuditLogsExportUrl } from "@/lib/api/audit";
import type { ExportAuditLogsParams } from "@/types/audit/audit";

const EXPORT_CAP = 50_000;

type Props = {
  params: ExportAuditLogsParams;
  totalElements: number;
  disabled?: boolean;
};

export function AuditExportButton({ params, totalElements, disabled }: Props) {
  const [confirmOpen, setConfirmOpen] = useState(false);
  const [isWorking, setIsWorking] = useState(false);

  const hasNothing = totalElements <= 0;
  const exceedsCap = totalElements > EXPORT_CAP;

  function triggerDownload() {
    setIsWorking(true);
    try {
      window.location.href = buildAuditLogsExportUrl(params);
    } finally {
      window.setTimeout(() => setIsWorking(false), 1500);
    }
  }

  function handleClick() {
    if (hasNothing || disabled) return;
    if (exceedsCap) {
      setConfirmOpen(true);
      return;
    }
    triggerDownload();
  }

  const tooltip = hasNothing
    ? "Không có sự kiện nào để xuất"
    : `Xuất CSV (${totalElements.toLocaleString("vi-VN")} bản ghi)`;

  return (
    <>
      <Button
        type="button"
        variant="outline"
        className="h-10 gap-2"
        onClick={handleClick}
        disabled={hasNothing || disabled || isWorking}
        title={tooltip}
      >
        {isWorking ? (
          <Loader2 className="h-4 w-4 animate-spin" />
        ) : (
          <Download className="h-4 w-4" />
        )}
        Xuất CSV
      </Button>

      <AlertDialog open={confirmOpen} onOpenChange={setConfirmOpen}>
        <AlertDialogContent>
          <AlertDialogHeader>
            <AlertDialogTitle>Vượt quá hạn mức xuất file</AlertDialogTitle>
            <AlertDialogDescription>
              Bộ lọc hiện khớp{" "}
              <strong>{totalElements.toLocaleString("vi-VN")}</strong> sự kiện.
              CSV bị giới hạn ở <strong>50.000 dòng</strong> — các bản ghi cũ
              hơn sẽ bị cắt bớt mà không có thông báo. Hãy thu hẹp khoảng thời
              gian để xuất đầy đủ.
            </AlertDialogDescription>
          </AlertDialogHeader>
          <AlertDialogFooter>
            <AlertDialogCancel>Huỷ</AlertDialogCancel>
            <AlertDialogAction
              onClick={() => {
                setConfirmOpen(false);
                triggerDownload();
              }}
            >
              Tiếp tục xuất (cắt bớt)
            </AlertDialogAction>
          </AlertDialogFooter>
        </AlertDialogContent>
      </AlertDialog>
    </>
  );
}
