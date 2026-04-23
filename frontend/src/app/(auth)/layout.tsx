import React from "react";
import { HardDrive } from "lucide-react";

export default function AuthLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="grid min-h-screen lg:grid-cols-2">
      {/* Branding Side - Hidden on mobile */}
      <div className="hidden lg:flex flex-col justify-between bg-primary p-12 text-primary-foreground relative overflow-hidden">
        {/* Subtle background decoration */}
        <div className="absolute top-0 left-0 w-full h-full bg-[linear-gradient(135deg,rgba(255,255,255,0.1)_0%,rgba(255,255,255,0)_100%)] pointer-events-none" />
        
        <div className="flex items-center gap-3 font-bold text-2xl relative z-10">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-white/20 border border-white/30 backdrop-blur-sm shadow-sm">
            <HardDrive className="h-6 w-6 text-white" />
          </div>
          File Storage Enterprise
        </div>
        
        <div className="max-w-lg relative z-10">
          <h1 className="text-4xl font-bold mb-4 tracking-tight leading-tight">
            Secure, scalable storage for modern teams.
          </h1>
          <p className="text-primary-foreground/80 text-lg leading-relaxed">
            Manage your files effortlessly with our enterprise-grade multi-tenant architecture. Built for speed, security, and absolute reliability.
          </p>
        </div>
        
        <div className="text-sm font-medium text-primary-foreground/60 relative z-10">
          &copy; {new Date().getFullYear()} File Storage Inc. All rights reserved.
        </div>
      </div>
      
      {/* Form Side */}
      <div className="flex items-center justify-center bg-background px-4 sm:px-6 lg:px-8">
        <div className="w-full max-w-[440px]">
          {children}
        </div>
      </div>
    </div>
  );
}
