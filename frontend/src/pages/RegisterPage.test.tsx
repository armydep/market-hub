import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { REGISTERED_EMAIL } from '../test/fixtures'
import { recordedAuthRegisterRequests } from '../test/handlers'
import { renderApp } from '../test/renderApp'

describe('RegisterPage', () => {
  it('registers, stores the token, and lands on the dashboard', async () => {
    const { user } = renderApp('/register')

    await user.type(screen.getByLabelText(/email/i), 'new-user@example.com')
    await user.type(screen.getByLabelText(/password/i), 'longenoughpassword')
    await user.click(screen.getByRole('button', { name: /^register$/i }))

    await screen.findByRole('table')
    await waitFor(() => expect(useAuthStore.getState().token).not.toBeNull())
    expect(useAuthStore.getState().email).toBe('new-user@example.com')
  })

  it('shows an error for an already-registered email and does not sign in', async () => {
    const { user } = renderApp('/register')

    await user.type(screen.getByLabelText(/email/i), REGISTERED_EMAIL)
    await user.type(screen.getByLabelText(/password/i), 'longenoughpassword')
    await user.click(screen.getByRole('button', { name: /^register$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('blocks submission client-side when the password is too short', async () => {
    const { user } = renderApp('/register')

    await user.type(screen.getByLabelText(/email/i), 'short-pw@example.com')
    await user.type(screen.getByLabelText(/password/i), 'short')
    await user.click(screen.getByRole('button', { name: /^register$/i }))

    // No request should have been sent at all — the browser's own constraint
    // validation (minLength=8) blocks the submit before onSubmit ever fires.
    expect(recordedAuthRegisterRequests).toHaveLength(0)
  })
})
