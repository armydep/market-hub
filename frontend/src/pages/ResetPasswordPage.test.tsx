import { screen, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { INVALID_RESET_TOKEN, VALID_RESET_TOKEN } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

describe('ResetPasswordPage', () => {
  it('resets the password with a valid token and shows a success message', async () => {
    const { user } = renderApp(`/reset-password?token=${VALID_RESET_TOKEN}`)

    await user.type(screen.getByLabelText(/new password/i), 'a-new-password1')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    const confirmation = await screen.findByText(/password has been updated/i)
    expect(confirmation).toBeInTheDocument()
    // Scoped to the page's own content: the guest-state app header also
    // renders a "Sign in" link, so an unscoped query matches both.
    const page = confirmation.closest('section')
    if (!page) throw new Error('Page section not found')
    expect(within(page).getByRole('link', { name: /sign in/i })).toBeInTheDocument()
  })

  it('shows the backend error for an invalid or expired token', async () => {
    const { user } = renderApp(`/reset-password?token=${INVALID_RESET_TOKEN}`)

    await user.type(screen.getByLabelText(/new password/i), 'a-new-password1')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/invalid or expired token/i)
  })

  it('blocks submission client-side when the password is too short', async () => {
    const { user } = renderApp(`/reset-password?token=${VALID_RESET_TOKEN}`)

    await user.type(screen.getByLabelText(/new password/i), 'short')
    await user.click(screen.getByRole('button', { name: /reset password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/at least 8 characters/i)
  })
})
