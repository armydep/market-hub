import { useState } from 'react'
import { Pagination } from '../components/Pagination'
import { EmptyState, FatalError, LoadingState } from '../components/States'
import { useAdminUsers } from '../hooks/useAdminUsers'
import { useBlockUser } from '../hooks/useBlockUser'
import { useUnblockUser } from '../hooks/useUnblockUser'
import styles from './AdminUsersPage.module.css'

/**
 * Admin-only user list with block/unblock controls (PRD F-010). Plain
 * pagination, no search or sort — the PRD frames Phase-1 admin as
 * intentionally basic (§2.12), unlike the market dashboard's grid.
 */
export function AdminUsersPage() {
  const [page, setPage] = useState(0)
  const users = useAdminUsers(page)
  const blockUser = useBlockUser()
  const unblockUser = useUnblockUser()

  if (users.isLoading) {
    return <LoadingState label="Loading users…" />
  }
  if (users.isError || !users.data) {
    return (
      <FatalError
        message={(users.error as Error)?.message ?? 'Could not load users.'}
        onRetry={() => users.refetch()}
      />
    )
  }

  const { content, totalPages, totalElements } = users.data

  return (
    <section className={styles.page}>
      <header className={styles.header}>
        <h1 className={styles.title}>User Management</h1>
      </header>

      {content.length === 0 ? (
        <EmptyState title="No registered users" />
      ) : (
        <>
          <div className={styles.wrapper}>
            <table className={styles.table}>
              <thead>
                <tr>
                  <th scope="col">Email</th>
                  <th scope="col">Role</th>
                  <th scope="col">Status</th>
                  <th scope="col">Registered</th>
                  <th scope="col">Action</th>
                </tr>
              </thead>
              <tbody>
                {content.map((user) => (
                  <tr key={user.id}>
                    <td>{user.email}</td>
                    <td>{user.role}</td>
                    <td>{user.blocked ? 'Blocked' : 'Active'}</td>
                    <td>{new Date(user.createdAt).toLocaleDateString()}</td>
                    <td>
                      {user.blocked ? (
                        <button
                          type="button"
                          onClick={() => unblockUser.mutate(user.id)}
                          disabled={unblockUser.isPending}
                        >
                          Unblock
                        </button>
                      ) : (
                        <button
                          type="button"
                          onClick={() => blockUser.mutate(user.id)}
                          disabled={blockUser.isPending}
                        >
                          Block
                        </button>
                      )}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <Pagination
            page={page}
            totalPages={totalPages}
            totalElements={totalElements}
            onPageChange={setPage}
            unit="users"
          />
        </>
      )}
    </section>
  )
}
