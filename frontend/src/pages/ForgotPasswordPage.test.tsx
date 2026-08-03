import { screen } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { PASSWORD_RESET_REQUEST_MESSAGE } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

describe('ForgotPasswordPage', () => {
  it('shows the same confirmation message regardless of whether the email is registered', async () => {
    const { user } = renderApp('/forgot-password')

    await user.type(screen.getByLabelText(/email/i), 'someone@example.com')
    await user.click(screen.getByRole('button', { name: /send reset link/i }))

    expect(await screen.findByText(PASSWORD_RESET_REQUEST_MESSAGE)).toBeInTheDocument()
  })
})
