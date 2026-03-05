import { defineConfig } from "vite";
import react from "@vitejs/plugin-react";

export default defineConfig({
  plugins: [react()],
  server: {
    proxy: {
      // API calls are fine with changeOrigin=true
      "/api": {
        target: "http://localhost:8080",
        changeOrigin: true,
        secure: false,
      },

      // IMPORTANT: keep Host=localhost:5173 for OAuth routes
      // so Spring Security generates redirect_uri using 5173
      "/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: false,
        secure: false,
      },
      "/login/oauth2": {
        target: "http://localhost:8080",
        changeOrigin: false,
        secure: false,
      },
      "/logout": {
        target: "http://localhost:8080",
        changeOrigin: false,
        secure: false,
      },
    },
  },
});