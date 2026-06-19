/// <reference types="vite/client" />

interface ImportMetaEnv {
  /** "true" only in the demo build (demo.bubbleup.online). Drives the public
   *  /demo landing, guest auto-login, the guided tour, and the mocked video. */
  readonly VITE_DEMO_MODE?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
