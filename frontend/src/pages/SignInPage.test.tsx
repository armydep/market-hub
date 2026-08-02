import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { REGISTERED_EMAIL, REGISTERED_PASSWORD } from '../test/fixtures'
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
})
