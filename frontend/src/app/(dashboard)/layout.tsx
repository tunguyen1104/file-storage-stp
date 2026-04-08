import React from "react";

export default function DashboardLayout({
  children,
}: Readonly<{
  children: React.ReactNode;
}>) {
  return (
    <div className="flex min-h-screen">
      {/* TODO: Add <Sidebar /> from @/components/layout/Sidebar */}
      <div className="flex flex-1 flex-col">
        {/* TODO: Add <Header /> from @/components/layout/Header */}
        {/* TODO: Add <Breadcrumb /> from @/components/layout/Breadcrumb */}
        <main className="flex-1 p-6 md:p-8 overflow-y-auto">
          {children}
        </main>
      </div>
    </div>
  );
}
