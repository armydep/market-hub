import { create } from 'zustand'
import { persist } from 'zustand/middleware'

interface ColumnsState {
  /** null until the user first changes something — meaning "use the server default". */
  visibleColumns: string[] | null
  toggleColumn: (key: string, serverDefault: string[]) => void
  reset: () => void
}

/**
 * Guest column visibility, held client-side only (F001-FR-008/023) — guests have
 * no server state at all. S8 gives registered users a persisted server-side set;
 * this store stays the guest path.
 *
 * Storing null rather than a copy of the server default matters: a user who has
 * never touched the picker should follow the application default if it changes,
 * not be pinned to whatever it happened to be on their first visit.
 */
export const useColumnsStore = create<ColumnsState>()(
  persist(
    (set, get) => ({
      visibleColumns: null,
      toggleColumn: (key, serverDefault) => {
        const current = get().visibleColumns ?? serverDefault
        const next = current.includes(key)
          ? current.filter((c) => c !== key)
          : [...current, key]
        set({ visibleColumns: next })
      },
      reset: () => set({ visibleColumns: null }),
    }),
    { name: 'market-hub.columns' },
  ),
)

/**
 * Resolve what to actually render: the user's choice if they've made one, else
 * the server default.
 *
 * Display order follows the server's `defaultVisible` sequence first, then any
 * remaining supported columns. That sequence is meaningful — it's the intended
 * dashboard layout (rank, name, symbol, price, …) — so ordering by the
 * `supported` array instead would silently rearrange the grid into the
 * catalog's declaration order. Toggling a column never reshuffles the rest.
 */
export function resolveVisibleColumns(
  chosen: string[] | null,
  serverDefault: string[],
  supported: string[],
): string[] {
  const selected = new Set(chosen ?? serverDefault)
  const displayOrder = [
    ...serverDefault,
    ...supported.filter((key) => !serverDefault.includes(key)),
  ]
  return displayOrder.filter((key) => selected.has(key))
}
