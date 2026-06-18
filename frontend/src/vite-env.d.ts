/// <reference types="vite/client" />

// Build-time constant injected by vite.config.ts `define`. True when the dev-only
// password login (`/login/testing`) should be available; inlined as a literal so
// the route + page tree-shake out of production builds when false.
declare const __DEV_LOGIN__: boolean
