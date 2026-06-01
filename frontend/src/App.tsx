import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
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
import AdminLayoutPage from './pages/admin/AdminLayoutPage'
import AdminExpertVerificationPage from './pages/admin/AdminExpertVerificationPage'
import CoursePage from './pages/CoursePage'
import Layout from './components/Sidebar'
import { useAuthStore } from './store/authStore'
import { applyThemeClass, useThemeStore } from './store/themeStore'
import { applyLangAttrs, useLanguageStore } from './store/languageStore'
import i18n from './i18n'
import { connectWs, disconnectWs } from './api/ws'

function RequireAuth({ children }: { children: JSX.Element }) {
  const accessToken = useAuthStore((s) => s.accessToken)
  if (!accessToken) return <Navigate to="/login" replace />
  return children
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
    if (useAuthStore.getState().accessToken) connectWs()
    return useAuthStore.subscribe((state, prev) => {
      if (state.accessToken && !prev.accessToken) connectWs()
      if (!state.accessToken && prev.accessToken) disconnectWs()
    })
  }, [])

  return (
    <BrowserRouter>
      <Routes>
        <Route path="/login" element={<LoginPage />} />
        <Route
          element={
            <RequireAuth>
              <Layout />
            </RequireAuth>
          }
        >
          <Route path="/dashboard" element={<DashboardPage />} />
          <Route path="/groups" element={<GroupsPage />} />
          <Route path="/academy" element={<AcademyPage />} />
          <Route path="/courses/:id" element={<CoursePage />} />
          <Route path="/profile" element={<Navigate to="/settings" replace />} />
          <Route path="/profile/:userId" element={<ProfilePage />} />
          <Route path="/settings" element={<SettingsPage />} />
          <Route path="/rooms/:roomId" element={<BubbleRoomPage />} />
          <Route path="/sessions/:sessionId" element={<ExpertRoomPage />} />
          <Route path="/become-expert" element={<ExpertOnboardingPage />} />
          <Route path="/experts" element={<ExpertDirectoryPage />} />
          <Route path="/experts/:userId" element={<ExpertPublicProfilePage />} />
          <Route path="/expert" element={<RequireExpert><ExpertDashboardPage /></RequireExpert>} />
          <Route path="/expert/profile/edit" element={<RequireExpert><ExpertProfileEditPage /></RequireExpert>} />
          <Route path="/expert/requests" element={<RequireExpert><BookingRequestsPage /></RequireExpert>} />
          <Route path="/bookings" element={<BookingRequestsPage />} />
          <Route path="/admin" element={<RequireAdmin><AdminLayoutPage /></RequireAdmin>} />
          <Route path="/admin/:tab" element={<RequireAdmin><AdminLayoutPage /></RequireAdmin>} />
          <Route path="/admin/experts" element={<RequireAdmin><AdminExpertVerificationPage /></RequireAdmin>} />
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
