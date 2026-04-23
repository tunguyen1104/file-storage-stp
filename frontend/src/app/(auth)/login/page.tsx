"use client";

import { useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, AlertCircle } from "lucide-react";

import { loginSchema, type LoginInput } from "@/lib/schemas/user.schema";
import { useAuth } from "@/hooks/use-auth";
import { toAppError } from "@/lib/api/core/api-error";
import { PasswordInput } from "@/components/auth/password-input";
import { Button } from "@/components/ui/button";
import { Input } from "@/components/ui/input";
import { Alert, AlertDescription } from "@/components/ui/alert";
import { 
  Card, 
  CardContent, 
  CardHeader, 
  CardTitle,
  CardFooter 
} from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

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
    <div className="w-full">
      <div className="text-center space-y-3 pb-8">
        <h1 className="text-3xl sm:text-4xl font-bold tracking-tight text-foreground">
          Welcome back
        </h1>
        <p className="text-base text-muted-foreground">
          Sign in to your account to continue
        </p>
        
        {verified && (
          <Alert className="bg-emerald-500/10 text-emerald-700 dark:text-emerald-300 border-emerald-500/30 mt-4 text-left">
            <AlertDescription>
              Email verified successfully! You can now sign in.
            </AlertDescription>
          </Alert>
        )}

        {resetSuccess && (
          <Alert className="bg-emerald-50 text-emerald-700 border-emerald-200 mt-4 text-left">
            <AlertDescription>
              Password has been reset successfully. Please log in.
            </AlertDescription>
          </Alert>
        )}
      </div>

      <div>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-5">
            {error && (
              <Alert className="bg-red-50 text-red-700 border-red-200 text-left">
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
                    <FormLabel className="font-semibold text-foreground">Work Email</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="admin@tenant.com"
                        autoComplete="email"
                        className="bg-muted/50 border-border/50 text-foreground placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-primary/30 transition-all h-11 rounded-lg"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage className="text-red-600 text-xs" />
                  </FormItem>
                )}
              />
              <FormField
                control={form.control}
                name="password"
                render={({ field }) => (
                  <FormItem>
                    <div className="flex items-center justify-between">
                      <FormLabel className="font-semibold text-foreground">Password</FormLabel>
                      <Link
                        href="/forgot-password"
                        className="text-sm font-semibold text-primary hover:text-primary/80 transition-colors"
                        tabIndex={-1}
                      >
                        Forgot password?
                      </Link>
                    </div>
                    <FormControl>
                      <PasswordInput
                        placeholder="••••••••"
                        autoComplete="current-password"
                        className="bg-muted/50 border-border/50 text-foreground placeholder:text-muted-foreground focus-visible:ring-2 focus-visible:ring-primary/30 transition-all h-11 rounded-lg"
                        {...field}
                      />
                    </FormControl>
                    <FormMessage className="text-red-600 text-xs" />
                  </FormItem>
                )}
              />
            </div>

            <Button
              type="submit"
              className="w-full h-11 shadow-md transition-all text-base rounded-lg"
              disabled={form.formState.isSubmitting}
            >
              {form.formState.isSubmitting ? (
                <>
                  <Loader2 className="w-5 h-5 mr-2 animate-spin" /> Signing in...
                </>
              ) : (
                "Sign In"
              )}
            </Button>
          </form>
        </Form>
      </div>
    </div>
  );
}

export default function LoginPage() {
  return (
    <Suspense
      fallback={
        <div className="flex flex-col items-center justify-center gap-3 w-full py-24">
          <Loader2 className="w-10 h-10 animate-spin text-primary" />
          <p className="text-sm font-medium text-muted-foreground">Loading workspace...</p>
        </div>
      }
    >
      <LoginForm />
    </Suspense>
  );
}