import { useEffect } from 'react'
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import LoginPage from './pages/LoginPage'
import DashboardPage from './pages/DashboardPage'
import GroupsPage from './pages/GroupsPage'
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
        </Route>
        <Route path="*" element={<Navigate to="/dashboard" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
