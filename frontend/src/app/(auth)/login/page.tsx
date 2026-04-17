"use client";

import { useState, Suspense } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2, AlertCircle, Building2 } from "lucide-react";

import { loginSchema, type LoginInput } from "@/lib/schemas/user.schema";
import { useAuth } from "@/hooks/use-auth";
import type { ApiResponse } from "@/types/api.types";
import axios from "axios";
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
      // Try to extract backend validation errors and map to form fields
      if (axios.isAxiosError(err)) {
        const body = err.response?.data as ApiResponse | undefined;
        if (body && !body.success) {
          if (typeof body.errors === "object" && body.errors !== null) {
            // Field-level validation errors
            for (const [field, messages] of Object.entries(body.errors)) {
              if (field === "email" || field === "password") {
                form.setError(field, {
                  message: Array.isArray(messages) ? messages[0] : messages,
                });
              }
            }
            return;
          }
          if (typeof body.errors === "string") {
            setError(body.errors);
            return;
          }
        }
      }
      setError(
        err instanceof Error
          ? err.message
          : "Invalid email or password. Please try again."
      );
    }
  };

  return (
    <Card className="w-full max-w-md shadow-xl border-gray-200 bg-white sm:p-2">
      <CardHeader className="text-center space-y-2 pb-6">
        <div className="flex justify-center mb-3">
          <div className="p-3 bg-blue-50 rounded-full shadow-sm">
            <Building2 className="w-8 h-8 text-blue-600" />
          </div>
        </div>
        <CardTitle className="text-2xl sm:text-3xl font-bold tracking-tight text-gray-900">
          Sign In
        </CardTitle>
        <p className="text-sm text-gray-500">
          Access your storage workspace
        </p>
        
        {verified && (
          <Alert className="bg-green-50 text-green-700 border-green-200 mt-4 text-left">
            <AlertDescription>
              Email verified successfully! You can now sign in.
            </AlertDescription>
          </Alert>
        )}

        {resetSuccess && (
          <Alert className="bg-green-50 text-green-700 border-green-200 mt-4 text-left">
            <AlertDescription>
              Password has been reset successfully. Please log in.
            </AlertDescription>
          </Alert>
        )}
      </CardHeader>

      <CardContent>
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
                    <FormLabel className="text-gray-700 font-medium">Work Email</FormLabel>
                    <FormControl>
                      <Input
                        type="email"
                        placeholder="admin@tenant.com"
                        autoComplete="email"
                        className="bg-white border-gray-300 text-gray-900 placeholder:text-gray-400 focus-visible:ring-2 focus-visible:ring-blue-500 h-11"
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
                      <FormLabel className="text-gray-700 font-medium">Password</FormLabel>
                      <Link
                        href="/forgot-password"
                        className="text-sm font-semibold text-blue-600 hover:text-blue-700 hover:underline transition-colors"
                        tabIndex={-1}
                      >
                        Forgot password?
                      </Link>
                    </div>
                    <FormControl>
                      <PasswordInput
                        placeholder="••••••••"
                        autoComplete="current-password"
                        className="bg-white border-gray-300 text-gray-900 placeholder:text-gray-400 focus-visible:ring-2 focus-visible:ring-blue-500 h-11"
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
              className="w-full h-11 bg-blue-600 hover:bg-blue-700 text-white font-semibold text-base transition-colors shadow-sm"
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
      </CardContent>

      <CardFooter className="flex flex-col space-y-2 border-t border-gray-100 pt-6 mt-4 text-center text-xs text-gray-500 sm:text-sm">
        <p>Internal system for authorized users only</p>
        <p>Contact your Administrator to request an account</p>
      </CardFooter>
    </Card>
  );
}

export default function LoginPage() {
  return (
    <div className="min-h-screen flex items-center justify-center px-4 sm:px-6 lg:px-8 py-12 bg-gray selection:bg-blue-100 selection:text-blue-900">
      <Suspense
        fallback={
          <div className="flex flex-col items-center gap-3">
            <Loader2 className="w-10 h-10 animate-spin text-blue-600" />
            <p className="text-sm font-medium text-gray-500">Loading workspace...</p>
          </div>
        }
      >
        <LoginForm />
      </Suspense>
    </div>
  );
}