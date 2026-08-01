import { render, screen } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { RequireAuth } from './RequireAuth'

function renderProtected() {
  return render(
    <MemoryRouter initialEntries={['/protected']}>
      <Routes>
        <Route
          path="/protected"
          element={
            <RequireAuth>
              <div>secret content</div>
            </RequireAuth>
          }
        />
        <Route path="/sign-in" element={<div>sign in page</div>} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('RequireAuth', () => {
  it('renders the protected content when authenticated', () => {
    useAuthStore.setState({ token: 'a.jwt.token', userId: 1, email: 'a@example.com', role: 'TRADER' })

    renderProtected()

    expect(screen.getByText('secret content')).toBeInTheDocument()
  })

  it('redirects to sign-in when not authenticated', () => {
    useAuthStore.setState({ token: null, userId: null, email: null, role: null })

    renderProtected()

    expect(screen.getByText('sign in page')).toBeInTheDocument()
    expect(screen.queryByText('secret content')).not.toBeInTheDocument()
  })
})
