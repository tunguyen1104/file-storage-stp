"use client";

import { useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, AlertCircle, Mail, Lock, Zap, HardDrive, ShieldCheck } from "lucide-react";

import { loginSchema, type LoginInput } from "@/lib/schemas/user.schema";
import { useAuth } from "@/hooks/use-auth";
import { toAppError } from "@/lib/api/core/api-error";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { Checkbox } from "@/components/ui/checkbox";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";
import { PasswordInput } from "@/components/auth/password-input";

function LoginForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const { signIn } = useAuth();
  const [error, setError] = useState<string | null>(null);
  
  const verified = searchParams.get("verified") === "true";
  const resetSuccess = searchParams.get("reset") === "success";

  const form = useForm<LoginInput>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const onSubmit = async (data: LoginInput) => {
    setError(null);
    try {
      const user = await signIn(data.email, data.password);
      if (user.role === "SUPER_ADMIN") {
        router.push("/tenants");
        return;
      }
      if (user.role === "TENANT_ADMIN") {
        router.push("/users");
        return;
      }
      router.push("/files");
    } catch (err: unknown) {
      const appError = toAppError(err);
      if (appError.validationErrors) {
        for (const [field, messages] of Object.entries(appError.validationErrors)) {
          if (field === "email" || field === "password") {
            form.setError(field as "email" | "password", {
              message: Array.isArray(messages) ? messages[0] : messages,
            });
          }
        }
        return;
      }
      setError(appError.message || "Invalid email or password. Please try again.");
    }
  };

  return (
    <div className="flex min-h-screen">
      {/* Left Column - Login Form */}
      <div className="flex flex-col w-full lg:w-1/2 p-8 sm:p-12 md:p-16 xl:p-24">
        {/* Header/Logo */}
        <div className="flex items-center justify-center mb-8">
          <div className="flex items-center gap-2">
            <div className="flex items-center justify-center w-10 h-10 bg-primary rounded-xl text-white">
              <HardDrive className="w-6 h-6" />
            </div>
            <span className="text-xl font-bold text-primary">DocuKeep</span>
          </div>
        </div>

        <div className="flex-1 flex flex-col justify-center max-w-[440px] w-full mx-auto">
          <div className="text-center mb-8">
            <h1 className="text-3xl font-bold text-slate-900 mb-3">Chào mừng trở lại</h1>
            <p className="text-slate-500 text-sm">Vui lòng nhập thông tin để truy cập tệp tin của bạn</p>
          </div>

          {verified && (
            <Alert className="bg-green-50 text-green-700 border-green-200 mb-6">
              <AlertDescription>
                Email verified successfully! You can now sign in.
              </AlertDescription>
            </Alert>
          )}

          {resetSuccess && (
            <Alert className="bg-green-50 text-green-700 border-green-200 mb-6">
              <AlertDescription>
                Password has been reset successfully. Please log in.
              </AlertDescription>
            </Alert>
          )}

          <div className="bg-white border border-slate-200 rounded-xl p-6 shadow-sm">
            <Form {...form}>
              <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
                {error && (
                  <Alert className="bg-red-50 text-red-700 border-red-200">
                    <AlertCircle className="h-4 w-4" />
                    <AlertDescription>{error}</AlertDescription>
                  </Alert>
                )}

                <div className="space-y-4">
                  <FormField
                    control={form.control}
                    name="email"
                    render={({ field }) => (
                      <FormItem>
                        <FormLabel className="text-sm font-semibold text-slate-700">Email</FormLabel>
                        <FormControl>
                          <div className="relative">
                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none">
                              <Mail className="h-5 w-5 text-gray-400" />
                            </div>
                            <Input
                              type="email"
                              placeholder="name@example.com"
                              autoComplete="email"
                              className="pl-10 h-11 border-gray-300 focus-visible:ring-primary"
                              {...field}
                            />
                          </div>
                        </FormControl>
                        <FormMessage className="text-red-500 text-xs" />
                      </FormItem>
                    )}
                  />

                  <FormField
                    control={form.control}
                    name="password"
                    render={({ field }) => (
                      <FormItem>
                        <div className="flex items-center justify-between">
                          <FormLabel className="text-sm font-semibold text-slate-700">Mật khẩu</FormLabel>
                          <Link
                            href="/forgot-password"
                            className="text-xs text-primary hover:underline"
                            tabIndex={-1}
                          >
                            Quên mật khẩu?
                          </Link>
                        </div>
                        <FormControl>
                          <div className="relative">
                            <div className="absolute inset-y-0 left-0 pl-3 flex items-center pointer-events-none z-10">
                              <Lock className="h-5 w-5 text-gray-400" />
                            </div>
                            <PasswordInput
                              placeholder="••••••••"
                              autoComplete="current-password"
                              className="h-11 border-gray-300 focus-visible:ring-primary !pl-10"
                              {...field}
                            />
                          </div>
                        </FormControl>
                        <FormMessage className="text-red-500 text-xs" />
                      </FormItem>
                    )}
                  />
                  
                  <div className="flex items-center space-x-2">
                    <Checkbox id="remember" className="border-gray-300 text-primary focus-visible:ring-primary" />
                    <label
                      htmlFor="remember"
                      className="text-sm font-medium leading-none peer-disabled:cursor-not-allowed peer-disabled:opacity-70 text-slate-600"
                    >
                      Ghi nhớ đăng nhập
                    </label>
                  </div>
                </div>

                <Button
                  type="submit"
                  className="w-full h-11 bg-primary hover:bg-primary/90 text-white font-semibold transition-all duration-300 rounded-lg mt-6 shadow-md hover:shadow-lg"
                  disabled={form.formState.isSubmitting}
                >
                  {form.formState.isSubmitting ? (
                    <>
                      <Loader2 className="w-5 h-5 mr-2 animate-spin" /> Đang đăng nhập...
                    </>
                  ) : (
                    "Đăng nhập"
                  )}
                </Button>
              </form>
            </Form>
          </div>
        </div>

        {/* Footer Links */}
        <div className="mt-auto pt-8 flex justify-center gap-6 text-sm text-slate-500 font-medium">
          <Link href="#" className="hover:text-slate-900 transition-colors">Hỗ trợ</Link>
          <Link href="#" className="hover:text-slate-900 transition-colors">Bảo mật</Link>
          <Link href="#" className="hover:text-slate-900 transition-colors">Điều khoản</Link>
        </div>
      </div>

      {/* Right Column - Purple Banner */}
      <div className="hidden lg:flex flex-col w-1/2 bg-primary p-16 text-white relative overflow-hidden flex-1">
        {/* Abstract Background Elements (Circles) */}
        <div className="absolute -top-[10%] -right-[10%] w-[50%] h-[50%] rounded-full border border-white/10" />
        <div className="absolute top-[20%] -right-[20%] w-[60%] h-[60%] rounded-full border border-white/10" />
        <div className="absolute -bottom-[20%] -left-[10%] w-[70%] h-[70%] rounded-full border border-white/10" />
        <div className="absolute bottom-[10%] right-[10%] w-[40%] h-[40%] rounded-full border border-white/10" />
        
        <div className="relative z-10 max-w-xl mx-auto flex flex-col justify-center h-full">
          <h2 className="text-4xl md:text-5xl font-bold mb-6 leading-tight">
            Lưu trữ thông minh.<br />
            Truy cập mọi nơi.
          </h2>
          <p className="text-white/80 text-lg mb-12 leading-relaxed">
            DocuKeep giúp doanh nghiệp và cá nhân quản lý tài liệu một cách khoa học, an toàn tuyệt đối trên nền tảng đám mây tiên tiến nhất.
          </p>

          <div className="space-y-6">
            <div className="bg-white rounded-2xl p-6 flex gap-4 items-start shadow-xl hover:shadow-2xl transition-all duration-300 transform hover:-translate-y-1">
              <div className="mt-1 bg-primary/10 p-3 rounded-xl">
                <Zap className="w-6 h-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold text-lg mb-1 text-slate-900">Tốc độ vượt trội</h3>
                <p className="text-slate-600 text-sm leading-relaxed">Tải lên và tải về tệp tin với tốc độ ánh sáng nhờ hạ tầng CDN toàn cầu của chúng tôi.</p>
              </div>
            </div>

            <div className="bg-white rounded-2xl p-6 flex gap-4 items-start shadow-xl hover:shadow-2xl transition-all duration-300 transform hover:-translate-y-1">
              <div className="mt-1 bg-primary/10 p-3 rounded-xl">
                <HardDrive className="w-6 h-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold text-lg mb-1 text-slate-900">Dung lượng không giới hạn</h3>
                <p className="text-slate-600 text-sm leading-relaxed">Mở rộng không gian lưu trữ của bạn bất cứ lúc nào với các gói cước linh hoạt.</p>
              </div>
            </div>

            <div className="bg-white rounded-2xl p-6 flex gap-4 items-start shadow-xl hover:shadow-2xl transition-all duration-300 transform hover:-translate-y-1">
              <div className="mt-1 bg-primary/10 p-3 rounded-xl">
                <ShieldCheck className="w-6 h-6 text-primary" />
              </div>
              <div>
                <h3 className="font-semibold text-lg mb-1 text-slate-900">Bảo mật đa lớp</h3>
                <p className="text-slate-600 text-sm leading-relaxed">Dữ liệu của bạn được mã hóa ngay từ khi rời khỏi thiết bị và bảo vệ bởi xác thực 2 lớp.</p>
              </div>
            </div>
          </div>
        </div>

        {/* Carousel Dots */}
        <div className="absolute bottom-8 left-0 w-full flex justify-center items-center z-10">
          <div className="flex items-center gap-2">
            <div className="h-[2px] w-16 bg-white/20"></div>
            <div className="w-2 h-2 rounded-full bg-white/50"></div>
            <div className="w-6 h-2 rounded-full bg-white"></div>
            <div className="w-2 h-2 rounded-full bg-white/50"></div>
            <div className="h-[2px] w-16 bg-white/20"></div>
          </div>
        </div>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-col items-center justify-center min-h-screen gap-3">
          <Loader2 className="w-10 h-10 animate-spin text-primary" />
          <p className="text-sm font-medium text-gray-500">Loading workspace...</p>
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}
