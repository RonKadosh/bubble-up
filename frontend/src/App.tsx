import { useEffect, lazy, Suspense } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import DemoLandingPage from './pages/DemoLandingPage'
import DemoStatsPage from './pages/DemoStatsPage'
import OAuthCallbackPage from './pages/auth/OAuthCallbackPage'
import DashboardPage from './pages/DashboardPage'
import GroupsPage from './pages/GroupsPage'
import ProfilePage from './pages/ProfilePage'
import SettingsPage from './pages/SettingsPage'
import AcademyPage from './pages/AcademyPage'
import BubbleRoomPage from './pages/BubbleRoomPage'
import ExpertRoomPage from './pages/ExpertRoomPage'
import ExpertOnboardingPage from './pages/expert/ExpertOnboardingPage'
import ExpertDashboardPage from './pages/expert/ExpertDashboardPage'
import ExpertProfileEditPage from './pages/expert/ExpertProfileEditPage'
import BookingRequestsPage from './pages/expert/BookingRequestsPage'
import ExpertDirectoryPage from './pages/ExpertDirectoryPage'
import ExpertPublicProfilePage from './pages/ExpertPublicProfilePage'
import HelpPage from './pages/HelpPage'
import AdminLayoutPage from './pages/admin/AdminLayoutPage'
import ReportPage from './pages/report/ReportPage'
import CoursePage from './pages/CoursePage'
import Layout from './components/Sidebar'
import { useAuthStore } from './store/authStore'
import { useOnboardingStore, isRouteAccessible, type LockableFeature } from './store/onboardingStore'
import { applyThemeClass, useThemeStore } from './store/themeStore'
import { applyLangAttrs, useLanguageStore } from './store/languageStore'
import i18n from './i18n'
import { connectWs, disconnectWs } from './api/ws'
import { DEMO_MODE } from './api/demo'

/** Where an unauthenticated visitor lands: the demo build sends them to the
 *  one-click /demo entry; the real app to the Google login. */
const UNAUTH_LANDING = DEMO_MODE ? '/demo' : '/login'

// Dev-only password login (`/login/testing`). `__DEV_LOGIN__` is a build-time
// literal (see vite.config.ts): true under `npm run dev` and in the local
// docker-compose stack (which passes VITE_DEV_LOGIN=true), false in production
// CI builds. When false, this dynamic import — and its chunk, including the
// restored login/register API code — is dead-code-eliminated from the bundle.
const TestingLoginPage = __DEV_LOGIN__
  ? lazy(() => import('./pages/auth/TestingLoginPage'))
  : null

function RequireAuth({ children }: { children: JSX.Element }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  if (!accessToken) return <Navigate to={UNAUTH_LANDING} replace />
  return children
}

function LandingRedirect() {
  const accessToken = useAuthStore((s) => s.accessToken)
  if (!accessToken) return <Navigate to={UNAUTH_LANDING} replace />
  return <Navigate to="/dashboard" replace />
}

/**
 * Gates expert-only pages. Non-experts get bounced to /become-expert with a
 * pitch to apply, instead of a flat 403. The role is read from authStore (set
 * on login / refresh and on apply success).
 */
function RequireExpert({ children }: { children: JSX.Element }) {
  const role = useAuthStore((s) => s.user?.role)
  if (role !== 'EXPERT' && role !== 'ADMIN') return <Navigate to="/become-expert" replace />
  return children
}

function RequireAdmin({ children }: { children: JSX.Element }) {
  const role = useAuthStore((s) => s.user?.role)
  if (role !== 'ADMIN') return <Navigate to="/dashboard" replace />
  return children
}

/**
 * Progressive unlocking: routes for not-yet-unlocked features bounce to the
 * onboarding wizard (/dashboard). Onboarded users and the still-loading window
 * pass through (see `isFeatureUnlocked`), so this only gates a mid-onboarding user
 * trying to jump ahead of the wizard.
 */
function RequireUnlocked({ feature, children }: { feature: LockableFeature; children: JSX.Element }) {
  const status = useOnboardingStore((s) => s.status)
  if (!isRouteAccessible(feature, status)) return <Navigate to="/dashboard" replace />
  return children
}

export default function App() {
  const theme = useThemeStore((s) => s.theme)
  const lang = useLanguageStore((s) => s.lang)

  useEffect(() => {
    applyThemeClass(theme)
  }, [theme])

  useEffect(() => {
    i18n.changeLanguage(lang)
    applyLangAttrs(lang)
  }, [lang])

  useEffect(() => {
    const { accessToken } = useAuthStore.getState()
    if (accessToken) connectWs()
    return useAuthStore.subscribe((state, prev) => {
      const wasConnected = Boolean(prev.accessToken)
      const shouldConnect = Boolean(state.accessToken)
      if (shouldConnect && !wasConnected) connectWs()
      if (!shouldConnect && wasConnected) disconnectWs()
    })
  }, [])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<LandingRedirect />} />
        {/* Build-specific entry pages: each build kills the page that can't work on
            it. Prod has no /api/demo backend (→ /demo redirects to /login below); the
            demo box has no Google OAuth creds, so its /login sign-in is dead and
            redirects to /demo. LandingRedirect/UNAUTH_LANDING already route unauth
            visitors correctly per build; this only hardens direct URL hits. */}
        <Route path="/login" element={DEMO_MODE ? <Navigate to="/demo" replace /> : <LoginPage />} />
        {/* Dev-only password login. Registered only when import.meta.env.DEV;
            in prod TestingLoginPage is null, the route is absent, and the path
            falls through to the catch-all redirect below. */}
        {TestingLoginPage && (
          <Route
            path="/login/testing"
            element={<Suspense fallback={null}><TestingLoginPage /></Suspense>}
          />
        )}
        {/* Public, no-login demo entry (demo build only). On prod the backend is
            gated off, so the page would 404 on "Start demo" — redirect to /login. */}
        <Route path="/demo" element={DEMO_MODE ? <DemoLandingPage /> : <Navigate to="/login" replace />} />
        {/* Operator-only demo usage view, secret-key gated (demo build only). */}
        {DEMO_MODE && <Route path="/demo/stats" element={<DemoStatsPage />} />}
        {/* OAuth landing page — public on purpose: /auth/callback reads
            tokens from the URL fragment and hydrates authStore. */}
        <Route path="/auth/callback" element={<OAuthCallbackPage />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/groups" element={<RequireUnlocked feature="bubbles"><GroupsPage /></RequireUnlocked>} />
          <Route path="/academy" element={<RequireUnlocked feature="academy"><AcademyPage /></RequireUnlocked>} />
          <Route path="/courses/:id" element={<CoursePage />} />
          <Route path="/profile" element={<Navigate to="/settings" replace />} />
          <Route path="/profile/:userId" element={<ProfilePage />} />
          <Route path="/settings" element={<RequireUnlocked feature="settings"><SettingsPage /></RequireUnlocked>} />
          <Route path="/help" element={<HelpPage />} />
          <Route path="/rooms/:roomId" element={<BubbleRoomPage />} />
          <Route path="/sessions/:sessionId" element={<ExpertRoomPage />} />
          <Route path="/become-expert" element={<ExpertOnboardingPage />} />
          <Route path="/experts" element={<RequireUnlocked feature="experts"><ExpertDirectoryPage /></RequireUnlocked>} />
          <Route path="/experts/:userId" element={<ExpertPublicProfilePage />} />
          <Route path="/expert" element={<RequireExpert><ExpertDashboardPage /></RequireExpert>} />
          <Route path="/expert/profile/edit" element={<RequireExpert><ExpertProfileEditPage /></RequireExpert>} />
          <Route path="/expert/requests" element={<RequireExpert><BookingRequestsPage /></RequireExpert>} />
          <Route path="/bookings" element={<BookingRequestsPage />} />
          <Route path="/report" element={<ReportPage />} />
          <Route path="/admin" element={<RequireAdmin><AdminLayoutPage /></RequireAdmin>} />
          {/* Legacy deep-link: the standalone expert page is now a tab in the admin shell. */}
          <Route path="/admin/experts" element={<Navigate to="/admin/expert-requests" replace />} />
          <Route path="/admin/:tab" element={<RequireAdmin><AdminLayoutPage /></RequireAdmin>} />
        </Route>
        <Route path="*" element={<LandingRedirect />} />
      </Routes>
    </BrowserRouter>
  )
}
