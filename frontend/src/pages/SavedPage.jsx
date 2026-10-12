import { useState } from 'react'
import { Link } from 'react-router-dom'
import { providerApi } from '../api'
import { useFetch } from '../hooks'
import { Button, Card, Empty, Notice, Spinner } from '../components/ui'
import { formatDate } from '../utils'

/**
 * The shortlist.
 *
 * There is no user id in any of these requests - the API takes identity
 * from the token. That is why nobody can read or change someone else's
 * list by editing a number in the URL.
 */
export default function SavedPage() {
  const { data: saved, error, loading, reload } = useFetch(() => providerApi.saved(), [])
  const [removeError, setRemoveError] = useState(null)

  const remove = async (providerId) => {
    setRemoveError(null)
    try {
      await providerApi.unsave(providerId)
      reload()
    } catch (err) {
      setRemoveError(err.message)
    }
  }

  if (loading) return <main className="page"><Spinner label="Loading your list" /></main>

  return (
    <main className="page">
      <div className="page__head">
        <h1>Saved providers</h1>
        <p>Providers you kept to come back to.</p>
      </div>

      {error && <Notice tone="error">{error.message}</Notice>}
      {removeError && <Notice tone="error">{removeError}</Notice>}

      {saved?.length === 0 ? (
        <Empty title="Nothing saved yet">
          <p>
            Save a provider from their page and it will show up here, with
            whatever note you left.
          </p>
          <Link to="/">Search the directory</Link>
        </Empty>
      ) : (
        <div className="stack">
          {saved?.map((s) => (
            <Card key={s.id}>
              <div className="row" style={{ justifyContent: 'space-between' }}>
                <div>
                  <h3 style={{ marginBottom: 'var(--s-1)' }}>
                    <Link to={`/providers/${s.providerId}`}>{s.fullName}</Link>
                  </h3>
                  <div className="muted small">{s.organization}</div>
                  {s.note && <p className="small" style={{ marginTop: 'var(--s-2)' }}>{s.note}</p>}
                  <div className="small muted">Saved {formatDate(s.savedAt)}</div>
                </div>

                <Button variant="quiet" size="small" onClick={() => remove(s.providerId)}>
                  Remove
                </Button>
              </div>
            </Card>
          ))}
        </div>
      )}
    </main>
  )
}
