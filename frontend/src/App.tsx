import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { useState } from 'react'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { AppHeader } from './components/AppHeader'
import { ErrorBoundary } from './components/ErrorBoundary'
import { RequireAuth } from './components/RequireAuth'
import { EmptyState } from './components/States'
import { AdminUsersPage } from './pages/AdminUsersPage'
import { CoinDetailPage } from './pages/CoinDetailPage'
import { DashboardPage } from './pages/DashboardPage'
import { RegisterPage } from './pages/RegisterPage'
import { SignInPage } from './pages/SignInPage'
import { makeQueryClient } from './queryClient'
import './App.css'

interface Props {
  /** Injected by tests so each case gets an isolated cache. */
  queryClient?: QueryClient
}

/** Routes and providers, without a router — so tests can supply their own. */
export function App({ queryClient }: Props = {}) {
  // Lazy initial state, not a bare call: building the client in the render body
  // works today only because react-router happens not to re-render App. Add one
  // piece of state above it and every navigation would discard the whole cache.
  const [fallbackClient] = useState(makeQueryClient)
  const client = queryClient ?? fallbackClient
  return (
    <QueryClientProvider client={client}>
      <div className="app-shell">
        <AppHeader />
        <main className="app-main">
          <ErrorBoundary>
            <Routes>
              <Route path="/" element={<DashboardPage />} />
              <Route path="/coins/:symbol" element={<CoinDetailPage />} />
              <Route path="/register" element={<RegisterPage />} />
              <Route path="/sign-in" element={<SignInPage />} />
              <Route
                path="/admin/users"
                element={
                  <RequireAuth role="ADMIN">
                    <AdminUsersPage />
                  </RequireAuth>
                }
              />
              <Route
                path="*"
                element={<EmptyState title="Page not found" hint="That route doesn't exist." />}
              />
            </Routes>
          </ErrorBoundary>
        </main>
      </div>
    </QueryClientProvider>
  )
}

export default function AppWithRouter() {
  return (
    <BrowserRouter>
      <App />
    </BrowserRouter>
  )
}
