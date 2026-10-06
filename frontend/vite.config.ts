import react from '@vitejs/plugin-react';
import { defineConfig } from 'vite';

// One HTML entry per page. Spring Boot serves the built files from the same origin as the API,
// so the SameSite=Strict session cookies need no CORS setup.
export default defineConfig({
  plugins: [react()],
  build: {
    rollupOptions: { input: ['index.html', 'dashboard.html', 'inspection.html'] },
  },
  // `npm run dev` hot-reloads the views against a running `./gradlew bootRun`.
  server: { proxy: { '/api': 'http://localhost:8080', '/storage': 'http://localhost:8080' } },
});
