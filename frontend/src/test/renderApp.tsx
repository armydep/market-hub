import { QueryClient } from '@tanstack/react-query'
import { render } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { App } from '../App'

/**
 * Render the app at a given URL with an isolated query cache and retries off,
 * so a simulated failure surfaces immediately instead of after backoff.
 */
export function renderApp(initialUrl = '/') {
  const queryClient = new QueryClient({
    defaultOptions: {
      queries: { retry: false, refetchOnWindowFocus: false },
    },
  })

  const user = userEvent.setup()
  const utils = render(
    <MemoryRouter initialEntries={[initialUrl]}>
      <App queryClient={queryClient} />
    </MemoryRouter>,
  )

  return { ...utils, user, queryClient }
}
