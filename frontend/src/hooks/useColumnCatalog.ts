import { useQuery } from '@tanstack/react-query'
import { fetchColumnCatalog } from '../api/client'

/**
 * The column catalog is server-owned configuration (F001-FR-005/006/015), not
 * market data — it changes only on redeploy, so it's cached for the session
 * rather than polled.
 */
export function useColumnCatalog() {
  return useQuery({
    queryKey: ['columnCatalog'],
    queryFn: ({ signal }) => fetchColumnCatalog(signal),
    staleTime: Infinity,
  })
}
