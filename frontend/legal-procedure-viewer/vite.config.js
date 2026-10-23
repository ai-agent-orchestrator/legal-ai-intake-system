import { defineConfig, loadEnv } from "vite";
import { fileURLToPath } from "node:url";
import react from "@vitejs/plugin-react";

export default defineConfig(({ mode }) => {
  const env = loadEnv(mode, process.cwd(), "API_");
  const proxy = {
    '/api': { target: process.env.API_PROXY_TARGET || env.API_PROXY_TARGET || 'http://localhost:8080', changeOrigin: true },
  };
  return {
    plugins: [react()],
    server: { proxy },
    preview: { proxy },
    build: {
      rollupOptions: {
        input: {
          viewer: fileURLToPath(new URL("./index.html", import.meta.url)),
          operator: fileURLToPath(new URL("./operator.html", import.meta.url)),
          intake: fileURLToPath(new URL("./intake.html", import.meta.url)),
        },
      },
    },
  };
});
