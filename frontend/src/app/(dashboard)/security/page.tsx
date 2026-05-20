import { Shield } from "lucide-react";

export default function SecurityPage() {
  return (
    <div className="flex h-full flex-col items-center justify-center gap-4 p-8 text-center">
      <div className="flex h-16 w-16 items-center justify-center rounded-full bg-[#F3E8FF]">
        <Shield className="h-8 w-8 text-[#7C3AED]" />
      </div>
      <div className="space-y-1">
        <h1 className="text-xl font-semibold text-gray-900">Cài đặt bảo mật</h1>
        <p className="text-sm text-gray-500">Cấu hình bảo mật sẽ sớm được ra mắt.</p>
      </div>
    </div>
  );
}
