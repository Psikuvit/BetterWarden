import react from '@vitejs/plugin-react'
import { defineConfig } from 'vite'

// Builds straight into warden-core's static resources, so Spring Boot's default
// classpath:/static/ handling serves the panel with zero extra Spring config -
// same "one embedded process" pattern as the rest of this project.
export default defineConfig({
  plugins: [react()],
  build: {
    outDir: '../warden-core/src/main/resources/static',
    emptyOutDir: true,
  },
  server: {
    // `npm run dev` still needs a real Core to talk to - point it at a locally
    // running one (HOST mode, default port) instead of duplicating API logic.
    proxy: {
      '/api': 'http://localhost:8095',
      '/health': 'http://localhost:8095',
      '/ws': {
        target: 'ws://localhost:8095',
        ws: true,
      },
    },
  },
})
