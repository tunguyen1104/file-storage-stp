"use client";

import { useState, Suspense, useEffect } from "react";
import { useRouter, useSearchParams } from "next/navigation";
import Link from "next/link";
import { useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Loader2 } from "lucide-react";

import {
  resetPasswordSchema,
  type ResetPasswordInput,
} from "@/lib/schemas/user.schema";
import { resetPassword, verifyResetToken } from "@/lib/api/auth";
import { toAppError } from "@/lib/api/core/api-error";
import { PasswordInput } from "@/components/auth/password-input";
import { Button } from "@/components/ui/button";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import {
  Form,
  FormControl,
  FormField,
  FormItem,
  FormLabel,
  FormMessage,
} from "@/components/ui/form";

function ResetPasswordForm() {
  const router = useRouter();
  const searchParams = useSearchParams();
  const token = searchParams.get("token");
  const [error, setError] = useState<string | null>(null);
  const [tokenValid, setTokenValid] = useState<boolean | null>(null);

  const form = useForm<ResetPasswordInput>({
    resolver: zodResolver(resetPasswordSchema),
    defaultValues: { password: "", confirmPassword: "" },
  });

  // Verify the reset token on mount
  useEffect(() => {
    if (!token) {
      setTokenValid(false);
      return;
    }

    let cancelled = false;
    verifyResetToken(token)
      .then((res) => {
        if (!cancelled) setTokenValid(res.success);
      })
      .catch(() => {
        if (!cancelled) setTokenValid(false);
      });

    return () => {
      cancelled = true;
    };
  }, [token]);

  const onSubmit = async (data: ResetPasswordInput) => {
    if (!token) return;
    setError(null);
    try {
      const result = await resetPassword(token, data.password, data.confirmPassword);
      if (result.success) {
        localStorage.setItem("passwordResetComplete", Date.now().toString());
        router.push("/login?reset=success");
        return;
      }
      setError(result.message ?? "Failed to reset password.");
    } catch (err: unknown) {
      const appError = toAppError(err);
      if (appError.validationErrors) {
        for (const [field, messages] of Object.entries(appError.validationErrors)) {
          if (field === "password" || field === "newPassword") {
            form.setError("password", {
              message: Array.isArray(messages) ? messages[0] : messages,
            });
          } else if (field === "confirmPassword") {
            form.setError("confirmPassword", {
              message: Array.isArray(messages) ? messages[0] : messages,
            });
          }
        }
        return;
      }
      setError(appError.message || "Failed to reset password. The link may have expired.");
    }
  };

  if (tokenValid === null) {
    return (
      <div className="flex justify-center">
        <Loader2 className="w-10 h-10 animate-spin" />
      </div>
    );
  }

  if (tokenValid === false) {
    return (
      <Card className="w-full max-w-md shadow-lg">
        <CardContent className="pt-6 text-center space-y-4">
          <p className="font-medium">Invalid or expired reset link.</p>
          <Link href="/forgot-password" className="text-sm text-muted-foreground hover:underline">
            Request a new reset link
          </Link>
        </CardContent>
      </Card>
    );
  }

  return (
    <Card className="w-full max-w-md shadow-lg">
      <CardHeader className="text-center">
        <CardTitle className="text-2xl font-bold">Reset Password</CardTitle>
        <p className="text-sm text-muted-foreground">
          Enter your new password below.
        </p>
      </CardHeader>
      <CardContent>
        <Form {...form}>
          <form onSubmit={form.handleSubmit(onSubmit)} className="space-y-4">
            <FormField
              control={form.control}
              name="password"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>New Password</FormLabel>
                  <FormControl>
                    <PasswordInput
                      placeholder="••••••••"
                      autoComplete="new-password"
                      {...field}
                    />
                  </FormControl>
                  <FormMessage />
                </FormItem>
              )}
            />
            <FormField
              control={form.control}
              name="confirmPassword"
              render={({ field }) => (
                <FormItem>
                  <FormLabel>Confirm Password</FormLabel>
                  <FormControl>
                    <PasswordInput
                      placeholder="••••••••"
                      autoComplete="new-password"
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
              className="w-full"
              disabled={form.formState.isSubmitting}
            >
              {form.formState.isSubmitting ? (
                <>
                  <Loader2 className="w-4 h-4 mr-2 animate-spin" /> Resetting...
                </>
              ) : (
                "Reset Password"
              )}
            </Button>
          </form>
        </Form>
        <div className="mt-4 text-center text-sm">
          <Link href="/login" className="text-muted-foreground hover:underline">
            Back to sign in
          </Link>
        </div>
      </CardContent>
    </Card>
  );
}

export default function ResetPasswordPage() {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <Suspense
        fallback={
          <div className="flex justify-center">
            <Loader2 className="w-10 h-10 animate-spin" />
          </div>
        }
      >
        <ResetPasswordForm />
      </Suspense>
    </div>
  );
}
