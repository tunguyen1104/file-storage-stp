"use client";

import { useRouter, useSearchParams } from "next/navigation";
import { Card, CardContent, CardHeader, CardTitle } from "@/components/ui/card";
import { Button } from "@/components/ui/button";
import { Loader2, Mail } from "lucide-react";
import { Suspense, useEffect, useState } from "react";
import { maskEmail } from "@/lib/auth";
import { forgotPassword } from "@/lib/api/auth";

const RESEND_COOLDOWN_SECONDS = 60;
const REDIRECT_DELAY_MS = 1500;

function CheckEmailForm() {
  const searchParams = useSearchParams();
  const email = searchParams.get("email");
  const flow = searchParams.get("flow") === "reset" ? "reset" : "verify";
  const router = useRouter();
  const [loading, setLoading] = useState(false);
  const [cooldown, setCooldown] = useState(0);
  const [redirecting, setRedirecting] = useState(false);

  // #6: Cooldown timer to prevent resend spam
  useEffect(() => {
    if (cooldown <= 0) return;
    const timer = setInterval(() => {
      setCooldown((c) => (c <= 1 ? 0 : c - 1));
    }, 1000);
    return () => clearInterval(timer);
  }, [cooldown]);

  const triggerRedirect = () => {
    setRedirecting(true);
    localStorage.removeItem("passwordResetComplete");
    const timer = setTimeout(() => {
      router.push("/login?reset=success");
    }, REDIRECT_DELAY_MS);
    return timer;
  };

  // Redirect to login when password reset completes in another tab
  useEffect(() => {
    if (flow !== "reset") return;
    const handleStorage = (e: StorageEvent) => {
      if (e.key === "passwordResetComplete") {
        triggerRedirect();
      }
    };
    window.addEventListener("storage", handleStorage);
    return () => window.removeEventListener("storage", handleStorage);
  // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [flow]);

  const handleResend = async () => {
    if (!email || cooldown > 0) return;
    setLoading(true);

    try {
      await forgotPassword(email);
      setCooldown(RESEND_COOLDOWN_SECONDS);
    } catch {
      // Silent fail — user sees no change
    } finally {
      setLoading(false);
    }
  };

  if (redirecting) {
    return (
      <Card className="w-full max-w-md shadow-lg">
        <CardContent className="pt-8 pb-8 flex flex-col items-center gap-3">
          <Loader2 className="w-8 h-8 animate-spin text-muted-foreground" />
          <p className="text-sm text-muted-foreground">
            Password reset successful. Redirecting to sign in…
          </p>
        </CardContent>
      </Card>
    );
  }

  // #12: Mask email to prevent phishing via crafted URLs
  const displayEmail = email ? maskEmail(email) : "your email";
  const headline = flow === "reset" ? "Reset link sent" : "Check your email";
  const helperText =
    flow === "reset"
      ? "We’ve sent a password reset link to:"
      : "We’ve sent a verification link to:";
  const resendLabel =
    flow === "reset" ? "Resend reset email" : "Resend verification email";

  return (
    <Card className="w-full max-w-md shadow-lg">
      <CardHeader className="text-center">
        <div className="flex justify-center mb-2">
          <Mail className="w-10 h-10" />
        </div>
        <CardTitle className="text-2xl font-bold">{headline}</CardTitle>
        <p className="text-sm text-muted-foreground mt-1">
          {helperText}
          <br />
          <span className="font-medium text-foreground">{displayEmail}</span>
        </p>
      </CardHeader>

      <CardContent className="text-center space-y-6">
        <p className="text-sm text-muted-foreground">
          Didn&apos;t receive it? Check your spam or junk folder.
        </p>

        <Button
          className="w-full"
          disabled={loading || cooldown > 0}
          onClick={handleResend}
          variant="outline"
        >
          {loading ? (
            <>
              <Loader2 className="w-4 h-4 mr-2 animate-spin" /> Resending...
            </>
          ) : cooldown > 0 ? (
            `Resend in ${cooldown}s`
          ) : (
            resendLabel
          )}
        </Button>

        <Button
          className="w-full"
          variant="link"
          onClick={() => router.push("/login")}
        >
          Back to sign in
        </Button>
      </CardContent>
    </Card>
  );
}

export default function CheckEmailPage() {
  return (
    <div className="min-h-screen flex items-center justify-center px-4">
      <Suspense
        fallback={
          <div className="flex justify-center">
            <Loader2 className="w-10 h-10 animate-spin" />
          </div>
        }
      >
        <CheckEmailForm />
      </Suspense>
    </div>
  );
}
