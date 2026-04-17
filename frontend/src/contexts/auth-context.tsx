"use client";

import React, {
  createContext,
  useCallback,
  useEffect,
  useMemo,
} from "react";
import { useRouter, usePathname } from "next/navigation";
import useSWR from "swr";
import type { Role } from "@/lib/constants";
import { API_ROUTES } from "@/lib/constants";
import {
  signIn as apiSignIn,
  logOut as apiLogOut,
  getMe,
} from "@/lib/api/auth";

export interface AuthUser {
  id: string;
  name: string;
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
  signIn: (email: string, password: string) => Promise<AuthUser>;
  logOut: () => Promise<void>;
  mutateUser: () => Promise<AuthUser | null | undefined>;
}

export const AuthContext = createContext<AuthContextState | undefined>(
  undefined
);

const AUTH_PAGES = [
  "/login",
  "/forgot-password",
  "/verify",
  "/check-email",
  "/reset-password",
];

async function meFetcher(): Promise<AuthUser> {
  const data = await getMe();
  return {
    ...data,
    name: data.name ?? data.fullName,
  };
}

export function AuthProvider({ children }: { children: React.ReactNode }) {
  const router = useRouter();
  const pathname = usePathname();

  const shouldFetchUser = useMemo(() => {
    if (AUTH_PAGES.some((route) => pathname.startsWith(route))) return false;
    return true;
  }, [pathname]);

  const {
    data: user,
    mutate,
    isLoading,
    isValidating,
  } = useSWR<AuthUser | null>(
    shouldFetchUser ? API_ROUTES.AUTH.ME : null,
    meFetcher,
    {
      revalidateOnFocus: false,
      shouldRetryOnError: false,
    }
  );

  const signIn = useCallback(
    async (email: string, password: string): Promise<AuthUser> => {
      // Let errors propagate as-is so callers can inspect the Axios error
      await apiSignIn(email, password);
      // Backend set cookies via Set-Cookie header; now fetch user profile
      const freshUser = await meFetcher();
      await mutate(freshUser, false);
      return freshUser;
    },
    [mutate]
  );

  const logOut = useCallback(async () => {
    try {
      await apiLogOut();
    } catch {
      // BFF proxy always clears cookies even on error
    } finally {
      await mutate(null, false);
      router.push("/login");
    }
  }, [mutate, router]);

  // Listen for forced logout from Axios interceptor
  useEffect(() => {
    const handleGlobalLogout = () => void logOut();
    window.addEventListener("auth:logout", handleGlobalLogout);
    return () => window.removeEventListener("auth:logout", handleGlobalLogout);
  }, [logOut]);

  const mutateUser = useCallback(async () => {
    return mutate();
  }, [mutate]);

  const contextValue = useMemo(
    () => ({
      user: user ?? null,
      isLoading,
      isValidating,
      signIn,
      logOut,
      mutateUser,
    }),
    [user, isLoading, isValidating, signIn, logOut, mutateUser]
  );

  return (
    <AuthContext.Provider value={contextValue}>
      {children}
    </AuthContext.Provider>
  );
}
