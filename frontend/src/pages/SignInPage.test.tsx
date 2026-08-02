import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { BLOCKED_EMAIL, LOCKED_EMAIL, REGISTERED_EMAIL, REGISTERED_PASSWORD } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

describe('SignInPage', () => {
  it('signs in with correct credentials and lands on the dashboard', async () => {
    const { user } = renderApp('/sign-in')

    await user.type(screen.getByLabelText(/email/i), REGISTERED_EMAIL)
    await user.type(screen.getByLabelText(/password/i), REGISTERED_PASSWORD)
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    await screen.findByRole('table')
    await waitFor(() => expect(useAuthStore.getState().token).not.toBeNull())
  })

  it('shows an error for bad credentials and stays signed out', async () => {
    const { user } = renderApp('/sign-in')

    await user.type(screen.getByLabelText(/email/i), REGISTERED_EMAIL)
    await user.type(screen.getByLabelText(/password/i), 'wrong-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid email or password/i)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('shows a distinct message for a blocked account and stays signed out', async () => {
    const { user } = renderApp('/sign-in')

    await user.type(screen.getByLabelText(/email/i), BLOCKED_EMAIL)
    await user.type(screen.getByLabelText(/password/i), 'whatever-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/account is blocked/i)
    expect(useAuthStore.getState().token).toBeNull()
  })

  it('shows a distinct message for a temporarily locked account and stays signed out', async () => {
    const { user } = renderApp('/sign-in')

    await user.type(screen.getByLabelText(/email/i), LOCKED_EMAIL)
    await user.type(screen.getByLabelText(/password/i), 'whatever-password')
    await user.click(screen.getByRole('button', { name: /^sign in$/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/temporarily locked/i)
    expect(useAuthStore.getState().token).toBeNull()
  })
})
