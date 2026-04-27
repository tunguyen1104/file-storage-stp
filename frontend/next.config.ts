import type {NextConfig} from "next";

const nextConfig: NextConfig = {
    /* config options here */
    images: {
        remotePatterns: [
        ],
    },
    eslint: {
        ignoreDuringBuilds: true,
    },
    reactStrictMode: true,
    output: 'standalone',
};

export default nextConfig;
