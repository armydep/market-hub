import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { ACCOUNT, AUTH_RESPONSE, OTHER_REGISTERED_EMAIL, REGISTERED_PASSWORD } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function signIn() {
  useAuthStore.getState().signIn(AUTH_RESPONSE)
}

/** Both forms have a "Current password" field, so tests scope by section. */
function formFor(headingName: RegExp): HTMLElement {
  const form = screen.getByRole('heading', { name: headingName }).closest('form')
  if (!form) throw new Error('Form not found')
  return form as HTMLElement
}

describe('AccountPage', () => {
  it('renders the signed-in user\'s account info', async () => {
    signIn()

    renderApp('/account')

    expect(await screen.findByText(ACCOUNT.email)).toBeInTheDocument()
    expect(screen.getByText(ACCOUNT.role)).toBeInTheDocument()
  })

  it('changes email with the correct current password', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const emailForm = formFor(/change email/i)
    const newEmail = 'new-account-email@example.com'
    await user.type(within(emailForm).getByLabelText(/new email/i), newEmail)
    await user.type(within(emailForm).getByLabelText(/current password/i), REGISTERED_PASSWORD)
    await user.click(within(emailForm).getByRole('button', { name: /save email/i }))

    expect(await screen.findByText(/email has been updated/i)).toBeInTheDocument()
    await waitFor(() => expect(screen.getByText(newEmail)).toBeInTheDocument())
    // The header's "Signed in as" line reflects the change without a re-login.
    expect(useAuthStore.getState().email).toBe(newEmail)
  })

  it('rejects an email change to an already-registered address', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const emailForm = formFor(/change email/i)
    await user.type(within(emailForm).getByLabelText(/new email/i), OTHER_REGISTERED_EMAIL)
    await user.type(within(emailForm).getByLabelText(/current password/i), REGISTERED_PASSWORD)
    await user.click(within(emailForm).getByRole('button', { name: /save email/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already registered/i)
  })

  it('rejects an email change with the wrong current password', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const emailForm = formFor(/change email/i)
    await user.type(within(emailForm).getByLabelText(/new email/i), 'irrelevant@example.com')
    await user.type(within(emailForm).getByLabelText(/current password/i), 'wrong-password')
    await user.click(within(emailForm).getByRole('button', { name: /save email/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/current password is incorrect/i)
  })

  it('changes password with the correct current password', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const passwordForm = formFor(/change password/i)
    await user.type(within(passwordForm).getByLabelText(/current password/i), REGISTERED_PASSWORD)
    await user.type(within(passwordForm).getByLabelText(/new password/i), 'brand-new-password-1')
    await user.click(within(passwordForm).getByRole('button', { name: /change password/i }))

    expect(await screen.findByText(/password has been changed/i)).toBeInTheDocument()
  })

  it('rejects a password change with the wrong current password', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const passwordForm = formFor(/change password/i)
    await user.type(within(passwordForm).getByLabelText(/current password/i), 'wrong-password')
    await user.type(within(passwordForm).getByLabelText(/new password/i), 'brand-new-password-1')
    await user.click(within(passwordForm).getByRole('button', { name: /change password/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/current password is incorrect/i)
  })

  it('rejects a new password shorter than 8 characters before submitting', async () => {
    signIn()
    const { user } = renderApp('/account')
    await screen.findByText(ACCOUNT.email)

    const passwordForm = formFor(/change password/i)
    await user.type(within(passwordForm).getByLabelText(/current password/i), REGISTERED_PASSWORD)
    await user.type(within(passwordForm).getByLabelText(/new password/i), 'short')
    await user.click(within(passwordForm).getByRole('button', { name: /change password/i }))

    expect(await screen.findByText(/at least 8 characters/i)).toBeInTheDocument()
  })
})
