/** @type {import('next').NextConfig} */
const nextConfig = {
  env: {
    NEXT_PUBLIC_API_URL: process.env.NEXT_PUBLIC_API_URL || "http://15.207.30.145:8080/api/v1",
  },
}

module.exports = nextConfig
