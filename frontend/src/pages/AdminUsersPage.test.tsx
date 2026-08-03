import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { ADMIN_AUTH_RESPONSE, ADMIN_USERS } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function signInAsAdmin() {
  useAuthStore.getState().signIn(ADMIN_AUTH_RESPONSE)
}

describe('AdminUsersPage', () => {
  it('renders the registered users list for an admin', async () => {
    signInAsAdmin()

    renderApp('/admin/users')

    expect(await screen.findByText(ADMIN_USERS[0].email)).toBeInTheDocument()
    expect(screen.getByText(ADMIN_USERS[1].email)).toBeInTheDocument()
  })

  it('blocks an active user and flips the row to Blocked', async () => {
    signInAsAdmin()
    const { user } = renderApp('/admin/users')

    const activeRow = (await screen.findByText(ADMIN_USERS[0].email)).closest('tr')
    if (!activeRow) throw new Error('Row not found')
    await user.click(within(activeRow).getByRole('button', { name: /^block$/i }))

    await waitFor(() => expect(within(activeRow).getByText('Blocked')).toBeInTheDocument())
    expect(within(activeRow).getByRole('button', { name: /^unblock$/i })).toBeInTheDocument()
  })

  it('unblocks a blocked user and flips the row to Active', async () => {
    signInAsAdmin()
    const { user } = renderApp('/admin/users')

    const blockedRow = (await screen.findByText(ADMIN_USERS[1].email)).closest('tr')
    if (!blockedRow) throw new Error('Row not found')
    await user.click(within(blockedRow).getByRole('button', { name: /^unblock$/i }))

    await waitFor(() => expect(within(blockedRow).getByText('Active')).toBeInTheDocument())
    expect(within(blockedRow).getByRole('button', { name: /^block$/i })).toBeInTheDocument()
  })

  it('a non-admin session sees an access-denied state instead of the list', async () => {
    useAuthStore.getState().signIn({ token: 't', userId: 1, email: 'trader@example.com', role: 'TRADER' })

    renderApp('/admin/users')

    expect(await screen.findByText('Access denied')).toBeInTheDocument()
    expect(screen.queryByText(ADMIN_USERS[0].email)).not.toBeInTheDocument()
  })
})
