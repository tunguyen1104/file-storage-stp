import Link from "next/link";
import { FileQuestion } from "lucide-react";

export default function NotFound() {
  return (
    <div className="flex min-h-screen flex-col items-center justify-center bg-gradient-to-br from-gray-50 via-white to-purple-50 px-6">
      {/* Decorative background circles */}
      <div className="pointer-events-none absolute inset-0 overflow-hidden">
        <div className="absolute -top-24 -right-24 h-96 w-96 rounded-full bg-[#7C3AED]/5 blur-3xl" />
        <div className="absolute -bottom-32 -left-32 h-[28rem] w-[28rem] rounded-full bg-blue-500/5 blur-3xl" />
      </div>

      <div className="relative flex flex-col items-center gap-6 text-center">
        {/* Icon */}
        <div className="flex h-24 w-24 items-center justify-center rounded-2xl bg-gradient-to-br from-[#7C3AED]/10 to-purple-100 shadow-sm border border-purple-100">
          <FileQuestion className="h-12 w-12 text-[#7C3AED]" />
        </div>

        {/* 404 number */}
        <h1 className="text-8xl font-extrabold tracking-tighter bg-gradient-to-br from-[#7C3AED] to-[#a855f7] bg-clip-text text-transparent select-none">
          404
        </h1>

        {/* Message */}
        <div className="space-y-2">
          <h2 className="text-xl font-semibold text-gray-800">
            Không tìm thấy trang
          </h2>
          <p className="max-w-sm text-sm text-gray-500 leading-relaxed">
            Trang bạn đang tìm kiếm không tồn tại hoặc đã được di chuyển sang
            vị trí khác.
          </p>
        </div>

        {/* Action */}
        <Link
          href="/files"
          className="mt-2 inline-flex items-center gap-2 rounded-lg bg-[#7C3AED] px-6 py-2.5 text-sm font-medium text-white shadow-md shadow-[#7C3AED]/25 transition-all hover:bg-[#6D28D9] hover:shadow-lg hover:shadow-[#7C3AED]/30 active:scale-[0.98]"
        >
          <svg
            xmlns="http://www.w3.org/2000/svg"
            width="16"
            height="16"
            viewBox="0 0 24 24"
            fill="none"
            stroke="currentColor"
            strokeWidth="2"
            strokeLinecap="round"
            strokeLinejoin="round"
          >
            <path d="m12 19-7-7 7-7" />
            <path d="M19 12H5" />
          </svg>
          Quay về trang chính
        </Link>
      </div>
    </div>
  );
}
