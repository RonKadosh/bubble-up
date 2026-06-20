import client, { ApiSuccess } from './client'

/**
 * True only in the demo build (VITE_DEMO_MODE baked in at build time for the
 * demo.bubbleup.online image). Gates the public /demo landing, guest auto-login,
 * the guided tour, and the "video off in the demo" placeholder. Production builds
 * leave this unset, so all the demo affordances compile away to no-ops.
 */
export const DEMO_MODE = import.meta.env.VITE_DEMO_MODE === 'true'

export interface DemoUser {
  id: string
  email: string
  role: string
  displayName: string
  avatarUrl: string | null
  emailVerified: boolean
}

export interface DemoStartResult {
  accessToken: string
  refreshToken: string
  user: DemoUser
  /** The Bubble the tour opens onto ("CS101 Night Owls"). */
  starterGroupId: string
  startTour: boolean
}

/** Builds a fresh isolated world and returns a guest session. Public (no auth). */
export async function startDemo(): Promise<DemoStartResult> {
  const res = await client.post<ApiSuccess<DemoStartResult>>('/demo/start')
  return res.data.data
}

/** Keeps the guest's world alive (resets its idle-TTL). Best-effort. */
export async function demoHeartbeat(): Promise<void> {
  await client.post('/demo/heartbeat')
}

/** Eager teardown when the visitor leaves; the server sweep is the backstop. */
export async function endDemo(): Promise<void> {
  await client.post('/demo/end')
}

export interface DemoStats {
  totalStarts: number
  activeSessions: number
  last7Days: number
}

/**
 * Demo usage counters for the operator. Guarded by a shared secret (app.demo.stats-key
 * on the backend) passed as ?key=... — the no-login demo has no admin session. A wrong
 * or missing key throws 401.
 */
export async function getDemoStats(key: string): Promise<DemoStats> {
  const res = await client.get<ApiSuccess<DemoStats>>('/demo/stats', { params: { key } })
  return res.data.data
}
