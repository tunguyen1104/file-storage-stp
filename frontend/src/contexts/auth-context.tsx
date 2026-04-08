"use client";

import React, {
  createContext,
  useCallback,
  useEffect,
  useMemo,
} from "react";
import useSWR from "swr";
import { usePathname, useRouter } from "next/navigation";
import { apiClient } from "@/lib/api-client";
import { API_ROUTES } from "@/lib/constants";
import type { Role } from "@/lib/constants";

export interface AuthUser {
  id: string;
  email: string;
  fullName: string;
  role: Role;
  tenantId: string;
  avatarUrl?: string;
}

interface AuthContextState {
  user: AuthUser | null;
  isLoading: boolean;
  isValidating: boolean;
  signIn: (email: string, password: string) => Promise<void>;
  logOut: () => Promise<void>;
  mutateUser: () => Promise<unknown>;
}

const meFetcher = async (url: string): Promise<AuthUser | null> => {
  try {
    const res = await apiClient.get(url);
    return res.data.data ?? null;
  } catch {
    return null;
  }
};

export const AuthContext = createContext<AuthContextState | undefined>(
  undefined
);

// Synced with PUBLIC_ROUTES in middleware.ts (#17)
const AUTH_PAGES = ["/login", "/forgot-password", "/verify", "/check-email", "/reset-password"];

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const pathname = usePathname();
  const router = useRouter();

  const shouldFetchUser = useMemo(() => {
    if (AUTH_PAGES.some((route) => pathname.startsWith(route))) return false;
    return true;
  }, [pathname]);

  const { data, isLoading, isValidating, mutate } = useSWR<AuthUser | null>(
    shouldFetchUser ? API_ROUTES.AUTH.ME : null,
    meFetcher,
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
      keepPreviousData: true,
    }
  );

  const signIn = useCallback(
    async (email: string, password: string) => {
      await apiClient.post(API_ROUTES.AUTH.LOGIN, {
        email,
        password,
      });
      // Backend sets httpOnly cookies via Set-Cookie header (forwarded by BFF)
      // No token in response body — browser stores cookies automatically
      await mutate();
      router.push("/files");
    },
    [mutate, router]
  );

  const logOut = useCallback(async () => {
    try {
      await apiClient.post(API_ROUTES.AUTH.LOGOUT);
    } catch {
      // Swallow — BFF clears httpOnly cookies server-side regardless
    } finally {
      await mutate(null, false);
      router.push("/login");
    }
  }, [mutate, router]);

  // Handle forced logout from interceptor
  useEffect(() => {
    const handleGlobalLogout = () => void logOut();
    window.addEventListener("auth:logout", handleGlobalLogout);
    return () => window.removeEventListener("auth:logout", handleGlobalLogout);
  }, [logOut]);

  const contextValue = useMemo(
    () => ({
      user: data ?? null,
      isLoading,
      isValidating,
      signIn,
      logOut,
      mutateUser: async () => mutate(),
    }),
    [data, isLoading, isValidating, signIn, logOut, mutate]
  );

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}