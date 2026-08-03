import type { AlertCondition } from './api/types'

/**
 * Shared between AlertsPage (creating/editing alerts) and NotificationsPage
 * (displaying what fired) so the two label sets can't drift apart.
 */
export const CONDITIONS: { value: AlertCondition; label: string }[] = [
  { value: 'ABOVE_OR_EQUAL', label: 'At or above' },
  { value: 'BELOW_OR_EQUAL', label: 'At or below' },
]

export function conditionLabel(condition: AlertCondition): string {
  return CONDITIONS.find((c) => c.value === condition)?.label ?? condition
}
