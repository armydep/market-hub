import { screen, waitFor } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { AUTH_RESPONSE, NOTIFICATION } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function signIn() {
  useAuthStore.getState().signIn(AUTH_RESPONSE)
}

describe('NotificationsPage', () => {
  it('renders a visible notification', async () => {
    signIn()

    renderApp('/notifications')

    expect(await screen.findByText(NOTIFICATION.symbol)).toBeInTheDocument()
    expect(screen.getByText(/triggered at/i)).toBeInTheDocument()
  })

  it('clears a notification and it disappears from the list', async () => {
    signIn()
    const { user } = renderApp('/notifications')

    await screen.findByText(NOTIFICATION.symbol)
    await user.click(screen.getByRole('button', { name: /clear/i }))

    await waitFor(() => expect(screen.queryByText(NOTIFICATION.symbol)).not.toBeInTheDocument())
    expect(await screen.findByText(/no notifications/i)).toBeInTheDocument()
  })

  it('shows an unread-count badge in the header that clears with the last notification', async () => {
    signIn()
    const { user } = renderApp('/notifications')

    await screen.findByText(NOTIFICATION.symbol)
    expect(await screen.findByText('1')).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: /clear/i }))

    await waitFor(() => expect(screen.queryByText('1')).not.toBeInTheDocument())
  })
})
