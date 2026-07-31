import '@testing-library/jest-dom/vitest'
import { cleanup } from '@testing-library/react'
import { setupServer } from 'msw/node'
import { afterAll, afterEach, beforeAll } from 'vitest'
import { useColumnsStore } from '../store/columnsStore'
import {
  handlers,
  resetFailures,
  resetRecordedRequests,
  resetRecordedSymbolRequests,
  resetSymbolFailures,
} from './handlers'

export const server = setupServer(...handlers)

beforeAll(() => server.listen({ onUnhandledRequest: 'error' }))

afterEach(() => {
  cleanup()
  server.resetHandlers()
  resetRecordedRequests()
  resetFailures()
  resetRecordedSymbolRequests()
  resetSymbolFailures()
  // The Zustand store is created at module scope and hydrated once per test
  // *file*, so clearing localStorage alone resets nothing — the in-memory
  // state is authoritative and leaks into the next test. Without this reset
  // the suite is order-dependent: a column hidden in one test disappears from
  // another's expected header count.
  localStorage.clear()
  useColumnsStore.setState({ visibleColumns: null })
})

afterAll(() => server.close())
