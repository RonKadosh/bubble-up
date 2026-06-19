/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" only in the demo build (demo.bubbleup.online). Drives the public
   *  /demo landing, guest auto-login, the guided tour, and the mocked video. */
  readonly VITE_DEMO_MODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}

// Build-time constant injected by vite.config.ts `define`. True when the dev-only
// password login (`/login/testing`) should be available; inlined as a literal so
// the route + page tree-shake out of production builds when false.
declare const __DEV_LOGIN__: boolean
