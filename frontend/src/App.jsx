import { BrowserRouter, Routes, Route } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext'
import { ThemeProvider } from './context/ThemeContext'
import Layout from './components/Layout'
import ProtectedRoute from './components/ProtectedRoute'

import AuthPage from './pages/AuthPage'
import StatewideResourcesPage from './pages/StatewideResourcesPage'
import SearchPage from './pages/SearchPage'
import ProviderDetailPage from './pages/ProviderDetailPage'
import SavedPage from './pages/SavedPage'
import MyReferralsPage from './pages/MyReferralsPage'
import AdminQueuePage from './pages/AdminQueuePage'
import AdminProvidersPage from './pages/AdminProvidersPage'
import ReportsPage from './pages/ReportsPage'
import NotFoundPage from './pages/NotFoundPage'

export default function App() {
  return (
    <ThemeProvider>
      <AuthProvider>
        <BrowserRouter>
          <Routes>
            {/* Public. /resources is deliberately open - someone who needs
                988 should not have to create an account first. */}
            <Route path="/login" element={<AuthPage />} />
            <Route path="/resources" element={<StatewideResourcesPage />} />

            {/* Everything else sits inside Layout, which renders the
                crisis banner on every screen. */}
            <Route element={<ProtectedRoute><Layout /></ProtectedRoute>}>
              <Route index element={<SearchPage />} />
              <Route path="providers/:id" element={<ProviderDetailPage />} />
              <Route path="saved" element={<SavedPage />} />
              <Route path="referrals" element={<MyReferralsPage />} />

              <Route
                path="admin/queue"
                element={<ProtectedRoute requireAdmin><AdminQueuePage /></ProtectedRoute>}
              />
              <Route
                path="admin/providers"
                element={<ProtectedRoute requireAdmin><AdminProvidersPage /></ProtectedRoute>}
              />
              <Route
                path="admin/reports"
                element={<ProtectedRoute requireAdmin><ReportsPage /></ProtectedRoute>}
              />
            </Route>

            <Route path="*" element={<NotFoundPage />} />
          </Routes>
        </BrowserRouter>
      </AuthProvider>
    </ThemeProvider>
  )
}
