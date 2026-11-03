import { defineConfig } from 'vite';
import react from '@vitejs/plugin-react';

// The dev server proxies /api to the Java backend so the browser never needs CORS during development.
export default defineConfig({
  plugins: [react()],
  server: { port: 5173, proxy: { '/api': process.env.VITE_BACKEND_URL || 'http://localhost:8080' } },
  test: { environment: 'jsdom', globals: true, setupFiles: './src/test-setup.js' },
});
