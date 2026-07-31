import { QueryClientProvider, type QueryClient } from '@tanstack/react-query'
import { BrowserRouter, Route, Routes } from 'react-router-dom'
import { ErrorBoundary } from './components/ErrorBoundary'
import { EmptyState } from './components/States'
import { DashboardPage } from './pages/DashboardPage'
import { makeQueryClient } from './queryClient'
import './App.css'

interface Props {
  /** Injected by tests so each case gets an isolated cache. */
  queryClient?: QueryClient
}

/** Routes and providers, without a router — so tests can supply their own. */
export function App({ queryClient }: Props = {}) {
  const client = queryClient ?? makeQueryClient()
  return (
    <QueryClientProvider client={client}>
      <div className="app-shell">
        <main className="app-main">
          <ErrorBoundary>
            <Routes>
              <Route path="/" element={<DashboardPage />} />
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
