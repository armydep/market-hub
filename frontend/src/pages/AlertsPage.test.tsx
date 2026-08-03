import { screen, waitFor, within } from '@testing-library/react'
import { describe, expect, it } from 'vitest'
import { useAuthStore } from '../store/authStore'
import { ACTIVE_ALERT, AUTH_RESPONSE, TRIGGERED_ALERT } from '../test/fixtures'
import { renderApp } from '../test/renderApp'

function signIn() {
  useAuthStore.getState().signIn(AUTH_RESPONSE)
}

describe('AlertsPage', () => {
  it('renders the active and triggered alerts', async () => {
    signIn()

    renderApp('/alerts')

    expect(await screen.findByText(ACTIVE_ALERT.symbol)).toBeInTheDocument()
    expect(screen.getByText(TRIGGERED_ALERT.symbol)).toBeInTheDocument()
  })

  it('creates an alert and shows it in the active list', async () => {
    signIn()
    const { user } = renderApp('/alerts')

    await screen.findByText(ACTIVE_ALERT.symbol)

    await user.type(screen.getByLabelText(/symbol/i), 'SOL')
    await user.type(screen.getByLabelText(/target price/i), '200')
    await user.click(screen.getByRole('button', { name: /create alert/i }))

    await waitFor(() => expect(screen.getByText('SOL')).toBeInTheDocument())
  })

  it('rejects creating an alert whose condition is already satisfied', async () => {
    signIn()
    const { user } = renderApp('/alerts')

    await screen.findByText(ACTIVE_ALERT.symbol)

    await user.type(screen.getByLabelText(/symbol/i), 'BTC')
    await user.type(screen.getByLabelText(/target price/i), '100')
    await user.click(screen.getByRole('button', { name: /create alert/i }))

    expect(await screen.findByRole('alert')).toHaveTextContent(/already satisfied/i)
  })

  it('edits an active alert', async () => {
    signIn()
    const { user } = renderApp('/alerts')

    const row = (await screen.findByText(ACTIVE_ALERT.symbol)).closest('li')
    if (!row) throw new Error('Row not found')

    await user.click(within(row).getByRole('button', { name: /edit/i }))
    const priceInput = within(row).getByDisplayValue(String(ACTIVE_ALERT.targetPrice))
    await user.clear(priceInput)
    await user.type(priceInput, '75000')
    await user.click(within(row).getByRole('button', { name: /save/i }))

    await waitFor(() => expect(screen.getByText(/75000/)).toBeInTheDocument())
  })

  it('deletes an active alert', async () => {
    signIn()
    const { user } = renderApp('/alerts')

    const row = (await screen.findByText(ACTIVE_ALERT.symbol)).closest('li')
    if (!row) throw new Error('Row not found')
    await user.click(within(row).getByRole('button', { name: /delete/i }))

    await waitFor(() => expect(screen.queryByText(ACTIVE_ALERT.symbol)).not.toBeInTheDocument())
    expect(await screen.findByText(/no active alerts/i)).toBeInTheDocument()
  })

  it('clears a triggered alert', async () => {
    signIn()
    const { user } = renderApp('/alerts')

    const row = (await screen.findByText(TRIGGERED_ALERT.symbol)).closest('li')
    if (!row) throw new Error('Row not found')
    await user.click(within(row).getByRole('button', { name: /clear/i }))

    await waitFor(() => expect(screen.queryByText(TRIGGERED_ALERT.symbol)).not.toBeInTheDocument())
    expect(await screen.findByText(/no triggered alerts/i)).toBeInTheDocument()
  })
})
