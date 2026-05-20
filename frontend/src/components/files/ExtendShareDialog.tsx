"use client";

import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import * as z from "zod";
import { Loader2, Clock, CalendarRange } from "lucide-react";
import { toast } from "sonner";

import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from "@/components/ui/dialog";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from "@/components/ui/select";
import { extendShare } from "@/lib/api/share";

const formSchema = z.object({
  duration: z.number().int().min(1, "Thời gian phải lớn hơn 0"),
  unit: z.enum(["minutes", "hours", "days"]),
});

type FormValues = z.infer<typeof formSchema>;

interface ExtendShareDialogProps {
  open: boolean;
  onOpenChange: (open: boolean) => void;
  shareId: string;
  resourceName?: string;
  onSuccess: () => void;
}

export function ExtendShareDialog({
  open,
  onOpenChange,
  shareId,
  resourceName,
  onSuccess,
}: ExtendShareDialogProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);

  const form = useForm<FormValues>({
    resolver: zodResolver(formSchema),
    defaultValues: {
      duration: 60,
      unit: "minutes",
    },
  });

  // Reset to default values whenever the dialog opens
  useEffect(() => {
    if (open) {
      form.reset({
        duration: 60,
        unit: "minutes",
      });
    }
  }, [open, form]);

  const presets = [
    { label: "30 phút", duration: 30, unit: "minutes" },
    { label: "1 giờ", duration: 1, unit: "hours" },
    { label: "4 giờ", duration: 4, unit: "hours" },
    { label: "1 ngày", duration: 1, unit: "days" },
    { label: "7 ngày", duration: 7, unit: "days" },
  ] as const;

  const handleApplyPreset = (duration: number, unit: "minutes" | "hours" | "days") => {
    form.setValue("duration", duration, { shouldValidate: true });
    form.setValue("unit", unit, { shouldValidate: true });
  };

  async function onSubmit(data: FormValues) {
    setIsSubmitting(true);
    let totalMinutes = data.duration;
    if (data.unit === "hours") {
      totalMinutes = data.duration * 60;
    } else if (data.unit === "days") {
      totalMinutes = data.duration * 24 * 60;
    }

    try {
      await extendShare(shareId, totalMinutes);
      toast.success("Gia hạn thời gian chia sẻ thành công");
      onSuccess();
      onOpenChange(false);
    } catch (error) {
      toast.error(
        error instanceof Error ? error.message : "Gia hạn chia sẻ thất bại"
      );
    } finally {
      setIsSubmitting(false);
    }
  }

  return (
    <Dialog open={open} onOpenChange={onOpenChange}>
      <DialogContent className="sm:max-w-[440px] rounded-xl border border-gray-100 bg-white p-6 shadow-2xl">
        <DialogHeader className="space-y-1">
          <DialogTitle className="flex items-center gap-2 text-lg font-bold text-gray-900">
            <Clock className="h-5 w-5 text-primary" />
            Gia hạn thời gian chia sẻ
          </DialogTitle>
          <DialogDescription className="text-gray-500 text-xs">
            {resourceName ? (
              <>Chọn hoặc nhập thời hạn muốn gia hạn thêm cho tài nguyên &ldquo;<span className="font-semibold text-gray-700">{resourceName}</span>&rdquo;.</>
            ) : (
              "Chọn hoặc nhập thời hạn muốn gia hạn thêm cho liên kết chia sẻ này."
            )}
          </DialogDescription>
        </DialogHeader>

        {/* Giao diện thông báo gia hạn */}
        <div className="mt-4 p-3.5 bg-amber-50/80 border border-amber-200/60 rounded-xl text-xs flex items-start gap-3 shadow-sm">
          <div className="p-1.5 rounded-lg bg-amber-100 text-amber-700 shrink-0 mt-0.5">
            <Clock className="h-4 w-4" />
          </div>
          <div className="space-y-1">
            <p className="font-bold text-amber-950">Thông báo gia hạn</p>
            <p className="text-amber-800/95 leading-relaxed">
              Thời gian mới sẽ được <strong>cộng dồn trực tiếp</strong> vào thời điểm hết hạn hiện tại. Liên kết chia sẻ sẽ duy trì trạng thái hoạt động liên tục mà không bị gián đoạn.
            </p>
          </div>
        </div>

        {/* Quick Presets */}
        <div className="mt-3">
          <label className="text-xs font-semibold text-gray-500 block mb-2">Gia hạn nhanh</label>
          <div className="flex flex-wrap gap-1.5">
            {presets.map((preset, idx) => {
              const currentDur = form.watch("duration");
              const currentUnit = form.watch("unit");
              const isActive = currentDur === preset.duration && currentUnit === preset.unit;
              return (
                <button
                  key={idx}
                  type="button"
                  onClick={() => handleApplyPreset(preset.duration, preset.unit)}
                  className={`px-3 py-1.5 rounded-lg text-xs font-bold transition-all border ${
                    isActive
                      ? "bg-primary text-white border-primary shadow-sm"
                      : "bg-gray-50 text-gray-600 border-gray-200 hover:bg-gray-100 hover:text-gray-900"
                  }`}
                >
                  {preset.label}
                </button>
              );
            })}
          </div>
        </div>

        <Form {...form}>
          <form
            onSubmit={form.handleSubmit(onSubmit)}
            className="space-y-5 mt-5"
          >
            <div className="flex gap-3">
              <FormField
                control={form.control}
                name="duration"
                render={({ field }) => (
                  <FormItem className="flex-1">
                    <FormLabel className="text-xs font-bold text-gray-600">Thời gian gia hạn</FormLabel>
                    <FormControl>
                      <Input
                        type="number"
                        min={1}
                        className="h-10 text-sm font-semibold"
                        {...field}
                        onChange={(e) => field.onChange(parseInt(e.target.value, 10) || 0)}
                      />
                    </FormControl>
                    <FormMessage />
                  </FormItem>
                )}
              />

              <FormField
                control={form.control}
                name="unit"
                render={({ field }) => (
                  <FormItem className="w-[120px]">
                    <FormLabel className="text-xs font-bold text-gray-600">Đơn vị</FormLabel>
                    <Select
                      onValueChange={field.onChange}
                      value={field.value}
                    >
                      <FormControl>
                        <SelectTrigger className="h-10 text-sm font-semibold">
                          <SelectValue placeholder="Chọn đơn vị" />
                        </SelectTrigger>
                      </FormControl>
                      <SelectContent>
                        <SelectItem value="minutes" className="text-sm font-semibold">Phút</SelectItem>
                        <SelectItem value="hours" className="text-sm font-semibold">Giờ</SelectItem>
                        <SelectItem value="days" className="text-sm font-semibold">Ngày</SelectItem>
                      </SelectContent>
                    </Select>
                    <FormMessage />
                  </FormItem>
                )}
              />
            </div>

            <DialogFooter className="mt-6 flex gap-2">
              <Button
                type="button"
                variant="outline"
                onClick={() => onOpenChange(false)}
                disabled={isSubmitting}
                className="flex-1 sm:flex-none text-xs font-bold h-9"
              >
                Hủy
              </Button>
              <Button
                type="submit"
                disabled={isSubmitting}
                className="flex-1 sm:flex-none text-xs font-bold h-9 bg-primary hover:bg-primary/95 text-white"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="mr-2 h-4 w-4 animate-spin" />
                    Đang xử lý
                  </>
                ) : (
                  <>
                    <CalendarRange className="mr-2 h-4 w-4" />
                    Xác nhận gia hạn
                  </>
                )}
              </Button>
            </DialogFooter>
          </form>
        </Form>
      </DialogContent>
    </Dialog>
  );
}
