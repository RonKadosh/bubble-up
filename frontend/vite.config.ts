import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

// Config is a function so we can read the build `mode`. `__DEV_LOGIN__` gates the
// dev-only password page (`/login/testing`):
//   - `mode !== 'production'`  → always on under `npm run dev` (the Vite dev server).
//   - `VITE_DEV_LOGIN==='true'` → on in a production build only when explicitly asked.
//     The local docker-compose passes this as a Docker build arg so the page works
//     against the full containerized stack; the prod compose uses a prebuilt CI
//     image that never sets it, so the page + its code are dead-code-eliminated.
// The value is inlined as a literal boolean, so the route + page chunk tree-shake
// cleanly when it's false. See App.tsx.
export default defineConfig(({ mode }) => ({
  plugins: [react()],
  define: {
    __DEV_LOGIN__: JSON.stringify(
      mode !== 'production' || process.env.VITE_DEV_LOGIN === 'true',
    ),
  },
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/ws': {
        target: 'http://localhost:8080',
        changeOrigin: true,
        ws: true,
      },
      // Google OAuth2: Spring publishes /oauth2/authorization/google (entry)
      // and /login/oauth2/code/google (callback). The browser hits these
      // directly so they must be proxied to the backend.
      '/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
      '/login/oauth2': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
}))
