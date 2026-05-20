"use client";

import { useState } from "react";
import { useRouter } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { KeyRound, Loader2, Mail, ShieldCheck } from "lucide-react";

import {
  forgotPasswordSchema,
  type ForgotPasswordInput,
} from "@/lib/schemas/user.schema";
import { forgotPassword } from "@/lib/api/auth";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

export default function ForgotPasswordPage() {
  const router = useRouter();
  const [error, setError] = useState<string | null>(null);

  const form = useForm<ForgotPasswordInput>({
    resolver: zodResolver(forgotPasswordSchema),
    defaultValues: { email: "" },
  });

  const onSubmit = async (data: ForgotPasswordInput) => {
    setError(null);
    try {
      await forgotPassword(data.email);
      // Always redirect to check-email regardless of whether email exists (anti-enumeration)
      router.push(
        `/check-email?flow=reset&email=${encodeURIComponent(data.email)}`
      );
    } catch {
      setError("Không thể gửi liên kết đặt lại mật khẩu. Vui lòng thử lại.");
    }
  };

  return (
    <div className="relative min-h-screen overflow-hidden px-4 py-10">
      <div className="pointer-events-none absolute inset-0 bg-[radial-gradient(circle_at_top,theme(colors.primary/15),transparent_45%)]" />
      <div className="relative mx-auto flex min-h-[calc(100vh-5rem)] w-full max-w-5xl items-center justify-center">
        <div className="grid w-full gap-6 lg:grid-cols-2">
          <Card className="hidden border-border/70 bg-card/95 shadow-lg lg:block">
            <CardHeader className="space-y-4">
              <div className="flex h-12 w-12 items-center justify-center rounded-full border border-primary/30 bg-primary/10">
                <KeyRound className="h-6 w-6 text-primary" />
              </div>
              <CardTitle className="text-2xl font-bold tracking-tight">
                Khôi phục tài khoản an toàn
              </CardTitle>
              <p className="text-sm text-muted-foreground">
                Chúng tôi sẽ gửi một liên kết đặt lại mật khẩu đến email của bạn để thiết lập mật khẩu mới.
              </p>
            </CardHeader>
            <CardContent className="space-y-4 pb-8">
              <div className="flex items-start gap-3 text-sm text-muted-foreground">
                <Mail className="mt-0.5 h-4 w-4 text-primary" />
                <span>Sử dụng email công việc được liên kết với tài khoản tổ chức của bạn.</span>
              </div>
              <div className="flex items-start gap-3 text-sm text-muted-foreground">
                <ShieldCheck className="mt-0.5 h-4 w-4 text-primary" />
                <span>Liên kết khôi phục có giới hạn thời gian để đảm bảo bảo mật.</span>
              </div>
            </CardContent>
          </Card>

          <Card className="w-full border-border/70 bg-card shadow-xl">
            <CardHeader className="space-y-3 text-center">
              <div className="mx-auto flex h-12 w-12 items-center justify-center rounded-full border border-primary/30 bg-primary/10 lg:hidden">
                <KeyRound className="h-6 w-6 text-primary" />
              </div>
              <CardTitle className="text-2xl font-bold tracking-tight">
                Quên mật khẩu
              </CardTitle>
              <p className="text-sm text-muted-foreground">
                Nhập email tài khoản của bạn để nhận liên kết đặt lại mật khẩu.
              </p>
            </CardHeader>
            <CardContent>
              <Form {...form}>
                <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
                  <FormField
                    control={form.control}
                    name="email"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel>Email</FormLabel>
                        <FormControl>
                          <Input
                            type="email"
                            placeholder="email@example.com"
                            autoComplete="email"
                            className="h-11"
                            {...field}
                          />
                        </FormControl>
                        <FormMessage />
                      </FormItem>
                    )}
                  />
                  {error && <p className="text-sm text-destructive">{error}</p>}
                  <Button
                    type="submit"
                    className="h-11 w-full"
                    disabled={form.formState.isSubmitting}
                  >
                    {form.formState.isSubmitting ? (
                      <>
                        <Loader2 className="mr-2 h-4 w-4 animate-spin" /> Đang gửi...
                      </>
                    ) : (
                      "Gửi liên kết đặt lại mật khẩu"
                    )}
                  </Button>
                </form>
              </Form>
              <div className="mt-5 text-center text-sm">
                <Link href="/login" className="text-muted-foreground hover:underline">
                  Quay lại trang đăng nhập
                </Link>
              </div>
            </CardContent>
          </Card>
        </div>
      </div>
    </div>
  );
}
